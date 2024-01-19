package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.concurrent.Executors;
import static java.util.stream.Collectors.toList;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephisto;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.LogBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
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

public class CowoBean implements Serializable {

    private Integer progress = 0;
    private Boolean runButtonDisabled = false;
    private StreamedContent fileToSave;
    private String selectedLanguage;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private int minTermFreq = 2;
    private int maxNGram = 4;
    private int minCoocFreqInt = 2;
    private boolean removeNonAsciiCharacters = false;
    private String typeCorrection = "none";
    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private boolean lemmatize = true;
    private boolean usePMI = false;
    private UploadedFile fileUserStopwords;
    private Boolean shareVVPublicly;
    private Boolean shareGephistoPublicly;
    private Integer minCharNumber = 4;
    private Map<Integer, String> mapOfLines;
    private Properties privateProperties;
    private boolean test = true;
    private String dataPersistenceUniqueId = "";
    private String sessionId;
    private boolean gexfHasArrived = false;

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

    public CowoBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("cowo");
        privateProperties = applicationProperties.getPrivateProperties();
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
        logBean.setSessionId(sessionId);
    }

    private void watchTower() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (!gexfHasArrived && WatchTower.getCurrentSessions().containsKey(sessionId)) {
                ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
                if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
                    for (MessageFromApi msg : messagesFromApi) {
                        if (msg.getDataPersistenceId().equals(dataPersistenceUniqueId)) {
                            gexfHasArrived = true;
                        }
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(LogBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });

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
        progress = 0;
        runButtonDisabled = true;
        gexfHasArrived = false;
//        watchTower();
        sendCallToCowoFunction();
        getTopNodes();
    }

    public void pollingDidTopNodesArrived() {
        String key = dataPersistenceUniqueId + "topnodes";
        boolean topNodesHaveArrived = WatchTower.getQueueOutcomesProcesses().containsKey(dataPersistenceUniqueId + "topnodes");
        if (topNodesHaveArrived) {
            WatchTower.getQueueOutcomesProcesses().remove(key);
            runButtonDisabled = false;
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/cowo/results.xhtml?faces-redirect=true");
        }
    }

    private void sendCallToCowoFunction() {

        try {
            sessionBean.sendFunctionPageReport();
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

            if (usePMI) {
                typeCorrection = "pmi";
            }

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
                    sb.append(entry.getValue().trim()).append("\n");
                }
                Files.writeString(fullPathForFileContainingTextInput, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
            inputData.setDataInSheets(new ArrayList());

            if (selectedLanguage == null) {
                selectedLanguage = "en";
            }

            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
            if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
                List<String> userSuppliedStopwords;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                    userSuppliedStopwords = br.lines().collect(toList());
                    int index = 0;
                    for (String stopword : userSuppliedStopwords) {
                        userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                    }
                } catch (IOException exIO) {
                    Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, exIO);
                }
            }

            String callbackURL = RemoteLocal.getDomain() + "/api/messageFromAPI/cowo";

            overallObject.add("lang", selectedLanguage);
            overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);
            overallObject.add("minCharNumber", minCharNumber);
            overallObject.add("replaceStopwords", replaceStopwords);
            overallObject.add("isScientificCorpus", scientificCorpus);
            overallObject.add("lemmatize", lemmatize);
            overallObject.add("removeAccents", removeNonAsciiCharacters);
            overallObject.add("minCoocFreq", minCoocFreqInt);
            overallObject.add("minTermFreq", minTermFreq);
            overallObject.add("maxNGram", maxNGram);
            overallObject.add("typeCorrection", typeCorrection);
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
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                    .withPath("api/cowo")
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .timeout(Duration.ofMinutes(20))
                    .uri(uri)
                    .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(20)).build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                progress = 80;
            } else {
                System.out.println("cowo returned by the API was not a 200 code");
                String errorMessage = resp.body();
                logBean.addOneNotificationFromString(errorMessage);
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void getTopNodes() {
        // waiting for the message saying the results have been persisted to disk
        Executors.newSingleThreadExecutor().execute(() -> {

            gexfHasArrived = false;

            while (!gexfHasArrived && WatchTower.getCurrentSessions().containsKey(sessionId)) {
                ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
                if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
                    Iterator<MessageFromApi> it = messagesFromApi.iterator();
                    while (it.hasNext()) {
                        MessageFromApi msg = it.next();
                        if (msg.getInfo().equals(MessageFromApi.Information.RESULT_ARRIVED) && msg.getDataPersistenceId().equals(dataPersistenceUniqueId)) {
                            gexfHasArrived = true;
                            it.remove();
                        }
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            gexfHasArrived = false;

            if (!WatchTower.getCurrentSessions().containsKey(sessionId)) {
                return;
            }

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                    .withHost("localhost")
                    .withPath("api/graphops/topnodes")
                    .addParameter("nbNodes", "30")
                    .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                    .toUri();
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(20)).build();
            CompletableFuture<HttpResponse<byte[]>> futureResponse = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
            futureResponse.thenAccept(resp -> {
                if (resp.statusCode() != 200) {
                    byte[] body = resp.body();
                    String error = new String(body, StandardCharsets.UTF_8);
                    System.out.println("top nodes returned by the API was not a 200 code");
                    System.out.println("error: " + error);
                    runButtonDisabled = false;
                } else {
                    byte[] body = resp.body();
                    String jsonResult = new String(body, StandardCharsets.UTF_8);
                    JsonObject jsonObject = Json.createReader(new StringReader(jsonResult)).readObject();
                    nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
                    edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
                    WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topnodes", System.currentTimeMillis());
                    progress = 100;
                }
            });
        }
        );
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

    public StreamedContent getFileToSave() {
        Path tempFolderRelativePath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId + "_result");
        String gexfAsString = "";
        if (Files.exists(tempFolderRelativePath)) {
            try {
                gexfAsString = Files.readString(tempFolderRelativePath, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            System.out.println("gexf file did not exist in temp folder");
            return new DefaultStreamedContent();
        }
        if (gexfAsString == null || gexfAsString.isBlank()) {
            System.out.println("gexf was null in cowo function");
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
            return new DefaultStreamedContent();
        } else {
            return GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "results");
        }
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public void execGraph(int maxNodes) {
    }

    public String getNodesAsJson() {
        return nodesAsJson;
    }

    public void setNodesAsJson(String nodesAsJson) {
        this.nodesAsJson = nodesAsJson;
    }

    public String getEdgesAsJson() {
        return edgesAsJson;
    }

    public void setEdgesAsJson(String edgesAsJson) {
        this.edgesAsJson = edgesAsJson;
    }

    public int getMinFreqNode() {
        return minFreqNode;
    }

    public void setMinFreqNode(int minFreqNode) {
        this.minFreqNode = minFreqNode;
    }

    public int getMaxNGram() {
        try {
            // these are defensive measures to avoid out of memory errors due to ngram detections on very large collections of files
            Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
            Path fullPathForFileContainingTextInput = Path.of(tempFolderRelativePath.toString(), dataPersistenceUniqueId);
            if (Files.notExists(fullPathForFileContainingTextInput)) {
                return 3;
            }
            long sizeInBytes = Files.size(fullPathForFileContainingTextInput);
            double sizeInMegabytes = sizeInBytes / 1024.0 / 1024.0;

            if (sizeInMegabytes > 70) {
                maxNGram = 2;
            } else if (sizeInMegabytes > 30) {
                maxNGram = 3;
            } else {
                maxNGram = 4;
            }
        } catch (IOException ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return maxNGram;
    }

    public void setMaxNGram(int maxNGram) {
        this.maxNGram = maxNGram;
    }

    public int getMinTermFreq() {
        return minTermFreq;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }

    public int getMaxFreqNode() {
        return maxFreqNode;
    }

    public void setMaxFreqNode(int maxFreqNode) {
        this.maxFreqNode = maxFreqNode;
    }

    public boolean isScientificCorpus() {
        return scientificCorpus;
    }

    public void setScientificCorpus(boolean scientificCorpus) {
        this.scientificCorpus = scientificCorpus;
    }

    public boolean isRemoveNonAsciiCharacters() {
        return removeNonAsciiCharacters;
    }

    public void setRemoveNonAsciiCharacters(boolean removeNonAsciiCharacters) {
        this.removeNonAsciiCharacters = removeNonAsciiCharacters;
    }

    public UploadedFile getFileUserStopwords() {
        return fileUserStopwords;
    }

    public void setFileUserStopwords(UploadedFile file) {
        this.fileUserStopwords = file;
    }

    public void uploadStopWordFile() {
        if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            String message = fileUserStopwords.getFileName() + " " + is_uploaded + ".";
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, message);
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

    public Boolean getShareVVPublicly() {
        return shareVVPublicly;
    }

    public void setShareVVPublicly(Boolean shareVVPublicly) {
        this.shareVVPublicly = shareVVPublicly;
    }

    public Boolean getShareGephistoPublicly() {
        return shareGephistoPublicly;
    }

    public void setShareGephistoPublicly(Boolean shareGephistoPublicly) {
        this.shareGephistoPublicly = shareGephistoPublicly;
    }

    public Integer getMinCharNumber() {
        return minCharNumber;
    }

    public void setMinCharNumber(Integer minCharNumber) {
        this.minCharNumber = minCharNumber;
    }

    public boolean isUsePMI() {
        return usePMI;
    }

    public void setUsePMI(boolean usePMI) {
        this.usePMI = usePMI;
    }

    public boolean isLemmatize() {
        return lemmatize;
    }

    public void setLemmatize(boolean lemmatize) {
        this.lemmatize = lemmatize;
    }

    public String getDataPersistenceUniqueId() {
        return dataPersistenceUniqueId;
    }

    public void setDataPersistenceUniqueId(String dataPersistenceUniqueId) {
        this.dataPersistenceUniqueId = dataPersistenceUniqueId;
    }

    public void gotoVV() {
        String apiPort = privateProperties.getProperty("nocode_api_port");
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexf(dataPersistenceUniqueId, apiPort, shareVVPublicly, applicationProperties);
        if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(linkToVosViewer);
            } catch (IOException ex) {
                System.out.println("error in ops for export to vv");
            }
        }
    }

    public void gotoGephisto() {
        String urlToGephisto = ExportToGephisto.exportAndReturnLink(shareGephistoPublicly, dataPersistenceUniqueId, applicationProperties);
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        try {
            externalContext.redirect(urlToGephisto);
        } catch (IOException ex) {
            System.out.println("error in redirect to Gephisto");
        }
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
