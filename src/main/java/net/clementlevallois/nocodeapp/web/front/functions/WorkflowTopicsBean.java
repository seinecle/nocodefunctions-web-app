package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Asynchronous;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static java.util.stream.Collectors.toList;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import org.primefaces.event.SlideEndEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import java.net.http.HttpResponse;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.GlobalQueryParams;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.BodyJsonKeys.USER_SUPPLIED_STOPWORDS;
import net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.IS_SCIENTIFIC_CORPUS;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.LANG;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.LEMMATIZE;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.MIN_CHAR_NUMBER;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.MIN_TERM_FREQ;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.PRECISION;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.REMOVE_ACCENTS;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.REPLACE_STOPWORDS;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.PostRequestBuilder;

@Named
@SessionScoped
@MultipartConfig
public class WorkflowTopicsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(WorkflowTopicsBean.class.getName());

    private Integer progress = 0;
    private Map<Integer, Multiset<String>> keywordsPerTopic;
    private Boolean runButtonDisabled = false;
    private String selectedLanguage;
    private int precision = 50;
    private int minCharNumber = 4;
    private int minTermFreq = 2;

    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private boolean lemmatize = true;
    private boolean removeNonAsciiCharacters = false;
    private UploadedFile fileUserStopwords;

    private String runButtonText = "";
    private String jobId = "";

    private Map<Integer, String> mapOfLines;

    private WorkflowTopicsProps props;
    private Globals globals;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    DataImportBean inputData;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Resource
    private ManagedExecutorService managedExecutorService;

    public WorkflowTopicsBean() {
    }

    @PostConstruct
    public void init() {
        runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
        props = new WorkflowTopicsProps(applicationProperties.getTempFolderFullPath());
        globals = new Globals(applicationProperties.getTempFolderFullPath());
        sessionBean.setFunctionName(WorkflowTopicsProps.NAME);
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getReport() {
        return "";
    }

    public void runAnalysis() {
        runButtonText = sessionBean.getLocaleBundle().getString("general.message.wait_long_operation");
        progress = 0;
        runButtonDisabled = true;

        try {
            if (sessionBean.getJobId() != null) {
                this.jobId = sessionBean.getJobId();
            } else {
                LOG.warning("No data found to generate input file.");
                runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
                runButtonDisabled = false;
                return;
            }

            var requestBuilder = microserviceClient.api().post(WorkflowTopicsProps.ENDPOINT);

            addJsonBody(requestBuilder);
            addQueryParams(requestBuilder);

            managedExecutorService.submit(() -> {
                sendRequestToMicroserviceAsync(requestBuilder);
            });

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        } catch (IllegalStateException e) {
            LOG.log(Level.SEVERE, "Error initiating analysis", e);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            runButtonDisabled = false;
        }
    }

    private PostRequestBuilder addJsonBody(PostRequestBuilder requestBuilder) {
        JsonObjectBuilder overallObject = Json.createObjectBuilder();

        JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
        if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
            List<String> userSuppliedStopwords = new ArrayList();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                userSuppliedStopwords = br.lines().collect(toList());
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error reading user supplied stopwords file", ex);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "stopwords", "Could not read stopwords file: " + ex.getMessage());
            }
            int index = 0;
            for (String stopword : userSuppliedStopwords) {
                userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
            }
        }
        overallObject.add(USER_SUPPLIED_STOPWORDS.name(), userSuppliedStopwordsBuilder);
        return requestBuilder.withJsonPayload(overallObject.build());
    }

    private PostRequestBuilder addQueryParams(PostRequestBuilder requestBuilder) {
        if (selectedLanguage == null) {
            selectedLanguage = "en";
        }
        for (QueryParams param : QueryParams.values()) {
            String paramValue = switch (param) {
                case LANG ->
                    selectedLanguage;
                case REPLACE_STOPWORDS ->
                    String.valueOf(replaceStopwords);
                case IS_SCIENTIFIC_CORPUS ->
                    String.valueOf(scientificCorpus);
                case LEMMATIZE ->
                    String.valueOf(lemmatize);
                case REMOVE_ACCENTS ->
                    String.valueOf(removeNonAsciiCharacters);
                case PRECISION ->
                    String.valueOf(precision);
                case MIN_CHAR_NUMBER ->
                    String.valueOf(minCharNumber);
                case MIN_TERM_FREQ ->
                    String.valueOf(minTermFreq);
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowTopicsProps.ENDPOINT;

        for (GlobalQueryParams param : GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }
        return requestBuilder;
    }

    @Asynchronous
    private void sendRequestToMicroserviceAsync(PostRequestBuilder requestBuilder) {
        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String errorBody = response.body();
                        LOG.log(Level.SEVERE, "Microservice task submission failed for dataId {0}. Status: {1}, Body: {2}", new Object[]{jobId, response.statusCode(), errorBody});
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "topics failed", "Could not send to topics microservice: " + errorBody);
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during microservice task submission for dataId " + jobId, e);
                    String errorMessage = "Exception communicating with microservice: " + e.getMessage();
                    if (e.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                    }
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "topics failed", errorMessage);
                    return null;
                });
    }

    public void checkTaskStatusForPolling() {
        Path pathSignalWorkflowComplete = globals.getWorkflowCompleteFilePath(jobId);
        boolean workflowComplete = Files.exists(pathSignalWorkflowComplete);

        if (workflowComplete) {
            runButtonDisabled = false;
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            progress = 100;
            processParsedResults();
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowTopicsProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
        }

        ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(jobId);

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
                        default -> {
                        }
                    }
                }
            }
        }
    }

    public void navigateToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowTopicsProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
    }

    private void processParsedResults() {
        try {
            Path jsonResults = props.getGlobalResultsJsonFilePath(jobId);

            if (!Files.exists(jsonResults)) {
                LOG.log(Level.WARNING, "JSON result file not found for dataId: {0}", jobId);
                logBean.addOneNotificationFromString("Cannot download Excel: Result file not found.");
                return;
            }

            String jsonResultAsString = Files.readString(jsonResults);
            JsonReader jsonReader = Json.createReader(new StringReader(jsonResultAsString));
            JsonObject jsonObject = jsonReader.readObject();

            this.keywordsPerTopic = new TreeMap<>();
            JsonObject keywordsPerTopicAsJson = jsonObject.getJsonObject("keywordsPerTopic");
            if (keywordsPerTopicAsJson != null) {
                for (String keyCommunity : keywordsPerTopicAsJson.keySet()) {
                    JsonObject termsAndFrequenciesForThisCommunity = keywordsPerTopicAsJson.getJsonObject(keyCommunity);
                    Multiset<String> termsAndFreqs = new Multiset<>();
                    if (termsAndFrequenciesForThisCommunity != null) {
                        termsAndFrequenciesForThisCommunity.keySet().forEach(term -> {
                            termsAndFreqs.addSeveral(term, termsAndFrequenciesForThisCommunity.getInt(term));
                        });
                    }
                    try {
                        keywordsPerTopic.put(Integer.valueOf(keyCommunity), termsAndFreqs);
                    } catch (NumberFormatException e) {
                        LOG.log(Level.WARNING, "Skipping non-integer topic key in JSON: " + keyCommunity, e);
                    }
                }
            } else {
                LOG.warning("JSON result did not contain 'keywordsPerTopic' object.");
                logBean.addOneNotificationFromString("Warning: Results format unexpected.");
            }

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error processing results JSON", e);
            logBean.addOneNotificationFromString("Error processing results: " + e.getMessage());
        }
    }

    public StreamedContent getGexfFile() {
        if (jobId == null || jobId.isEmpty()) {
            LOG.warning("Cannot provide GEXF file, jobId is null or empty.");
            logBean.addOneNotificationFromString("Cannot download GEXF: Analysis ID not set.");
            return new DefaultStreamedContent();
        }
        try {
            Path gexfResults = props.getGexfFilePath(jobId);
            if (Files.exists(gexfResults)) {
                String gexfAsString = Files.readString(gexfResults);
                StreamedContent exportGexfAsStreamedFile = GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "network_file_with_topics");
                return exportGexfAsStreamedFile;
            } else {
                LOG.log(Level.WARNING, "GEXF result file not found for dataId: {0}", jobId);
                logBean.addOneNotificationFromString("GEXF file not found.");
                return new DefaultStreamedContent();
            }

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading GEXF file", ex);
            logBean.addOneNotificationFromString("Error providing GEXF file for download.");
            return new DefaultStreamedContent();
        }
    }

    public void uploadStopWordFile() {
        try {
            if (fileUserStopwords != null && fileUserStopwords.getFileName() != null && fileUserStopwords.getInputStream() != null) {
                long fileSize = fileUserStopwords.getSize();
                String contentType = fileUserStopwords.getContentType();
                LOG.log(Level.INFO, "Uploaded stopwords file: {0}, size: {1} bytes, type: {2}", new Object[]{fileUserStopwords.getFileName(), fileSize, contentType});
                String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
                String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
                sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, fileUserStopwords.getFileName() + " " + is_uploaded + ".");
            } else {
                LOG.warning("Uploaded stopwords file is null, has no name, or no input stream.");
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Warning", "No file uploaded or file is empty.");
            }
        } catch (IOException ex) {
            Logger.getLogger(WorkflowTopicsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private StreamedContent createExcelFileFromJsonSavedData() {
        if (jobId == null || jobId.isEmpty()) {
            LOG.warning("Cannot create Excel file, jobId is null or empty.");
            logBean.addOneNotificationFromString("Cannot download results: job ID not set.");
            return new DefaultStreamedContent();
        }
        try {
            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/topics")
                    .addQueryParameter("nbTerms", "10")
                    .addQueryParameter("jobId", jobId)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = futureBytes.join();

            InputStream is = new ByteArrayInputStream(body);
            return DefaultStreamedContent.builder()
                    .name("results_topics.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();

        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            LOG.log(Level.SEVERE, "Error during asynchronous export service call (CompletionException)", cause);
            String errorMessage = "Error exporting data: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            logBean.addOneNotificationFromString(errorMessage);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", errorMessage);
            return new DefaultStreamedContent();
        }
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

    public void dummy() {
    }

    public StreamedContent getExcelFileToSave() {
        return createExcelFileFromJsonSavedData();
    }

    public void setExcelFileToSave(StreamedContent excelFileToSave) {
    }

    public void setGexfFile(StreamedContent gexfFile) {
    }

    public Map<Integer, Multiset<String>> getCommunitiesResult() {
        return keywordsPerTopic;
    }

    public void setCommunitiesResult(Map<Integer, Multiset<String>> communitiesResult) {
        this.keywordsPerTopic = communitiesResult;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public void onSlideEnd(SlideEndEvent event) {
        this.precision = (int) event.getValue();
    }

    public Boolean getScientificCorpus() {
        return scientificCorpus;
    }

    public void setScientificCorpus(Boolean scientificCorpus) {
        this.scientificCorpus = scientificCorpus;
    }

    public UploadedFile getFileUserStopwords() {
        return fileUserStopwords;
    }

    public void setFileUserStopwords(UploadedFile file) {
        this.fileUserStopwords = file;
    }

    public boolean isOkToShareStopwords() {
        return okToShareStopwords;
    }

    public void setOkToShareStopwords(boolean okToShareStopwords) {
        this.okToShareStopwords = okToShareStopwords;
    }

    public boolean isReplaceStopwords() {
        return replaceStopwords;
    }

    public void setReplaceStopwords(boolean replaceStopwords) {
        this.replaceStopwords = replaceStopwords;
    }

    public boolean isRemoveNonAsciiCharacters() {
        return removeNonAsciiCharacters;
    }

    public void setRemoveNonAsciiCharacters(boolean removeNonAsciiCharacters) {
        this.removeNonAsciiCharacters = removeNonAsciiCharacters;
    }

    public boolean isLemmatize() {
        return lemmatize;
    }

    public void setLemmatize(boolean lemmatize) {
        this.lemmatize = lemmatize;
    }

    public int getMinCharNumber() {
        return minCharNumber;
    }

    public void setMinCharNumber(int minCharNumber) {
        this.minCharNumber = minCharNumber;
    }

    public int getMinTermFreq() {
        return minTermFreq;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    public String getRunButtonText() {
        return runButtonText;
    }

    public void setRunButtonText(String runButtonText) {
        this.runButtonText = runButtonText;
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
