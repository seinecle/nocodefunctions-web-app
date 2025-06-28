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
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class WorkflowCowoBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(WorkflowCowoBean.class.getName());

    @Inject
    private WorkflowSessionBean workflowSessionBean;

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
        if (workflowSessionBean.getCowoState() == null) {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("cowo-import.xhtml?faces-redirect=true");
            } catch (IOException ex) {
                System.getLogger(WorkflowCowoBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
    }

    public void setFunctionNameInSession() {
        sessionBean.setFunctionName(WorkflowCowoProps.NAME);
    }

    public void runAnalysis() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters parameters) {
            if (parameters.jobId() == null || parameters.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            try {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
                String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
                workflowSessionBean.setCowoState(cowoService.startAnalysis(parameters, sessionId));
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error initiating analysis", e);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            }
        }
    }

    public String pollingListener() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.Processing processingState) {
            String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
            workflowSessionBean.setCowoState(cowoService.checkCompletion(processingState, sessionId));
            if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady) {
                return "/workflow-cowo/results.xhtml?faces-redirect=true";
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public String getRunButtonText() {
        return switch (workflowSessionBean.getCowoState()) {
            case CowoState.Processing p ->
                sessionBean.getLocaleBundle().getString("general.message.wait_long_operation");
            default ->
                sessionBean.getLocaleBundle().getString("general.verbs.compute");
        };
    }

    public boolean isRunButtonDisabled() {
        return workflowSessionBean.getCowoState() instanceof CowoState.Processing;
    }

    public int getProgress() {
        CowoState cowoState = workflowSessionBean.getCowoState();
        return switch (cowoState) {
            case CowoState.AwaitingParameters ap ->
                0;
            case CowoState.Processing p ->
                p.progress();
            case CowoState.ResultsReady rr ->
                100;
            case CowoState.FlowFailed ff ->
                0;
        };
    }

    public String getNodesAsJson() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            return rr.nodesAsJson();
        }
        return "{}";
    }

    public String getEdgesAsJson() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            return rr.edgesAsJson();
        }
        return "{}";
    }

    public int getMinFreqNode() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            return rr.minFreqNode();
        }
        return 0;
    }

    public int getMaxFreqNode() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            return rr.maxFreqNode();
        }
        return 0;
    }

    public void gotoVV() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
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
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
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
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            return GEXFSaver.exportGexfAsStreamedFile(rr.gexf(), "results_cowo");
        }
        return new DefaultStreamedContent();
    }

    // Getters and Setters for parameters, which now delegate to the state object
    private void updateAwaitingParameters(java.util.function.Function<CowoState.AwaitingParameters, CowoState.AwaitingParameters> updater) {
        if (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters params) {
            workflowSessionBean.setCowoState(updater.apply(params));
        }
    }

    public List<String> getSelectedLanguages() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.selectedLanguages() : new ArrayList<>();
    }

    public void setSelectedLanguages(List<String> languages) {
        updateAwaitingParameters(p -> p.withSelectedLanguages(languages));
    }

    public int getMinTermFreq() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.minTermFreq() : 2;
    }

    public void setMinTermFreq(int freq) {
        updateAwaitingParameters(p -> p.withMinTermFreq(freq));
    }

    public int getMaxNGram() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) {
            return p.maxNGram();
        }
        return 4;
    }

    public void setMaxNGram(int nGram) {
        updateAwaitingParameters(p -> p.withMaxNGram(nGram));
    }

    public boolean isRemoveNonAsciiCharacters() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.removeNonAsciiCharacters();
    }

    public void setRemoveNonAsciiCharacters(boolean flag) {
        updateAwaitingParameters(p -> p.withRemoveNonAsciiCharacters(flag));
    }

    public boolean isScientificCorpus() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.scientificCorpus();
    }

    public void setScientificCorpus(boolean flag) {
        updateAwaitingParameters(p -> p.withScientificCorpus(flag));
    }

    public boolean isFirstNames() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.firstNames();
    }

    public void setFirstNames(boolean flag) {
        updateAwaitingParameters(p -> p.withFirstNames(flag));
    }

    public boolean isLemmatize() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.lemmatize();
    }

    public void setLemmatize(boolean flag) {
        updateAwaitingParameters(p -> p.withLemmatize(flag));
    }

    public boolean isReplaceStopwords() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.replaceStopwords();
    }

    public void setReplaceStopwords(boolean flag) {
        updateAwaitingParameters(p -> p.withReplaceStopwords(flag));
    }

    public boolean isUsePMI() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) && p.usePMI();
    }

    public void setUsePMI(boolean flag) {
        updateAwaitingParameters(p -> p.withUsePMI(flag));
    }

    public UploadedFile getFileUserStopwords() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.fileUserStopwords() : null;
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
        return (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) && rr.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            workflowSessionBean.setCowoState(rr.withShareVVPublicly(flag));
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) && rr.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            workflowSessionBean.setCowoState(rr.withShareGephiLitePublicly(flag));
        }
    }

    public Integer getMinCharNumber() {
        return (workflowSessionBean.getCowoState() instanceof CowoState.AwaitingParameters p) ? p.minCharNumber() : 4;
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
