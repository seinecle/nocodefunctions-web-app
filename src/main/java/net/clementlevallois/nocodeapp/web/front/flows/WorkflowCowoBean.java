package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

@Named
@SessionScoped
public class WorkflowCowoBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(WorkflowCowoBean.class.getName());

    private CowoFlowState currentState;

    @Inject
    private BackToFrontMessengerBean logBean;
    @Inject
    private CowoDataInputBean cowoDataInputBean;
    @Inject
    private SessionBean sessionBean;
    @Inject
    private ExportToVosViewer exportToVosViewer;
    @Inject
    private ExportToGephiLite exportToGephiLite;
    @Inject
    private WorkflowCowoService cowoService;

    public WorkflowCowoBean() {
    }

    @PostConstruct
    public void init() {

        String jobId = cowoDataInputBean.getJobId();

        if (jobId == null || jobId.isBlank()) {
            // This can happen if the user navigates directly to the page without importing data.
            // We can redirect them or show a message. For now, we'll just log it.
            LOG.warning("WorkflowCowoBean initialized without a jobId from CowoDataInputBean.");
        }

        // Initialize with default parameters
        currentState = new CowoFlowState.AwaitingParameters(
                jobId,
                new ArrayList<>(), // selectedLanguages
                2, // minTermFreq
                4, // maxNGram
                false, // removeNonAsciiCharacters
                false, // scientificCorpus
                true, // firstNames
                true, // lemmatize
                false, // replaceStopwords
                false, // usePMI
                null, // fileUserStopwords
                4 // minCharNumber
        );
    }

    public void setFunctionNameInSession() {
        sessionBean.setFunctionName(WorkflowCowoProps.NAME);
    }

    public void runAnalysis() {
        if (currentState instanceof CowoFlowState.AwaitingParameters parameters) {
            if (parameters.jobId() == null || parameters.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            try {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
                String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
                this.currentState = cowoService.startAnalysis(parameters, sessionId);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error initiating analysis", e);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            }
        }
    }

    public boolean isShouldPoll() {
        return currentState instanceof CowoFlowState.Processing;
    }

    public void pollingListener() {
        if (currentState instanceof CowoFlowState.Processing processingState) {
            String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
            this.currentState = cowoService.checkCompletion(processingState, sessionId);
            if (this.currentState instanceof CowoFlowState.ResultsReady) {
                FacesContext context = FacesContext.getCurrentInstance();
                context.getApplication().getNavigationHandler().handleNavigation(context, null, "/workflow-cowo/results.xhtml?faces-redirect=true");
            }
        }
    }

    public String getRunButtonText() {
        return switch (currentState) {
            case CowoFlowState.Processing p ->
                sessionBean.getLocaleBundle().getString("general.message.wait_long_operation");
            default ->
                sessionBean.getLocaleBundle().getString("general.verbs.compute");
        };
    }

    public boolean isRunButtonDisabled() {
        return currentState instanceof CowoFlowState.Processing;
    }

    public int getProgress() {
        return switch (currentState) {
            case CowoFlowState.AwaitingParameters ap ->
                0;
            case CowoFlowState.Processing p ->
                p.progress();
            case CowoFlowState.ResultsReady rr ->
                100;
            case CowoFlowState.FlowFailed ff ->
                0;
        };
    }

    public String getNodesAsJson() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            return rr.nodesAsJson();
        }
        return "{}";
    }

    public String getEdgesAsJson() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            return rr.edgesAsJson();
        }
        return "{}";
    }

    public int getMinFreqNode() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            return rr.minFreqNode();
        }
        return 0;
    }

    public int getMaxFreqNode() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            return rr.maxFreqNode();
        }
        return 0;
    }

    public void gotoVV() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            String linkToVosViewer = exportToVosViewer.exportAndReturnLinkFromGexfWithGet(rr.jobId(), rr.shareVVPublicly());
            if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
                try {
                    ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                    externalContext.redirect(linkToVosViewer);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error redirecting to VOSviewer", ex);
                }
            }
        }
    }

    public void gotoGephiLite() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            String urlToGephiLite = exportToGephiLite.exportAndReturnLinkFromId(rr.jobId(), rr.shareGephiLitePublicly());
            if (urlToGephiLite != null && !urlToGephiLite.isBlank()) {
                try {
                    ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                    externalContext.redirect(urlToGephiLite);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error redirecting to Gephi Lite", ex);
                }
            }
        }
    }

    public StreamedContent getFileToSave() {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            return GEXFSaver.exportGexfAsStreamedFile(rr.gexf(), "results_cowo");
        }
        return new DefaultStreamedContent();
    }

    // Getters and Setters for parameters, which now delegate to the state object
    private void updateAwaitingParameters(java.util.function.Function<CowoFlowState.AwaitingParameters, CowoFlowState.AwaitingParameters> updater) {
        if (currentState instanceof CowoFlowState.AwaitingParameters params) {
            this.currentState = updater.apply(params);
        }
    }

    public List<String> getSelectedLanguages() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) ? p.selectedLanguages() : new ArrayList<>();
    }

    public void setSelectedLanguages(List<String> languages) {
        updateAwaitingParameters(p -> p.withSelectedLanguages(languages));
    }

    public int getMinTermFreq() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) ? p.minTermFreq() : 2;
    }

    public void setMinTermFreq(int freq) {
        updateAwaitingParameters(p -> p.withMinTermFreq(freq));
    }

    public int getMaxNGram() {
        if (currentState instanceof CowoFlowState.AwaitingParameters p) {
            return p.maxNGram();
        }
        return 4;
    }

    public void setMaxNGram(int nGram) {
        updateAwaitingParameters(p -> p.withMaxNGram(nGram));
    }

    public boolean isRemoveNonAsciiCharacters() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) && p.removeNonAsciiCharacters();
    }

    public void setRemoveNonAsciiCharacters(boolean flag) {
        updateAwaitingParameters(p -> p.withRemoveNonAsciiCharacters(flag));
    }

    public boolean isScientificCorpus() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) && p.scientificCorpus();
    }

    public void setScientificCorpus(boolean flag) {
        updateAwaitingParameters(p -> p.withScientificCorpus(flag));
    }

    public boolean isFirstNames() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) && p.firstNames();
    }

    public void setFirstNames(boolean flag) {
        updateAwaitingParameters(p -> p.withFirstNames(flag));
    }

    public boolean isLemmatize() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) && p.lemmatize();
    }

    public void setLemmatize(boolean flag) {
        updateAwaitingParameters(p -> p.withLemmatize(flag));
    }

    public boolean isReplaceStopwords() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) && p.replaceStopwords();
    }

    public void setReplaceStopwords(boolean flag) {
        updateAwaitingParameters(p -> p.withReplaceStopwords(flag));
    }

    public boolean isUsePMI() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) && p.usePMI();
    }

    public void setUsePMI(boolean flag) {
        updateAwaitingParameters(p -> p.withUsePMI(flag));
    }

    public UploadedFile getFileUserStopwords() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) ? p.fileUserStopwords() : null;
    }

    public void setFileUserStopwords(UploadedFile file) {
        updateAwaitingParameters(p -> p.withFileUserStopwords(file));
        if (file != null && file.getFileName() != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, file.getFileName() + " " + is_uploaded + ".");
        }
    }

    public Boolean getShareVVPublicly() {
        return (currentState instanceof CowoFlowState.ResultsReady rr) && rr.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            this.currentState = rr.withShareVVPublicly(flag);
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return (currentState instanceof CowoFlowState.ResultsReady rr) && rr.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (currentState instanceof CowoFlowState.ResultsReady rr) {
            this.currentState = rr.withShareGephiLitePublicly(flag);
        }
    }

    public Integer getMinCharNumber() {
        return (currentState instanceof CowoFlowState.AwaitingParameters p) ? p.minCharNumber() : 4;
    }

    public void setMinCharNumber(Integer minChar) {
        updateAwaitingParameters(p -> p.withMinCharNumber(minChar));
    }

    public List<Locale> getAvailable() {
        List<Locale> available = new ArrayList<>();
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
