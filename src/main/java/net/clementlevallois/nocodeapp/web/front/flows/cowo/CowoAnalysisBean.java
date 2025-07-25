package net.clementlevallois.nocodeapp.web.front.flows.cowo;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class CowoAnalysisBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CowoAnalysisBean.class.getName());

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private CowoService cowoService;

    @PostConstruct
    public void init() {
        if (sessionBean.getCowoState() == null) {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("cowo-import.html?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirection error", ex);
            }
        }
    }

    public void setFunctionNameInSession() {
        sessionBean.setFunctionName(WorkflowCowoProps.NAME);
    }

    public void runAnalysis() {
        if (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters parameters) {
            if (parameters.jobId() == null || parameters.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            try {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
                String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
                CowoState.Processing processingState = cowoService.startAnalysis(parameters, sessionId);
                sessionBean.setCowoState(processingState);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error initiating analysis", e);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getCowoState() instanceof CowoState.Processing processingState) {
            sessionBean.setCowoState(cowoService.checkCompletion(processingState));
            if (sessionBean.getCowoState() instanceof CowoState.ResultsReady) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext()
                        .redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/workflow-cowo/results.html");
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Redirect to results.xhtml failed", ex);
                }
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getCowoState() instanceof CowoState.Processing)
            ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
            : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getCowoState() instanceof CowoState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getCowoState()) {
            case CowoState.AwaitingParameters ap -> 0;
            case CowoState.Processing p -> p.progress();
            case CowoState.ResultsReady rr -> 100;
            case CowoState.FlowFailed ff -> 0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<CowoState.AwaitingParameters, CowoState.AwaitingParameters> updater) {
        if (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters params) {
            sessionBean.setCowoState(updater.apply(params));
        }
    }

    // Input parameters setters and getters

    public List<String> getSelectedLanguages() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.selectedLanguages() : new ArrayList<>();
    }

    public void setSelectedLanguages(List<String> languages) {
        updateAwaitingParameters(p -> p.withSelectedLanguages(languages));
    }

    public int getMinTermFreq() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.minTermFreq() : 2;
    }

    public void setMinTermFreq(int freq) {
        updateAwaitingParameters(p -> p.withMinTermFreq(freq));
    }

    public int getMaxNGram() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.maxNGram() : 4;
    }

    public void setMaxNGram(int nGram) {
        updateAwaitingParameters(p -> p.withMaxNGram(nGram));
    }

    public Integer getMinCharNumber() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.minCharNumber() : 4;
    }

    public void setMinCharNumber(Integer minChar) {
        updateAwaitingParameters(p -> p.withMinCharNumber(minChar));
    }

    public boolean isRemoveNonAsciiCharacters() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.removeNonAsciiCharacters();
    }

    public void setRemoveNonAsciiCharacters(boolean flag) {
        updateAwaitingParameters(p -> p.withRemoveNonAsciiCharacters(flag));
    }

    public boolean isScientificCorpus() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.scientificCorpus();
    }

    public void setScientificCorpus(boolean flag) {
        updateAwaitingParameters(p -> p.withScientificCorpus(flag));
    }

    public boolean isFirstNames() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.firstNames();
    }

    public void setFirstNames(boolean flag) {
        updateAwaitingParameters(p -> p.withFirstNames(flag));
    }

    public boolean isLemmatize() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.lemmatize();
    }

    public void setLemmatize(boolean flag) {
        updateAwaitingParameters(p -> p.withLemmatize(flag));
    }

    public boolean isReplaceStopwords() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.replaceStopwords();
    }

    public void setReplaceStopwords(boolean flag) {
        updateAwaitingParameters(p -> p.withReplaceStopwords(flag));
    }

    public boolean isUsePMI() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.usePMI();
    }

    public void setUsePMI(boolean flag) {
        updateAwaitingParameters(p -> p.withUsePMI(flag));
    }

    public UploadedFile getFileUserStopwords() {
        return (sessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.fileUserStopwords() : null;
    }

    public void setFileUserStopwords(UploadedFile file) {
        updateAwaitingParameters(p -> p.withFileUserStopwords(file));
        if (file != null && file.getFileName() != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, file.getFileName() + " " + is_uploaded + ".");
        }
    }

    public List<Locale> getAvailable() {
        List<Locale> available = new ArrayList<>();
        String[] availableStopwordLists = new String[]{"ar", "bg", "ca", "da", "de", "el", "en", "es", "fr", "it", "ja", "nl", "no", "pl", "pt", "ro", "ru", "tr"};
        for (String tag : availableStopwordLists) {
            available.add(Locale.forLanguageTag(tag));
        }
        Locale requestLocale = Optional.ofNullable(FacesContext.getCurrentInstance())
            .map(ctx -> ctx.getExternalContext().getRequestLocale())
            .orElse(Locale.getDefault());
        available.sort(new LocaleComparator(requestLocale));
        return available;
    }
}
