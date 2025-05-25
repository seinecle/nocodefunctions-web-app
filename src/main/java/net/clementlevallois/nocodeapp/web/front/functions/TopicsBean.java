package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
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
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import net.clementlevallois.importers.model.DataFormatConverter;
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
import org.primefaces.PrimeFaces;
import org.primefaces.event.SlideEndEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import java.net.http.HttpResponse;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;

@Named
@SessionScoped
@MultipartConfig
public class TopicsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(TopicsBean.class.getName());

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
    private String dataPersistenceUniqueId = "";
    private volatile boolean taskComplete = false;
    private volatile boolean taskSuccess = false;
    private String sessionId;

    private Map<Integer, String> mapOfLines;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ImportSimpleLinesBean simpleLinesImportBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Resource
    private ManagedExecutorService managedExecutorService;

    public TopicsBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("topics");
        runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
        logBean.setSessionId(sessionId);
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
        taskComplete = false;
        taskSuccess = false;

        try {
            if (simpleLinesImportBean.getDataPersistenceUniqueId() != null) {
                this.dataPersistenceUniqueId = simpleLinesImportBean.getDataPersistenceUniqueId();
            } else {
                generateInputDataAndDataId();
                if (this.dataPersistenceUniqueId == null || this.dataPersistenceUniqueId.isEmpty()) {
                    throw new IllegalStateException("Data persistence ID could not be generated.");
                }
            }

            final JsonObject parameters = buildRequestParameters();

            managedExecutorService.submit(() -> {
                sendRequestToMicroserviceAsync(parameters);
            });

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        } catch (IllegalStateException e) {
            LOG.log(Level.SEVERE, "Error initiating analysis", e);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            runButtonDisabled = false;
            taskComplete = false;
            taskSuccess = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
        }
    }

    private JsonObject buildRequestParameters() {
        JsonObjectBuilder overallObject = Json.createObjectBuilder();
        if (selectedLanguage == null) {
            selectedLanguage = "en";
        }

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
        overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);
        return overallObject.build();
    }

    private void sendRequestToMicroserviceAsync(JsonObject parameters) {
        String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/workflow/topics";
        microserviceClient.api().post("/api/workflow/topics")
                .withJsonPayload(parameters)
                .addQueryParameter("lang", selectedLanguage)
                .addQueryParameter("replaceStopwords", String.valueOf(replaceStopwords))
                .addQueryParameter("isScientificCorpus", String.valueOf(scientificCorpus))
                .addQueryParameter("lemmatize", String.valueOf(lemmatize))
                .addQueryParameter("removeAccents", String.valueOf(removeNonAsciiCharacters))
                .addQueryParameter("precision", String.valueOf(precision))
                .addQueryParameter("minCharNumber", String.valueOf(minCharNumber))
                .addQueryParameter("sessionId", sessionId)
                .addQueryParameter("dataPersistenceId", dataPersistenceUniqueId)
                .addQueryParameter("callbackURL", callbackURL)
                .sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String errorBody = response.body();
                        LOG.log(Level.SEVERE, "Microservice task submission failed for dataId {0}. Status: {1}, Body: {2}", new Object[]{dataPersistenceUniqueId, response.statusCode(), errorBody});
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "topics failed", "Could not send to topics microservice: "+ errorBody);
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during microservice task submission for dataId " + dataPersistenceUniqueId, e);
                    String errorMessage = "Exception communicating with microservice: " + e.getMessage();
                    if (e.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                    }
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "topics failed", errorMessage);
                    return null;
                });
    }

    public void checkTaskStatusForPolling() {
        if (sessionId == null || dataPersistenceUniqueId == null || taskComplete) {
            return;
        }

        ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
        if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
            Iterator<MessageFromApi> it = messagesFromApi.iterator();
            while (it.hasNext()) {
                MessageFromApi msg = it.next();
                if (msg.getDataPersistenceId() != null && msg.getDataPersistenceId().equals(dataPersistenceUniqueId)) {
                    LOG.log(Level.INFO, "Polling detected message for dataId {0}: {1}", new Object[]{dataPersistenceUniqueId, msg.getInfo()});

                    switch (msg.getInfo()) {
                        case WORKFLOW_COMPLETED -> {
                            taskComplete = true;
                            taskSuccess = true;
                            progress = 100;
                            runButtonDisabled = false;
                            it.remove();
                            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "pollingPanel");
                            LOG.log(Level.INFO, "Task {0} complete, attempting to load results.", dataPersistenceUniqueId);
                            try {
                                Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
                                Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);
                                Path jsonResults = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId + "_result.json");

                                if (Files.exists(jsonResults)) {
                                    String jsonAsString = Files.readString(jsonResults);
                                    try (JsonReader jsonReader = Json.createReader(new StringReader(jsonAsString))) {
                                        JsonObject jsonObject = jsonReader.readObject();
                                        processParsedResults(jsonObject);
                                        LOG.info("Processed results from saved JSON file.");
                                    }
                                } else {
                                    LOG.log(Level.SEVERE, "Result file not found for dataId: {0}", dataPersistenceUniqueId);
                                    logBean.addOneNotificationFromString("Error: Result file not found.");
                                    taskSuccess = false;
                                }
                            } catch (IOException e) {
                                LOG.log(Level.SEVERE, "Error reading or processing result file for dataId: " + dataPersistenceUniqueId, e);
                                logBean.addOneNotificationFromString("Error processing results: " + e.getMessage());
                                taskSuccess = false;
                            }
                        }
                        case ERROR -> {
                            LOG.log(Level.WARNING, "Polling detected ERROR message for dataId {0}: {1}", new Object[]{dataPersistenceUniqueId, msg.getMessage()});
                            taskComplete = true;
                            taskSuccess = false;
                            runButtonDisabled = false;
                            progress = 0;
                            logBean.addOneNotificationFromString("Analysis failed: " + msg.getMessage());
                            it.remove();
                            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "pollingPanel");
                            PrimeFaces.current().executeScript("stopPollingWidget();");
                        }
                        case PROGRESS -> {
                            if (msg.getMessage() != null) {
                                this.progress = Integer.valueOf(msg.getMessage());
                                PrimeFaces.current().ajax().update("progressComponentId");
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

    public void navigatToResults(AjaxBehaviorEvent event) {
        if (taskComplete && taskSuccess) {
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/topics/results.xhtml?faces-redirect=true");
        } else if (taskComplete && !taskSuccess) {
            logBean.addOneNotificationFromString("Cannot navigate to results: Task failed.");
        }
    }

    public void generateInputDataAndDataId() {
        dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
        LOG.log(Level.INFO, "Generating input data for dataId: {0}", dataPersistenceUniqueId);
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);
            Files.createDirectories(tempFolderForThisTask);

            if (mapOfLines == null || mapOfLines.isEmpty()) {
                DataFormatConverter dataFormatConverter = new DataFormatConverter();
                mapOfLines = dataFormatConverter.convertToMapOfLines(
                        inputData.getBulkData(),
                        inputData.getDataInSheets(),
                        inputData.getSelectedSheetName(),
                        inputData.getSelectedColumnIndex(),
                        inputData.getHasHeaders()
                );

                if (mapOfLines == null || mapOfLines.isEmpty()) {
                    LOG.warning("No data found to generate input file.");
                    logBean.addOneNotificationFromString("No data found for analysis.");
                    dataPersistenceUniqueId = null;
                    return;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                sb.append(entry.getValue().trim()).append("\n");
            }
            Path fullPathToInputFile = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId);
            Files.writeString(fullPathToInputFile, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

            LOG.log(Level.INFO, "Input data saved to: {0}", fullPathToInputFile);

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error generating input data file", ex);
            logBean.addOneNotificationFromString("Error preparing input data: " + ex.getMessage());
            dataPersistenceUniqueId = null;
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

    private void processParsedResults(JsonObject jsonObject) {
        LOG.info("Processing parsed results JSON.");
        try {
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
                LOG.log(Level.INFO, "Successfully processed {0} topics.", keywordsPerTopic.size());
            } else {
                LOG.warning("JSON result did not contain 'keywordsPerTopic' object.");
                logBean.addOneNotificationFromString("Warning: Results format unexpected.");
            }

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error processing results JSON", e);
            logBean.addOneNotificationFromString("Error processing results: " + e.getMessage());
        }
    }

    public StreamedContent getGexfFile() {
        if (dataPersistenceUniqueId == null || dataPersistenceUniqueId.isEmpty()) {
            LOG.warning("Cannot provide GEXF file, dataPersistenceUniqueId is null or empty.");
            logBean.addOneNotificationFromString("Cannot download GEXF: Analysis ID not set.");
            return new DefaultStreamedContent();
        }
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);

            Path gexfResults = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId + "_result.gexf");

            if (Files.exists(gexfResults)) {
                String gexfAsString = Files.readString(gexfResults);
                StreamedContent exportGexfAsStreamedFile = GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "network_file_with_topics");
                return exportGexfAsStreamedFile;
            } else {
                LOG.log(Level.WARNING, "GEXF result file not found for dataId: {0}", dataPersistenceUniqueId);
                logBean.addOneNotificationFromString("GEXF file not found.");
                return new DefaultStreamedContent();
            }

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading GEXF file", ex);
            logBean.addOneNotificationFromString("Error providing GEXF file for download.");
            return new DefaultStreamedContent();
        }
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
            Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
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

    public boolean isTaskSuccess() {
        return taskSuccess;
    }

    public void setTaskSuccess(boolean taskSuccess) {
        this.taskSuccess = taskSuccess;
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

    private StreamedContent createExcelFileFromJsonSavedData() {
        if (dataPersistenceUniqueId == null || dataPersistenceUniqueId.isEmpty()) {
            LOG.warning("Cannot create Excel file, dataPersistenceUniqueId is null or empty.");
            logBean.addOneNotificationFromString("Cannot download results: Analysis ID not set.");
            return new DefaultStreamedContent();
        }
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);
            Path jsonResults = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId + "_result.json");

            if (!Files.exists(jsonResults)) {
                LOG.log(Level.WARNING, "JSON result file not found for dataId: {0}", dataPersistenceUniqueId);
                logBean.addOneNotificationFromString("Cannot download Excel: Result file not found.");
                return new DefaultStreamedContent();
            }

            String jsonAsStringString = Files.readString(jsonResults);
            byte[] topicsAsArray = jsonAsStringString.getBytes(StandardCharsets.UTF_8);

            // Use the importService builder and the new withByteArrayPayload method
            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/topics")
                    .addQueryParameter("nbTerms", "10")
                    .withByteArrayPayload(topicsAsArray)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = futureBytes.join(); // Blocks until the future completes or throws exception

            LOG.info("Export service call successful (async then join).");
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
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading JSON file before export", ex);
            logBean.addOneNotificationFromString("Error preparing data for export: " + ex.getMessage());
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "Error preparing data for export.");
            return new DefaultStreamedContent();
        }
    }
}
