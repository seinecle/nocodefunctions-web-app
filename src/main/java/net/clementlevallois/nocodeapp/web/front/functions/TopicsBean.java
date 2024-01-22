package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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
import jakarta.json.stream.JsonParsingException;
import jakarta.servlet.annotation.MultipartConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.LogBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
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
    private String jsonResultAsString;
    private Map<Integer, Multiset<String>> keywordsPerTopic;
    private Map<Integer, Multiset<Integer>> topicsPerLine;
    private Boolean runButtonDisabled = false;
    private StreamedContent excelFileToSave;
    private StreamedContent fileTopicsPerLineToSave;
    private StreamedContent gexfFile;
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
    private String sessionId;
    private boolean topicsHaveArrived = false;

    private Properties privateProperties;

    private Map<Integer, String> mapOfLines;

    @Inject
    LogBean logBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ImportSimpleLinesBean simpleLinesImportBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public TopicsBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("topics");
        privateProperties = applicationProperties.getPrivateProperties();
        runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
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
        topicsHaveArrived = false;
        sendCallToTopicsFunction();
        formatTopicsInVariousWays();
    }

    public void pollingDidEndResultsArrive() {
        String key = dataPersistenceUniqueId + "topics";
        boolean topicsHaveArrived = WatchTower.getQueueOutcomesProcesses().containsKey(dataPersistenceUniqueId + "topics");
        if (topicsHaveArrived) {
            WatchTower.getQueueOutcomesProcesses().remove(key);
            runButtonDisabled = false;
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/topics/results.xhtml?faces-redirect=true");
        }
    }

    public void navigatToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/topics/results.xhtml?faces-redirect=true");
    }

    public void sendCallToTopicsFunction() {
        progress = 0;
        HttpRequest request;
        HttpClient client;
        try {
            sessionBean.sendFunctionPageReport();
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

            if (simpleLinesImportBean.getDataPersistenceUniqueId() != null) {
                dataPersistenceUniqueId = simpleLinesImportBean.getDataPersistenceUniqueId();
            } else {
                dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
                Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
                Path fullPathForFileContainingTextInput = Path.of(tempFolderRelativePath.toString(), dataPersistenceUniqueId);
                DataFormatConverter dataFormatConverter = new DataFormatConverter();
                mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                    if (entry.getValue() == null){
                        continue;
                    }
                    sb.append(entry.getValue().trim()).append("\n");
                }
                Files.writeString(fullPathForFileContainingTextInput, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
            inputData.setDataInSheets(new ArrayList());

            if (selectedLanguage == null) {
                selectedLanguage = "en";
            }

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.finding_key_terms"));
            progress = 20;

            client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
            if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
                List<String> userSuppliedStopwords;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                    userSuppliedStopwords = br.lines().collect(toList());
                }
                int index = 0;
                for (String stopword : userSuppliedStopwords) {
                    userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                }
            }

            String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/topics";
            
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

            JsonObject build = overallObject.build();
            StringWriter sw = new StringWriter(128);
            try (JsonWriter jw = Json.createWriter(sw)) {
                jw.write(build);
            }
            String jsonString = sw.toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withHost("localhost")
                    .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))))
                    .withPath("api/topics")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            if (resp.statusCode() == 200) {
                progress = 80;
            } else {
                System.out.println("topics returned by the API was not a 200 code");
                String errorMessage = body;
                logBean.addOneNotificationFromString(errorMessage);
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                runButtonDisabled = false;
            }

        } catch (IOException | NumberFormatException | InterruptedException ex) {
            System.out.println("ex:" + ex.getMessage());
        }
    }

    private void formatTopicsInVariousWays() {
        // waiting for the message saying the results have been persisted to disk
        Executors.newSingleThreadExecutor().execute(() -> {

            try {

                topicsHaveArrived = false;

                while (!topicsHaveArrived && WatchTower.getCurrentSessions().containsKey(sessionId)) {
                    ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
                    if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
                        Iterator<MessageFromApi> it = messagesFromApi.iterator();
                        while (it.hasNext()) {
                            MessageFromApi msg = it.next();
                            if (msg.getInfo().equals(MessageFromApi.Information.RESULT_ARRIVED) && msg.getDataPersistenceId().equals(dataPersistenceUniqueId)) {
                                topicsHaveArrived = true;
                                it.remove();
                            }
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                topicsHaveArrived = false;

                if (!WatchTower.getCurrentSessions().containsKey(sessionId)) {
                    return;
                }

                progress = 60;

                keywordsPerTopic = new TreeMap();
                topicsPerLine = new TreeMap();
                Path tempDataPath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId + "_result");
                jsonResultAsString = Files.readString(tempDataPath, StandardCharsets.UTF_8);

                JsonReader jsonReader = Json.createReader(new StringReader(jsonResultAsString));
                JsonObject jsonObject;
                try {
                    jsonObject = jsonReader.readObject();
                } catch (JsonParsingException jsonEx) {
                    System.out.println("error: the json we received is not formatted as json");
                    runButtonDisabled = true;
                    return;
                }

                String gexfSemanticNetwork = jsonObject.getString("gexf");
                gexfFile = GEXFSaver.exportGexfAsStreamedFile(gexfSemanticNetwork, "semantic_network");

                JsonObject keywordsPerTopicAsJson = jsonObject.getJsonObject("keywordsPerTopic");
                for (String keyCommunity : keywordsPerTopicAsJson.keySet()) {
                    JsonObject termsAndFrequenciesForThisCommunity = keywordsPerTopicAsJson.getJsonObject(keyCommunity);
                    Iterator<String> iteratorTerms = termsAndFrequenciesForThisCommunity.keySet().iterator();
                    Multiset<String> termsAndFreqs = new Multiset();
                    while (iteratorTerms.hasNext()) {
                        String nextTerm = iteratorTerms.next();
                        termsAndFreqs.addSeveral(nextTerm, termsAndFrequenciesForThisCommunity.getInt(nextTerm));
                    }
                    keywordsPerTopic.put(Integer.valueOf(keyCommunity), termsAndFreqs);
                }
                JsonObject topicsPerLineAsJson = jsonObject.getJsonObject("topicsPerLine");
                for (String lineNumber : topicsPerLineAsJson.keySet()) {
                    JsonObject topicsAndTheirCountsForOneLine = topicsPerLineAsJson.getJsonObject(lineNumber);
                    Iterator<String> iteratorTopics = topicsAndTheirCountsForOneLine.keySet().iterator();
                    Multiset<Integer> topicsAndFreqs = new Multiset();
                    while (iteratorTopics.hasNext()) {
                        String nextTopic = iteratorTopics.next();
                        topicsAndFreqs.addSeveral(Integer.valueOf(nextTopic), topicsAndTheirCountsForOneLine.getInt(nextTopic));
                    }
                    topicsPerLine.put(Integer.valueOf(lineNumber), topicsAndFreqs);
                }

                if (keywordsPerTopic.isEmpty()) {
                    return;
                }
                byte[] topicsAsArray = Converters.byteArraySerializerForAnyObject(jsonResultAsString);

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
                excelFileToSave = DefaultStreamedContent.builder()
                        .name("results_topics.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();

                progress = 100;
                WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topics", System.currentTimeMillis());
                progress = 100;
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(TopicsBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
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
        return excelFileToSave;
    }

    public void setExcelFileToSave(StreamedContent excelFileToSave) {
        this.excelFileToSave = excelFileToSave;
    }

    public StreamedContent getGexfFile() {
        return gexfFile;
    }

    public void setGexfFile(StreamedContent gexfFile) {
        this.gexfFile = gexfFile;
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
}
