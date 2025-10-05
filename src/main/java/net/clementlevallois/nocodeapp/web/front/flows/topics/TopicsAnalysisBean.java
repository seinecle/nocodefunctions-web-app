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
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class TopicsAnalysisBean implements Serializable {

    @Inject
    private SessionBean sessionBean;
    @Inject
    private BackToFrontMessengerBean logBean;
    @Inject
    private TopicsService topicsService;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() == null) {
            FacesUtils.redirectTo("topics-data-import.html");
        }
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters params) {
            if (params.jobId() == null || params.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            FlowState processingState = topicsService.callTopicsMicroService(params);
            if (processingState != null) {
                sessionBean.setFlowState(processingState);
            } else {
                sessionBean.setFlowState(new FlowFailed(params.jobId(), params, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof TopicsState.Processing processingState) {
            sessionBean.setFlowState(topicsService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof TopicsState.ResultsReady) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/workflow-topics/results.html");
                } catch (IOException ex) {
                    throw new NocodeApplicationException("An IO error occurred", ex);
                }
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getFlowState() instanceof TopicsState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getFlowState() instanceof TopicsState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getFlowState()) {
            case TopicsState.AwaitingParameters ap ->
                0;
            case TopicsState.Processing p ->
                p.progress();
            case TopicsState.ResultsReady rr ->
                100;
            default ->
                0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<TopicsState.AwaitingParameters, TopicsState.AwaitingParameters> updater) {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters params) {
            sessionBean.setFlowState(updater.apply(params));
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
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.selectedLanguage();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setSelectedLanguage(String language) {
        updateAwaitingParameters(p -> p.withSelectedLanguage(language));
    }

    public boolean isReplaceStopwords() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.replaceStopwords();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setReplaceStopwords(boolean replace) {
        updateAwaitingParameters(p -> p.withReplaceStopwords(replace));
    }

    public boolean isScientificCorpus() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.scientificCorpus();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setScientificCorpus(boolean scientific) {
        updateAwaitingParameters(p -> p.withScientificCorpus(scientific));
    }

    public boolean isLemmatize() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.lemmatize();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setLemmatize(boolean lemmatize) {
        updateAwaitingParameters(p -> p.withLemmatize(lemmatize));
    }

    public boolean isRemoveNonAsciiCharacters() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.removeNonAsciiCharacters();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setRemoveNonAsciiCharacters(boolean remove) {
        updateAwaitingParameters(p -> p.withRemoveNonAsciiCharacters(remove));
    }

    public int getPrecision() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.precision();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setPrecision(int precision) {
        updateAwaitingParameters(p -> p.withPrecision(precision));
    }

    public int getMinCharNumber() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.minCharNumber();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setMinCharNumber(int minChar) {
        updateAwaitingParameters(p -> p.withMinCharNumber(minChar));
    }

    public int getMinTermFreq() {
        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            return p.minTermFreq();
        }
        else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
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
