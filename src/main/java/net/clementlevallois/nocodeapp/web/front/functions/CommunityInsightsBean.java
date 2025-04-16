package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.HashMap;
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
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportGraphBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig

public class CommunityInsightsBean implements Serializable {

    private Integer progress = 0;
    private String jsonResultAsString;
    private Boolean runButtonDisabled = false;
    private StreamedContent excelFileToSave;
    private StreamedContent gexfFile;
    private String selectedLanguage;
    private String selectedAttributeForText;
    private String selectedAttributeForCommunity;

    private Integer minCommunitySize = 10;
    private Integer maxKeyNodesPerCommunity = 5;
    
    private String runButtonText = "";
    private String dataPersistenceUniqueId = "";
    private String sessionId;
    private boolean insightsHaveArrived = false;

    private Properties privateProperties;

    private Map<Integer, String> mapOfLines;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ImportGraphBean importGraphBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public CommunityInsightsBean() {
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
        insightsHaveArrived = false;
        sendCallToTopicsFunction();
        formatTopicsInVariousWays();
    }

    public void pollingDidEndResultsArrive() {
        String key = dataPersistenceUniqueId + "community-insights";
        insightsHaveArrived = WatchTower.getQueueOutcomesProcesses().containsKey(key);
        if (insightsHaveArrived) {
            WatchTower.getQueueOutcomesProcesses().remove(key);
            runButtonDisabled = false;
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/community-insights/results.xhtml?faces-redirect=true");
        }
    }

    public void navigatToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/community-insights/results.xhtml?faces-redirect=true");
    }

    public void sendCallToTopicsFunction() {
        progress = 0;
        HttpRequest request;
        HttpClient client;
        try {
            sessionBean.sendFunctionPageReport();
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

            if (importGraphBean.getDataPersistenceUniqueId() != null) {
                dataPersistenceUniqueId = importGraphBean.getDataPersistenceUniqueId();
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


            String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/community-insights";
            
            overallObject.add("lang", selectedLanguage);
            overallObject.add("userSuppliedCommunityFieldName", selectedAttributeForCommunity);
            overallObject.add("maxKeyNodesPerCommunity", maxKeyNodesPerCommunity);
            overallObject.add("minCommunitySize", minCommunitySize);
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
                    .withPath("api/graphops/keynodes")
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

                insightsHaveArrived = false;

                while (!insightsHaveArrived && WatchTower.getCurrentSessions().containsKey(sessionId)) {
                    ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
                    if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
                        Iterator<MessageFromApi> it = messagesFromApi.iterator();
                        while (it.hasNext()) {
                            MessageFromApi msg = it.next();
                            if (msg.getInfo().equals(MessageFromApi.Information.RESULT_ARRIVED) && msg.getDataPersistenceId().equals(dataPersistenceUniqueId)) {
                                insightsHaveArrived = true;
                                it.remove();
                            }
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(CommunityInsightsBean.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                insightsHaveArrived = false;

                if (!WatchTower.getCurrentSessions().containsKey(sessionId)) {
                    return;
                }

                progress = 80;

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

                Map<String,Multiset<String>> topNodesPerCommunity = new HashMap();
                
                JsonObject topNodesPerCommunityAsJson = jsonObject.getJsonObject("keywordsPerTopic");
                for (String keyCommunity : topNodesPerCommunityAsJson.keySet()) {
                    JsonObject termsAndFrequenciesForThisCommunity = topNodesPerCommunityAsJson.getJsonObject(keyCommunity);
                    Iterator<String> iteratorTerms = termsAndFrequenciesForThisCommunity.keySet().iterator();
                    Multiset<String> termsAndFreqs = new Multiset();
                    while (iteratorTerms.hasNext()) {
                        String nextTerm = iteratorTerms.next();
                        termsAndFreqs.addSeveral(nextTerm, termsAndFrequenciesForThisCommunity.getInt(nextTerm));
                    }
                    topNodesPerCommunity.put(keyCommunity, termsAndFreqs);
                }
                
                
                  Map<String,Multiset<Integer>> topicsPerCommunity = new HashMap();
                  
                JsonObject topicsPerCommunityAsJson = jsonObject.getJsonObject("topicsPerLine");
                for (String lineNumber : topicsPerCommunityAsJson.keySet()) {
                    JsonObject topicsAndTheirCountsForOneLine = topicsPerCommunityAsJson.getJsonObject(lineNumber);
                    Iterator<String> iteratorTopics = topicsAndTheirCountsForOneLine.keySet().iterator();
                    Multiset<Integer> topicsAndFreqs = new Multiset();
                    while (iteratorTopics.hasNext()) {
                        String nextTopic = iteratorTopics.next();
                        topicsAndFreqs.addSeveral(Integer.valueOf(nextTopic), topicsAndTheirCountsForOneLine.getInt(nextTopic));
                    }
                    topicsPerCommunity.put(lineNumber, topicsAndFreqs);
                }

                if (topicsPerCommunity.isEmpty()) {
                    return;
                }
                byte[] topicsAsArray = Converters.byteArraySerializerForAnyObject(jsonResultAsString);

                HttpRequest.BodyPublisher bodyPublisherTopics = HttpRequest.BodyPublishers.ofByteArray(topicsAsArray);

                URI uriExportTopicsToExcel = UrlBuilder
                        .empty()
                        .withScheme("http")
                        .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                        .withHost("localhost")
                        .withPath("api/export/xlsx/community_insights")
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
                        .name("community_insights.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();

                progress = 100;
                WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topics", System.currentTimeMillis());
                progress = 100;
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(CommunityInsightsBean.class.getName()).log(Level.SEVERE, null, ex);
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

    public List<String> getNodeAttributesForText() {
        return importGraphBean.getNamesOfNodeAttributes();
    }

    public List<String> getNodeAttributesForModularity() {
        return importGraphBean.getNamesOfNodeAttributes();
    }

    public String getSelectedAttributeForText() {
        return selectedAttributeForText;
    }

    public void setSelectedAttributeForText(String selectedAttributeForText) {
        this.selectedAttributeForText = selectedAttributeForText;
    }

    public String getSelectedAttributeForCommunity() {
        return selectedAttributeForCommunity;
    }

    public void setSelectedAttributeForCommunity(String selectedAttributeForCommunity) {
        this.selectedAttributeForCommunity = selectedAttributeForCommunity;
    }

    public Integer getMinCommunitySize() {
        return minCommunitySize;
    }

    public void setMinCommunitySize(Integer minCommunitySize) {
        this.minCommunitySize = minCommunitySize;
    }

    public Integer getMaxKeyNodesPerCommunity() {
        return maxKeyNodesPerCommunity;
    }

    public void setMaxKeyNodesPerCommunity(Integer maxKeyNodesPerCommunity) {
        this.maxKeyNodesPerCommunity = maxKeyNodesPerCommunity;
    }
    
    
    
    
    
    
}
