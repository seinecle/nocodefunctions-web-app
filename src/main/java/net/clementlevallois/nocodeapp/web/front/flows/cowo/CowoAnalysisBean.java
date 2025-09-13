package net.clementlevallois.nocodeapp.web.front.flows.cowo;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import java.io.Serializable;
import java.util.*;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class CowoAnalysisBean implements Serializable {

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private CowoService cowoService;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() == null) {
            FacesUtils.redirectTo("cowo-import.html");
        }
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters parameters) {
            if (parameters.jobId() == null || parameters.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            try {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
                String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
                FlowState cowoState = cowoService.callCowoMicroService(parameters, sessionId);
                sessionBean.setFlowState(cowoState);
            } catch (Exception e) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            }
        } else {
            throw new IllegalStateException("State is not CowoState.AwaitingParameters " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof CowoState.Processing processingState) {
            sessionBean.setFlowState(cowoService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof CowoState.ResultsReady) {
                FacesUtils.redirectTo("results.html");
            }
        } else {
            throw new IllegalStateException("State is not CowoState.Processing " + sessionBean.getFlowState().getClass().getSimpleName());
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getFlowState() instanceof CowoState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getFlowState() instanceof CowoState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getFlowState()) {
            case CowoState.AwaitingParameters ap ->
                0;
            case CowoState.Processing p ->
                p.progress();
            case CowoState.ResultsReady rr ->
                100;
            default ->
                0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<CowoState.AwaitingParameters, CowoState.AwaitingParameters> updater) {
        if (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters params) {
            sessionBean.setFlowState(updater.apply(params));
        }else{
             throw new IllegalStateException("wrong state in updating params in CowoState " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public List<String> getSelectedLanguages() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) ? p.selectedLanguages() : new ArrayList<>();
    }

    public void setSelectedLanguages(List<String> languages) {
        updateAwaitingParameters(p -> p.withSelectedLanguages(languages));
    }

    public int getMinTermFreq() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) ? p.minTermFreq() : 2;
    }

    public void setMinTermFreq(int freq) {
        updateAwaitingParameters(p -> p.withMinTermFreq(freq));
    }

    public int getMaxNGram() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) ? p.maxNGram() : 4;
    }

    public void setMaxNGram(int nGram) {
        updateAwaitingParameters(p -> p.withMaxNGram(nGram));
    }

    public Integer getMinCharNumber() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) ? p.minCharNumber() : 4;
    }

    public void setMinCharNumber(Integer minChar) {
        updateAwaitingParameters(p -> p.withMinCharNumber(minChar));
    }

    public boolean isRemoveNonAsciiCharacters() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) && p.removeNonAsciiCharacters();
    }

    public void setRemoveNonAsciiCharacters(boolean flag) {
        updateAwaitingParameters(p -> p.withRemoveNonAsciiCharacters(flag));
    }

    public boolean isScientificCorpus() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) && p.scientificCorpus();
    }

    public void setScientificCorpus(boolean flag) {
        updateAwaitingParameters(p -> p.withScientificCorpus(flag));
    }

    public boolean isFirstNames() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) && p.firstNames();
    }

    public void setFirstNames(boolean flag) {
        updateAwaitingParameters(p -> p.withFirstNames(flag));
    }

    public boolean isLemmatize() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) && p.lemmatize();
    }

    public void setLemmatize(boolean flag) {
        updateAwaitingParameters(p -> p.withLemmatize(flag));
    }

    public boolean isReplaceStopwords() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) && p.replaceStopwords();
    }

    public void setReplaceStopwords(boolean flag) {
        updateAwaitingParameters(p -> p.withReplaceStopwords(flag));
    }

    public boolean isUsePMI() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) && p.usePMI();
    }

    public void setUsePMI(boolean flag) {
        updateAwaitingParameters(p -> p.withUsePMI(flag));
    }

    public UploadedFile getFileUserStopwords() {
        return (sessionBean.getFlowState() instanceof CowoState.AwaitingParameters p) ? p.fileUserStopwords() : null;
    }

    public void setFileUserStopwords(UploadedFile file) {
        updateAwaitingParameters(p -> p.withFileUserStopwords(file));
        if (file != null && file.getFileName() != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, file.getFileName() + " " + is_uploaded + ".");
        } else {
            throw new IllegalStateException("uploaded stopwords file not supposed to be null");
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
