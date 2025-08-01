package net.clementlevallois.nocodeapp.web.front.flows.topics;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class TopicsAnalysisBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(TopicsAnalysisBean.class.getName());

    @Inject
    private SessionBean sessionBean;
    @Inject
    private BackToFrontMessengerBean logBean;
    @Inject
    private TopicsService topicsService;

    @PostConstruct
    public void init() {
        if (sessionBean.getTopicsState() == null) {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("topics-data-import.xhtml?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirect failed in topics analysis bean init", ex);
            }
        }
    }

    public void runAnalysis() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters params) {
            if (params.jobId() == null || params.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            TopicsState processingState = topicsService.startAnalysis(params);
            if (processingState != null) {
                sessionBean.setTopicsState(processingState);
            } else {
                sessionBean.setTopicsState(new TopicsState.FlowFailed(params.jobId(), params, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getTopicsState() instanceof TopicsState.Processing processingState) {
            sessionBean.setTopicsState(topicsService.checkCompletion(processingState));
            if (sessionBean.getTopicsState() instanceof TopicsState.ResultsReady) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/workflow-topics/results.html");
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Redirect to results.xhtml failed", ex);
                }
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getTopicsState() instanceof TopicsState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getTopicsState() instanceof TopicsState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getTopicsState()) {
            case TopicsState.AwaitingParameters ap ->
                0;
            case TopicsState.Processing p ->
                p.progress();
            case TopicsState.ResultsReady rr ->
                100;
            case TopicsState.FlowFailed ff ->
                0;
            default ->
                0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<TopicsState.AwaitingParameters, TopicsState.AwaitingParameters> updater) {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters params) {
            sessionBean.setTopicsState(updater.apply(params));
        }
    }

    // Getters and setters for the analysis parameters
    public void setFileUserStopwords(UploadedFile file) {
        updateAwaitingParameters(p -> p.withFileUserStopwords(file));
        if (file != null && file.getFileName() != null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", file.getFileName() + " has been uploaded.");
        }
    }

    // You will need to add getters and setters for all the other parameters, similar to this example:
    public String getSelectedLanguage() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.selectedLanguage();
        }
        return "en"; // Default value
    }

    public void setSelectedLanguage(String language) {
        updateAwaitingParameters(p -> p.withSelectedLanguage(language));
    }

    public boolean isReplaceStopwords() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.replaceStopwords();
        }
        return false;
    }

    public void setReplaceStopwords(boolean replace) {
        updateAwaitingParameters(p -> p.withReplaceStopwords(replace));
    }

    public boolean isScientificCorpus() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.scientificCorpus();
        }
        return false;
    }

    public void setScientificCorpus(boolean scientific) {
        updateAwaitingParameters(p -> p.withScientificCorpus(scientific));
    }

    public boolean isLemmatize() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.lemmatize();
        }
        return false;
    }

    public void setLemmatize(boolean lemmatize) {
        updateAwaitingParameters(p -> p.withLemmatize(lemmatize));
    }

    public boolean isRemoveNonAsciiCharacters() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.removeNonAsciiCharacters();
        }
        return false;
    }

    public void setRemoveNonAsciiCharacters(boolean remove) {
        updateAwaitingParameters(p -> p.withRemoveNonAsciiCharacters(remove));
    }

    public int getPrecision() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.precision();
        }
        return 50; // Default value
    }

    public void setPrecision(int precision) {
        updateAwaitingParameters(p -> p.withPrecision(precision));
    }

    public int getMinCharNumber() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.minCharNumber();
        }
        return 3; // Default value
    }

    public void setMinCharNumber(int minChar) {
        updateAwaitingParameters(p -> p.withMinCharNumber(minChar));
    }

    public int getMinTermFreq() {
        if (sessionBean.getTopicsState() instanceof TopicsState.AwaitingParameters p) {
            return p.minTermFreq();
        }
        return 3; // Default value
    }

    public void setMinTermFreq(int minFreq) {
        updateAwaitingParameters(p -> p.withMinTermFreq(minFreq));
    }

    public List<Locale> getAvailable() {
        List<Locale> available = new ArrayList();
        String[] availableStopwordLists = new String[]{"ar", "bg", "ca", "da", "de", "el", "en", "es", "fr", "it", "ja", "nl", "no", "pl", "pt", "ro", "ru", "tr"};
        for (String tag : availableStopwordLists) {
            available.add(Locale.forLanguageTag(tag));
        }
        FacesContext context = FacesContext.getCurrentInstance();
        Locale requestLocale = (context != null) ? context.getExternalContext().getRequestLocale() : Locale.getDefault();
        Collections.sort(available, new LocaleComparator(requestLocale));
        return available;
    }

}
