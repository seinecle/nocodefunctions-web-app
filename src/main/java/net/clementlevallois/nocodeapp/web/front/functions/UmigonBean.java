package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Asynchronous;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.clementlevallois.functions.model.FunctionUmigon;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.ERROR;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.FAILED;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.GOTORESULTS;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.PROGRESS;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.RESULT_ARRIVED;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.WORKFLOW_COMPLETED;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.umigon.model.classification.Document;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

@Named
@SessionScoped
public class UmigonBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(UmigonBean.class.getName());

    private Integer progress = 3;
    private Integer countTreated = 0;
    private List<Document> results;
    private ConcurrentHashMap<Integer, Document> tempResults;
    private String[] sentiments;
    private String selectedLanguage;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private List<Document> filteredDocuments;
    private Integer maxCapacity = 10_000;

    private String jobId;
    private Globals globals;
    private FunctionUmigon props;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    public UmigonBean() {
    }

    @PostConstruct
    public void init() {
        String positive_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_positive");
        String negative_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_negative");
        String neutral_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_neutral");
        sentiments = new String[]{positive_tone, negative_tone, neutral_tone};
        globals = new Globals(applicationProperties.getTempFolderFullPath());
        props = new FunctionUmigon(applicationProperties.getTempFolderFullPath());

    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public void onComplete() {
    }

    public void cancel() {
        progress = null;
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Analysis Cancelled", "Attempting to cancel analysis.");
    }

    public String runAnalysis() {
        progress = 3;
        countTreated = 0;
        runButtonDisabled = true;

        if (selectedLanguage == null || selectedLanguage.isEmpty()) {
            selectedLanguage = "en";
        }
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");
        startAnalysisAsync();

        return null;
    }

    @Asynchronous
    public void startAnalysisAsync() {
        tempResults = new ConcurrentHashMap<>();
        filteredDocuments = new ArrayList<>();
        results = new ArrayList<>();

        String owner = applicationProperties.getPrivateProperties().getProperty("pwdOwner");
        if (owner == null) {
            LOG.severe("pwdOwner property is not set!");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Configuration Error", "Owner password not configured.");
            return;
        }

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + FunctionUmigon.ENDPOINT;

        var requestBuilder = microserviceClient.api().post(FunctionUmigon.ENDPOINT);

        for (FunctionUmigon.QueryParams param : FunctionUmigon.QueryParams.values()) {
            String value = switch (param) {
                case TEXT_LANG ->
                    selectedLanguage;
                case EXPLANATION ->
                    "on";
                case SHORTER ->
                    "true";
                case OWNER ->
                    owner;
                case OUTPUT_FORMAT ->
                    "bytes";
                case EXPLANATION_LANG ->
                    sessionBean.getCurrentLocale().toLanguageTag();
            };
            requestBuilder.addQueryParameter(param.name(), value);
        }

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String value = switch (param) {
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), value);
        }

        requestBuilder
                .sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Umigon microservice call failed");
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Microservice error");
                    }
                })
                .exceptionally(exception -> {
                    LOG.log(Level.SEVERE, "Exception during async Umigon call");
                    String errorMessage = "Communication error: " + exception.getMessage();
                    if (exception.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage = "Communication error: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
                    }
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", errorMessage);
                    countTreated++;
                    return null;
                });
    }

    public void checkTaskStatusForPolling() {
        if (jobId == null) {
            return;
        }

        Path pathSignalWorkflowComplete = globals.getWorkflowCompleteFilePath(jobId);
        boolean workflowComplete = Files.exists(pathSignalWorkflowComplete);

        if (workflowComplete) {
            processResults();
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowTopicsProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
        }

        ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get("");

        if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
            Iterator<MessageFromApi> it = messagesFromApi.iterator();
            while (it.hasNext()) {
                MessageFromApi msg = it.next();
                if (msg.getjobId() != null && msg.getjobId().equals(jobId)) {
                    switch (msg.getInfo()) {
                        case ERROR -> {
                            LOG.log(Level.WARNING, "Polling detected ERROR message for dataId {0}: {1}", new Object[]{jobId, msg.getMessage()});
                            runButtonDisabled = false;
                            progress = 0;
                            logBean.addOneNotificationFromString("Analysis failed: " + msg.getMessage());
                            it.remove();
                        }
                        case PROGRESS -> {
                            if (msg.getProgress() != null) {
                                this.progress = msg.getProgress();
                            }
                            it.remove();
                        }
                        case INTERMEDIARY, RESULT_ARRIVED, WORKFLOW_COMPLETED, FAILED, GOTORESULTS -> {
                        }
                    }
                }
            }
        }
    }

    private void processResults() {
        // open the binary results file, extract the list of Docs and populate tempResult and results
        for (Map.Entry<Integer, Document> entry : tempResults.entrySet()) {
            results.add(entry.getKey(), entry.getValue());
        }

    }

    private void handleAnalysisFailure() {
        progress = 0;
        runButtonDisabled = false;
        PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");
    }

    public List<Document> getResults() {
        return results;
    }

    public void setResults(List<Document> results) {
        this.results = results;
    }

    public String[] getSentiments() {
        return sentiments;
    }

    public void setSentiments(String[] sentiments) {
        this.sentiments = sentiments;
    }

    public String getSelectedLanguage() {
        return selectedLanguage;
    }

    public void setSelectedLanguage(String selectedLanguage) {
        this.selectedLanguage = selectedLanguage;
    }

    public Boolean getRunButtonDisabled() {
        return runButtonDisabled;
    }

    public void setRunButtonDisabled(Boolean runButtonDisabled) {
        this.runButtonDisabled = runButtonDisabled;
    }

    public String signal(int rowId) {
        Document docFound = null;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
        } else if (results != null && rowId < results.size()) {
            docFound = results.get(rowId);
        }

        if (docFound == null) {
            LOG.log(Level.WARNING, "Signalled document not found in backing bean collections for rowId: {0}", rowId);
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Could not find document to signal.");
            return "";
        }

        docFound.setFlaggedAsFalseLabel(true);
        LOG.log(Level.INFO, "Document ID {0} signalled as potentially misclassified.", docFound.getId());
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Feedback Sent", "Thank you for your feedback!");

        return "";
    }

    public String showExplanation(int rowId) {
        Document docFound = null;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
        } else if (results != null && rowId < results.size()) {
            docFound = results.get(rowId);
        }

        if (docFound == null) {
            LOG.log(Level.WARNING, "Document to explain not found in backing bean collections for rowId: {0}", rowId);
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Could not find document to show explanation.");
            return "";
        }
        docFound.setShowExplanation(true);
        return "";
    }

    public String hideExplanation(int rowId) {
        Document docFound = null;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
        } else if (results != null && rowId < results.size()) {
            docFound = results.get(rowId);
        }

        if (docFound == null) {
            LOG.log(Level.WARNING, "Document explanation to hide not found in backing bean collections for rowId: {0}", rowId);
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Could not find document to hide explanation.");
            return "";
        }
        docFound.setShowExplanation(false);
        return "";
    }

    public void dummy() {
    }

    public StreamedContent getFileToSave() {
        if (results == null || results.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "No results available to export.");
            return new DefaultStreamedContent();
        }
        try {
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);

            String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag();

            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/umigon")
                    .addQueryParameter("lang", lang)
                    .withByteArrayPayload(documentsAsByteArray)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                return DefaultStreamedContent.builder()
                        .name("results_umigon.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Error creating StreamedContent from export response body", e);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Download Error", "Could not prepare download file.");
                return new DefaultStreamedContent();
            }

        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            LOG.log(Level.SEVERE, "Error during asynchronous export service call (CompletionException)", cause);
            String errorMessage = "Error exporting data: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", errorMessage);
            return new DefaultStreamedContent();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error serializing results before export", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "Error preparing data for export: " + ex.getMessage());
            return new DefaultStreamedContent();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unexpected error in getFileToSave", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "An unexpected error occurred: " + ex.getMessage());
            return new DefaultStreamedContent();
        }
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public List<Document> getFilteredDocuments() {
        return filteredDocuments;
    }

    public void setFilteredDocuments(List<Document> filteredDocuments) {
        this.filteredDocuments = filteredDocuments;
    }
}
