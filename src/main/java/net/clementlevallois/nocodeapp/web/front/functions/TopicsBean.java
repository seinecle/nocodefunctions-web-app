package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import jakarta.json.JsonWriter;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import org.primefaces.PrimeFaces;
import org.primefaces.event.SlideEndEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig

public class TopicsBean implements Serializable {

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

    private Properties privateProperties;

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

    @Resource
    private ManagedExecutorService managedExecutorService;

    public TopicsBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("topics");
        privateProperties = applicationProperties.getPrivateProperties();
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
        progress = 0; // Progress needs rethinking - best if pushed from Javalin via callbacks
        runButtonDisabled = true;
        taskComplete = false;
        taskSuccess = false;

        try {
            if (simpleLinesImportBean.getDataPersistenceUniqueId() != null) {
                this.dataPersistenceUniqueId = simpleLinesImportBean.getDataPersistenceUniqueId();
            } else {
                generateInputDataAndDataId(); // Needs implementation
            }
            inputData.setDataInSheets(new ArrayList());

            final JsonObject parameters = buildRequestParameters();

            managedExecutorService.submit(() -> {
                boolean submitted = sendRequestToMicroserviceAsync(parameters);
                if (!submitted) {
                    logBean.addOneNotificationFromString("Failed to submit task to microservice.");
                }
            });

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        } catch (Exception e) {
            Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, "Error initiating analysis", e);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            runButtonDisabled = false;
            taskComplete = false;
            taskSuccess = false;

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
                Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
            }
            int index = 0;
            for (String stopword : userSuppliedStopwords) {
                userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
            }
        }
        String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/workflow/topics";
        overallObject.add("lang", selectedLanguage);
        overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);
        overallObject.add("replaceStopwords", replaceStopwords);
        overallObject.add("isScientificCorpus", scientificCorpus);
        overallObject.add("lemmatize", lemmatize);
        overallObject.add("removeAccents", removeNonAsciiCharacters);
        overallObject.add("precision", precision);
        overallObject.add("minCharNumber", minCharNumber);
        overallObject.add("minTermFreq", minTermFreq);
        overallObject.add("sessionId", sessionId);
        overallObject.add("dataPersistenceId", dataPersistenceUniqueId);
        overallObject.add("callbackURL", callbackURL);
        return overallObject.build();
    }

    private boolean sendRequestToMicroserviceAsync(JsonObject parameters) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        StringWriter sw = new StringWriter(128);
        try (JsonWriter jw = Json.createWriter(sw)) {
            jw.write(parameters);
        }
        String jsonString = sw.toString();

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonString, StandardCharsets.UTF_8);

        URI uri = UrlBuilder.empty()
                .withScheme("http").withHost("localhost")
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                .withPath("api/workflow/topics")
                .toUri();

        HttpRequest request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .header("Content-Type", "application/json")
                .build();

        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                logBean.addOneNotificationFromString("Task submitted successfully.");
                return true;
            } else {
                logBean.addOneNotificationFromString("Microservice rejected the task. Status: " + resp.statusCode());
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Microservice task submission failed.");
                runButtonDisabled = false;
                taskComplete = false;
                taskSuccess = false;
                return false;
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, "Error sending request to microservice", ex);
            logBean.addOneNotificationFromString("Communication error with microservice: " + ex.getMessage());
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Communication error.");
            runButtonDisabled = false;
            taskComplete = false;
            taskSuccess = false;
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void checkTaskStatusForPolling() {
        if (sessionId == null) {
            return;
        }
        ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
        if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
            Iterator<MessageFromApi> it = messagesFromApi.iterator();
            while (it.hasNext()) {
                MessageFromApi msg = it.next();
                if (msg.getInfo().equals(MessageFromApi.Information.WORKFLOW_COMPLETED) && msg.getDataPersistenceId().equals(dataPersistenceUniqueId)) {
                    taskComplete = true;
                    taskSuccess = true;
                    it.remove();
                }
            }
        }
        if (taskComplete && taskSuccess && FacesContext.getCurrentInstance() != null) {
            try {
                runButtonDisabled = false;
                // Update components showing button state, status messages etc.
                PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");

                // create the data structure to show results on the results page
                Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
                Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);

                Path jsonResults = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId + "_result.json");
                String jsonAsString = Files.readString(jsonResults);
                try (JsonReader jsonReader = Json.createReader(new StringReader(jsonAsString))) {
                    // Read the JsonObject from the JsonReader.
                    JsonObject jsonObject = jsonReader.readObject();

                    processParsedResults(jsonObject);

                    // Trigger client-side navigation or update results area
                    String pollWidgetVar = "pollingWidget";
                    String targetUrl = "results.html";
                    String scriptToExecute = String.format("stopPollingAndNavigate('%s', '%s');",
                            pollWidgetVar,
                            targetUrl);
                    if (PrimeFaces.current() != null) {
                        PrimeFaces.current().executeScript(scriptToExecute);
                        System.out.println("Executed script: " + scriptToExecute); // For debugging
                    } else {
                        System.err.println("Could not execute PrimeFaces script - no current context?");
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    public void navigatToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/topics/results.xhtml?faces-redirect=true");
    }

    public void generateInputDataAndDataId() {
        dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);
            Files.createDirectories(tempFolderForThisTask);
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                sb.append(entry.getValue().trim()).append("\n");
            }
            Path fullPathToInputFile = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId);
            Files.writeString(fullPathToInputFile, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
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
        try {
            this.keywordsPerTopic = new TreeMap<>();
            JsonObject keywordsPerTopicAsJson = jsonObject.getJsonObject("keywordsPerTopic");
            for (String keyCommunity : keywordsPerTopicAsJson.keySet()) {
                JsonObject termsAndFrequenciesForThisCommunity = keywordsPerTopicAsJson.getJsonObject(keyCommunity);
                Multiset<String> termsAndFreqs = new Multiset<>();
                for (String term : termsAndFrequenciesForThisCommunity.keySet()) {
                    termsAndFreqs.addSeveral(term, termsAndFrequenciesForThisCommunity.getInt(term));
                }
                keywordsPerTopic.put(Integer.valueOf(keyCommunity), termsAndFreqs);
            }
        } catch (NumberFormatException e) {
            System.out.println("error with the json decoding");
        }
    }

    public StreamedContent getGexfFile() {
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);

            Path gexfResults = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId + "_result.gexf");
            String gexfAsString = Files.readString(gexfResults);

            StreamedContent exportGexfAsStreamedFile = GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "network_file_with_topics");
            return exportGexfAsStreamedFile;
        } catch (IOException ex) {
            Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
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
        if (fileUserStopwords != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, fileUserStopwords.getFileName() + " " + is_uploaded + ".");
        }
    }

    public boolean isOkToShareStopwords() {
        return okToShareStopwords;
    }

    public void setOkToShareStopwords(boolean okToShareStopwords) {
        System.out.println("ok to share stopwords");
        this.okToShareStopwords = okToShareStopwords;
    }

    public boolean isReplaceStopwords() {
        return replaceStopwords;
    }

    public void setReplaceStopwords(boolean replaceStopwords) {
        System.out.println("ok to replace stopwords");
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
        Locale requestLocale = FacesContext.getCurrentInstance().getExternalContext().getRequestLocale();
        Collections.sort(available, new LocaleComparator(requestLocale));
        return available;
    }

    private StreamedContent createExcelFileFromJsonSavedData() {
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);

            Path jsonResults = Path.of(tempFolderForThisTask.toString(), dataPersistenceUniqueId + "_result.json");
            String jsonAsStringString = Files.readString(jsonResults);

            byte[] topicsAsArray = Converters.byteArraySerializerForAnyObject(jsonAsStringString);

            HttpRequest.BodyPublisher bodyPublisherTopics = HttpRequest.BodyPublishers.ofByteArray(topicsAsArray);

            URI uriExportTopicsToExcel = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/export/xlsx/topics")
                    .addParameter("nbTerms", "10")
                    .toUri();

            HttpRequest requestExportTopicsToExcel = HttpRequest.newBuilder()
                    .POST(bodyPublisherTopics)
                    .uri(uriExportTopicsToExcel)
                    .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(5)).build();

            HttpResponse<byte[]> resp = client.send(requestExportTopicsToExcel, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            InputStream is = new ByteArrayInputStream(body);
            DefaultStreamedContent excelFileAsStream = DefaultStreamedContent.builder()
                    .name("results_topics.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();

            WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topics", System.currentTimeMillis());
            progress = 100;
            return excelFileAsStream;
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;

    }
}
