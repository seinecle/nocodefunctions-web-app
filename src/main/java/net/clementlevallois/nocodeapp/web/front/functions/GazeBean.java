package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
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
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;



@Named
@SessionScoped
public class GazeBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(GazeBean.class.getName());

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

    // private Properties privateProperties; // No longer needed directly

    private String gexf; // Used for export methods

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    DataImportBean dataImportBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;


    public GazeBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("gaze");
        // privateProperties = applicationProperties.getPrivateProperties(); // Initialized in MicroserviceHttpClient
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
            LOG.log(Level.SEVERE, "Error running Cooc analysis", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", "Could not start Cooc analysis: " + ex.getMessage());
             runButtonDisabled = false;
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
                 runButtonDisabled = false; // Enable button on error
                 return;
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
                 runButtonDisabled = false; // Enable button on error
                 return;
            }

            callSim(sourcesAndTargets);
            getTopNodes();
             runButtonDisabled = true; // Disable button while processing

        } catch (NumberFormatException ex) {
            LOG.log(Level.SEVERE, "Error parsing source column index", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", "Invalid source column index: " + ex.getMessage());
             runButtonDisabled = false; // Enable button on error
        } catch (Exception ex) {
             LOG.log(Level.SEVERE, "Error running Sim analysis", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", "Could not start Sim analysis: " + ex.getMessage());
             runButtonDisabled = false; // Enable button on error
        }
    }

    public void callCooc(Map<Integer, Multiset<String>> inputLines) {
        JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
        for (Map.Entry<Integer, Multiset<String>> entryLines : inputLines.entrySet()) {
            JsonArrayBuilder createArrayBuilder = Json.createArrayBuilder();
            Multiset<String> targets = entryLines.getValue();
            for (String target : targets.toListOfAllOccurrences()) {
                createArrayBuilder.add(target);
            }
            linesBuilder.add(String.valueOf(entryLines.getKey()), createArrayBuilder);
        }

        JsonObject jsonPayload = Json.createObjectBuilder()
            .add("lines", linesBuilder)
            .build();

        String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/gaze";

        microserviceClient.api().post("/api/gaze/cooc")
            .withJsonPayload(jsonPayload)
            .addQueryParameter("sessionId", sessionId)
            .addQueryParameter("dataPersistenceId", dataPersistenceUniqueId)
            .addQueryParameter("callbackURL", callbackURL)
            .sendAsync(HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() != 200) {
                    String error = response.body();
                    LOG.log(Level.SEVERE, "Cooc call failed. Status: {0}, Body: {1}", new Object[]{response.statusCode(), error});
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cooc Failed", "Could not send to Cooc microservice: " + error);
                } else {
                    LOG.log(Level.INFO, "Cooc task submitted successfully for dataId: {0}", dataPersistenceUniqueId);
                }
            })
            .exceptionally(e -> {
                LOG.log(Level.SEVERE, "Exception during async Cooc call for dataId " + dataPersistenceUniqueId, e);
                String errorMessage = "Exception communicating with Cooc microservice: " + e.getMessage();
                 if (e.getCause() instanceof MicroserviceCallException msce) {
                     errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                 }
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cooc Failed", errorMessage);
                 // UI update handled by pollingDidTopNodesArrive on error signal
                return null;
            });
    }

    public void callSim(Map<String, Set<String>> sourcesAndTargets) {
        JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
        for (Map.Entry<String, Set<String>> entryLines : sourcesAndTargets.entrySet()) {
            JsonArrayBuilder createArrayBuilder = Json.createArrayBuilder();
            Set<String> targets = entryLines.getValue();
            for (String target : targets) {
                createArrayBuilder.add(target);
            }
            linesBuilder.add(entryLines.getKey(), createArrayBuilder);
        }

        JsonObject jsonPayload = Json.createObjectBuilder()
            .add("lines", linesBuilder)
            .build();

        String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/gaze";

        microserviceClient.api().post("/api/gaze/sim")
            .withJsonPayload(jsonPayload)
            // Parameters from the original JSON payload moved to query parameters
            .addQueryParameter("minSharedTarget", String.valueOf(minSharedTargets))
            .addQueryParameter("sessionId", sessionId)
            .addQueryParameter("dataPersistenceId", dataPersistenceUniqueId)
            .addQueryParameter("callbackURL", callbackURL)
            .sendAsync(HttpResponse.BodyHandlers.ofString())
            .thenAccept(response -> {
                if (response.statusCode() != 200) {
                    String error = response.body();
                    LOG.log(Level.SEVERE, "Sim call failed. Status: {0}, Body: {1}", new Object[]{response.statusCode(), error});
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Sim Failed", "Could not send to Sim microservice: " + error);
                     // UI update handled by pollingDidTopNodesArrive on error signal
                } else {
                    LOG.log(Level.INFO, "Sim task submitted successfully for dataId: {0}", dataPersistenceUniqueId);
                    // Microservice will send RESULT_ARRIVED via callback
                }
            })
            .exceptionally(e -> {
                LOG.log(Level.SEVERE, "Exception during async Sim call for dataId " + dataPersistenceUniqueId, e);
                String errorMessage = "Exception communicating with Sim microservice: " + e.getMessage();
                 if (e.getCause() instanceof MicroserviceCallException msce) {
                     errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                 }
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Sim Failed", errorMessage);
                 // UI update handled by pollingDidTopNodesArrive on error signal
                return null;
            });
    }

    private void getTopNodes() {
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
                            LOG.log(Level.INFO, "Polling detected RESULT_ARRIVED message for dataId: {0}", dataPersistenceUniqueId);
                        }
                    }
                }
                if (!gexfHasArrived) {
                     try {
                         Thread.sleep(500);
                     } catch (InterruptedException ex) {
                         LOG.log(Level.SEVERE, "Polling thread interrupted", ex);
                         Thread.currentThread().interrupt();
                         break;
                     }
                }
            }

            if (!WatchTower.getCurrentSessions().containsKey(sessionId)) {
                 LOG.warning("Session expired while waiting for GEXF result for dataId: " + dataPersistenceUniqueId);
                 sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Analysis Timeout", "Session expired while waiting for results.");
                 runButtonDisabled = false; // Enable button
                 // WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topnodes", System.currentTimeMillis()); // Signal failure to polling?
                 return;
            }

            LOG.log(Level.INFO, "Fetching top nodes JSON for dataId: {0}", dataPersistenceUniqueId);
            microserviceClient.api().get("/api/graphops/topnodes")
                .addQueryParameter("nbNodes", "30")
                .addQueryParameter("dataPersistenceId", dataPersistenceUniqueId)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .thenAccept(jsonResultString -> {
                    try {
                        JsonObject jsonObject = Json.createReader(new StringReader(jsonResultString)).readObject();
                        nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
                        edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));

                        WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topnodes", System.currentTimeMillis());
                        LOG.log(Level.INFO, "Top nodes JSON fetched and signal sent for dataId: {0}", dataPersistenceUniqueId);

                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Error processing top nodes JSON response for dataId: " + dataPersistenceUniqueId, e);
                         sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Results Error", "Error processing results: " + e.getMessage());
                         // Signal failure to polling?
                         // WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topnodes", System.currentTimeMillis());
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during async getTopNodes call for dataId " + dataPersistenceUniqueId, e);
                    String errorMessage = "Failed to fetch results: " + e.getMessage();
                     if (e.getCause() instanceof MicroserviceCallException) {
                         MicroserviceCallException msce = (MicroserviceCallException) e.getCause();
                         errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                     }
                     sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Results Error", errorMessage);
                     // Signal failure to polling?
                     // WatchTower.getQueueOutcomesProcesses().put(dataPersistenceUniqueId + "topnodes", System.currentTimeMillis());

                    return null;
                });
        });
    }

    public void gotoVV() {
        String apiPort = applicationProperties.getPrivateProperties().getProperty("nocode_api_port");
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        String linkToVosViewer = ExportToVosViewer.finishOpsFromGraphAsJson(dataPersistenceUniqueId, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
        if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(linkToVosViewer);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error redirecting to VosViewer", ex);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Navigation Error", "Could not navigate to VosViewer.");
            }
        } else {
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Navigation Error", "Could not generate link to VosViewer. Ensure analysis completed successfully.");
        }
    }

    public void gotoGephiLite() {
         if (dataPersistenceUniqueId == null || dataPersistenceUniqueId.isEmpty()) {
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Navigation Error", "Analysis ID not set. Cannot navigate to Gephi Lite.");
             return;
         }
        // This method uses a helper class that handles the export and link generation.
        // It does not directly use MicroserviceHttpClient for the export itself,
        // but the helper might.
        Path userGeneratedGephiLiteDirectoryFullPath = applicationProperties.getUserGeneratedGephiLiteDirectoryFullPath(shareGephiLitePublicly);
        Path relativePathFromProjectRootToGephiLiteFolder = applicationProperties.getRelativePathFromProjectRootToGephiLiteFolder();
        Path gephiLiteRootFullPath = applicationProperties.getGephiLiteRootFullPath();
        // The original code passed 'gexf' String here, but the refactored code relies on dataPersistenceUniqueId
        // to locate the GEXF file on disk. Assuming ExportToGephiLite is adapted for this.
        String urlToGephiLite = ExportToGephiLite.exportAndReturnLink(dataPersistenceUniqueId, userGeneratedGephiLiteDirectoryFullPath, relativePathFromProjectRootToGephiLiteFolder, gephiLiteRootFullPath);
        if (urlToGephiLite != null && !urlToGephiLite.isBlank()) {
             ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
             try {
                 externalContext.redirect(urlToGephiLite);
             } catch (IOException ex) {
                 LOG.log(Level.SEVERE, "Error redirecting to Gephi Lite", ex);
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Navigation Error", "Could not navigate to Gephi Lite.");
             }
        } else {
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Navigation Error", "Could not generate link to Gephi Lite. Ensure analysis completed successfully.");
        }
    }

    public StreamedContent getFileToSave() {
        if (dataPersistenceUniqueId == null || dataPersistenceUniqueId.isEmpty()) {
             LOG.warning("Cannot provide GEXF file for download, dataPersistenceUniqueId is null or empty.");
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "Analysis ID not set. Cannot download.");
             return new DefaultStreamedContent();
         }
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path gexfFilePath = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId + "_result.gexf");

            if (Files.exists(gexfFilePath)) {
                String gexfAsString = Files.readString(gexfFilePath, StandardCharsets.UTF_8);
                return GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "results_gaze");
            } else {
                LOG.log(Level.WARNING, "GEXF result file not found for dataId: {0}", dataPersistenceUniqueId);
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "GEXF file not found.");
                return new DefaultStreamedContent();
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading GEXF file for download", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Download Error", "Error providing GEXF file for download.");
            return new DefaultStreamedContent();
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
