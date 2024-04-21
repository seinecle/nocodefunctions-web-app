package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephisto;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class GazeBean implements Serializable {

    private Integer progress = 0;
    private Integer minSharedTargets = 1;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private Boolean shareVVPublicly;
    private Boolean shareGephiLitePublicly;
    private boolean applyPMI = false;

    private String dataPersistenceUniqueId;
    private String sessionId;
    private boolean gexfHasArrived = false;

    private Properties privateProperties;

    private String gexf;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    DataImportBean dataImportBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public GazeBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("networkconverter");
        privateProperties = applicationProperties.getPrivateProperties();
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
    }

    public void onTabChange(String sheetName) {
        dataImportBean.setSelectedSheetName(sheetName);
    }

    public void pollingDidTopNodesArrive() {
        String key = dataPersistenceUniqueId + "topnodes";
        boolean topNodesHaveArrived = WatchTower.getQueueOutcomesProcesses().containsKey(dataPersistenceUniqueId + "topnodes");
        if (topNodesHaveArrived) {
            WatchTower.getQueueOutcomesProcesses().remove(key);
            runButtonDisabled = false;
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/gaze/results.xhtml?faces-redirect=true");
        }
    }

    public void navigatToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/gaze/results.xhtml?faces-redirect=true");
    }

    public void runCoocAnalysis() {
        try {
            dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
            progress = 0;
            sessionBean.sendFunctionPageReport();
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = dataImportBean.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(dataImportBean.getSelectedSheetName())) {
                    sheetWithData = sm;
                    break;
                }
            }
            if (sheetWithData == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (1)");
                return;
            }
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            Map<Integer, Multiset<String>> lines = new HashMap();

            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();
            int i = 0;
            Multiset<String> multiset;
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> next = iterator.next();
                multiset = new Multiset();
                for (CellRecord cr : next.getValue()) {
                    multiset.addOne(cr.getRawValue());
                }
                lines.put(i++, multiset);
            }
            if (lines.isEmpty()) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (2)");
                return;
            }

            if (dataImportBean.getHasHeaders()) {
                lines.remove(0);
            }

            callCooc(lines);
            getTopNodes();
            runButtonDisabled = true;
        } catch (Exception ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void runSimAnalysis(String sourceColIndex, String sheetName) {
        try {
            dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
            sessionBean.sendFunctionPageReport();
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = dataImportBean.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(sheetName)) {
                    sheetWithData = sm;
                    break;
                }
            }
            if (sheetWithData == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (1)");
            }
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            if (dataImportBean.getHasHeaders()) {
                mapOfCellRecordsPerRow.remove(0);
            }
            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();

            Map<String, Set<String>> sourcesAndTargets = new HashMap();
            Set<String> setTargets;
            String source = "";
            int sourceIndexInt = Integer.parseInt(sourceColIndex);
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> entryCellRecordsInRow = iterator.next();
                setTargets = new HashSet();
                for (CellRecord cr : entryCellRecordsInRow.getValue()) {
                    if (cr.getColIndex() == sourceIndexInt) {
                        source = cr.getRawValue();
                    } else {
                        setTargets.add(cr.getRawValue());
                    }
                }
                if (sourcesAndTargets.containsKey(source)) {
                    Set<String> existingTargetsForThisSource = sourcesAndTargets.get(source);
                    existingTargetsForThisSource.addAll(setTargets);
                    sourcesAndTargets.put(source, existingTargetsForThisSource);
                } else {
                    sourcesAndTargets.put(source, setTargets);
                }
            }
            if (sourcesAndTargets.isEmpty()) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (2)");
            }
            callSim(sourcesAndTargets);
            getTopNodes();
        } catch (NumberFormatException ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void callCooc(Map<Integer, Multiset<String>> inputLines) throws Exception {
        HttpRequest request;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
        JsonObjectBuilder overallObject = Json.createObjectBuilder();

        JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
        for (Map.Entry<Integer, Multiset<String>> entryLines : inputLines.entrySet()) {
            JsonArrayBuilder createArrayBuilder = Json.createArrayBuilder();
            Multiset<String> targets = entryLines.getValue();
            for (String target : targets.toListOfAllOccurrences()) {
                createArrayBuilder.add(target);
            }
            linesBuilder.add(String.valueOf(entryLines.getKey()), createArrayBuilder);
        }
        String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/gaze";

        overallObject.add("lines", linesBuilder);
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
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                .withHost("localhost")
                .withPath("api/gaze/cooc")
                .toUri();

        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            String error = resp.body();
            System.out.println(error);
            logBean.addOneNotificationFromString(error);
            runButtonDisabled = false;
        }
    }

    public void callSim(Map<String, Set<String>> sourcesAndTargets) {
        try {
            HttpRequest request;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
            for (Map.Entry<String, Set<String>> entryLines : sourcesAndTargets.entrySet()) {
                JsonArrayBuilder createArrayBuilder = Json.createArrayBuilder();
                Set<String> targets = entryLines.getValue();
                for (String target : targets) {
                    createArrayBuilder.add(target);
                }
                linesBuilder.add(entryLines.getKey(), createArrayBuilder);
            }

            String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/gaze";

            JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
            parametersBuilder.add("minSharedTarget", minSharedTargets);

            overallObject.add("lines", linesBuilder);
            overallObject.add("parameters", parametersBuilder);
            overallObject.add("sessionId", sessionId);
            overallObject.add("dataPersistenceId", dataPersistenceUniqueId);
            overallObject.add("callbackURL", callbackURL);

            String jsonString = overallObject.build().toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withHost("localhost")
                    .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))))
                    .withPath("api/gaze/sim")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String error = resp.body();
                System.out.println(error);
                logBean.addOneNotificationFromString(error);
                runButtonDisabled = false;
            }
        } catch (IOException | InterruptedException ex) {
            System.out.println("exception when getting top nodes: " + ex);
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
                    runButtonDisabled = false;
                }
            });
        }
        );
    }

    public void gotoVV() {
        String apiPort = privateProperties.getProperty("nocode_api_port");
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexf(gexf, apiPort, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
        if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(linkToVosViewer);
            } catch (IOException ex) {
                System.out.println("error in ops for export to vv");
            }
        }
    }

    public void gotoGephiLite() {
        if (gexf == null || gexf.isBlank()) {
            String errorMessage = "gexf file was null or empty, not possible to go to Gephi Lite";
            logBean.addOneNotificationFromString(errorMessage);

        }
        Path userGeneratedGephiLiteDirectoryFullPath = applicationProperties.getUserGeneratedGephiLiteDirectoryFullPath(shareGephiLitePublicly);
        Path relativePathFromProjectRootToGephiLiteFolder = applicationProperties.getRelativePathFromProjectRootToGephiLiteFolder();
        Path gephiLiteRootFullPath = applicationProperties.getGephiLiteRootFullPath();
        String urlToGephiLite = ExportToGephiLite.exportAndReturnLink(gexf, userGeneratedGephiLiteDirectoryFullPath, relativePathFromProjectRootToGephiLiteFolder, gephiLiteRootFullPath);
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        try {
            externalContext.redirect(urlToGephiLite);
        } catch (IOException ex) {
            System.out.println("error in redirect to Gephi Lite");
        }
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

    public int getMaxFreqNode() {
        return maxFreqNode;
    }

    public void setMaxFreqNode(int maxFreqNode) {
        this.maxFreqNode = maxFreqNode;
    }

    public Boolean getShareVVPublicly() {
        return shareVVPublicly;
    }

    public void setShareVVPublicly(Boolean shareVVPublicly) {
        this.shareVVPublicly = shareVVPublicly;
    }

    public Boolean getShareGephiLitePublicly() {
        return shareGephiLitePublicly;
    }

    public void setShareGephiLitePublicly(Boolean shareGephiLitePublicly) {
        this.shareGephiLitePublicly = shareGephiLitePublicly;
    }

    public boolean isApplyPMI() {
        return applyPMI;
    }

    public void setApplyPMI(boolean applyPMI) {
        this.applyPMI = applyPMI;
    }

    public Integer getMinSharedTargets() {
        return minSharedTargets;
    }

    public void setMinSharedTargets(Integer minSharedTargets) {
        this.minSharedTargets = minSharedTargets;
    }
}
