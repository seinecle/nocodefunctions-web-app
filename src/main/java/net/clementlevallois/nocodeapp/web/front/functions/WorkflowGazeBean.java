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
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.SESSION_ID;
import net.clementlevallois.functions.model.WorkflowGazeProps;
import net.clementlevallois.functions.model.WorkflowGazeProps.BodyJsonKeys;
import static net.clementlevallois.functions.model.WorkflowGazeProps.BodyJsonKeys.LINES;
import static net.clementlevallois.functions.model.WorkflowGazeProps.QueryParams.MIN_SHARED_TARGETS;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.IS_SCIENTIFIC_CORPUS;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.LANG;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.LEMMATIZE;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.MIN_CHAR_NUMBER;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.MIN_TERM_FREQ;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.PRECISION;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.REMOVE_ACCENTS;
import static net.clementlevallois.functions.model.WorkflowTopicsProps.QueryParams.REPLACE_STOPWORDS;
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
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.PostRequestBuilder;

@Named
@SessionScoped
public class WorkflowGazeBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(WorkflowGazeBean.class.getName());

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

    private String jobId;
    private String sessionId;

    private String gexf; // Used for export methods

    private WorkflowGazeProps props;
    private Globals globals;

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

    public WorkflowGazeBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction(WorkflowGazeProps.NAME);
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
        props = new WorkflowGazeProps(applicationProperties.getTempFolderFullPath());
        globals = new Globals(applicationProperties.getTempFolderFullPath());
    }

    public void onTabChange(String sheetName) {
        dataImportBean.setSelectedSheetName(sheetName);
    }

    public void pollingDidTopNodesArrive() {
        Path pathSignalWorkflowComplete = props.getWorkflowCompleteFilePath(jobId);
        boolean workflowComplete = Files.exists(pathSignalWorkflowComplete);
        if (workflowComplete) {
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
            jobId = UUID.randomUUID().toString().substring(0, 10);
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
            runButtonDisabled = true;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error running Cooc analysis", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", "Could not start Cooc analysis: " + ex.getMessage());
            runButtonDisabled = false;
        }
    }

    public void runSimAnalysis(String sourceColIndex, String sheetName) {
        try {
            jobId = UUID.randomUUID().toString().substring(0, 10);
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
                .add(LINES.name(), linesBuilder)
                .build();

        var requestBuilder = microserviceClient.api().post(WorkflowGazeProps.ENDPOINT_COOC)
                .withJsonPayload(jsonPayload);

        addGlobalQueryParams(requestBuilder);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String error = response.body();
                        LOG.log(Level.SEVERE, "Cooc call failed. Status: {0}, Body: {1}", new Object[]{response.statusCode(), error});
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cooc Failed", "Could not send to Cooc microservice: " + error);
                    } else {
                        LOG.log(Level.INFO, "Cooc task submitted successfully for dataId: {0}", jobId);
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during async Cooc call for dataId " + jobId, e);
                    String errorMessage = "Exception communicating with Cooc microservice: " + e.getMessage();
                    if (e.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                    }
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cooc Failed", errorMessage);
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
                .add(LINES.name(), linesBuilder)
                .build();

        var requestBuilder = microserviceClient.api().post(WorkflowGazeProps.ENDPOINT_SIM)
                .withJsonPayload(jsonPayload);

        addQueryParamsForSim(requestBuilder);
        addGlobalQueryParams(requestBuilder);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String error = response.body();
                        LOG.log(Level.SEVERE, "Sim call failed. Status: {0}, Body: {1}", new Object[]{response.statusCode(), error});
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Sim Failed", "Could not send to Sim microservice: " + error);
                    } else {
                        LOG.log(Level.INFO, "Sim task submitted successfully for dataId: {0}", jobId);
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during async Sim call for dataId " + jobId, e);
                    String errorMessage = "Exception communicating with Sim microservice: " + e.getMessage();
                    if (e.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                    }
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Sim Failed", errorMessage);
                    return null;
                });
    }

    private MicroserviceHttpClient.PostRequestBuilder addQueryParamsForSim(PostRequestBuilder requestBuilder) {
        for (WorkflowGazeProps.QueryParams param : WorkflowGazeProps.QueryParams.values()) {
            String paramValue = null;
            switch (param) {
                case MIN_SHARED_TARGETS ->
                    paramValue = String.valueOf(minSharedTargets);
            }
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }
        return requestBuilder;

    }

    private MicroserviceHttpClient.PostRequestBuilder addGlobalQueryParams(PostRequestBuilder requestBuilder) {

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowGazeProps.ENDPOINT_GAZE;

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = null;
            switch (param) {
                case SESSION_ID ->
                    paramValue = sessionId;
                case JOB_ID ->
                    paramValue = jobId;
                case CALLBACK_URL ->
                    paramValue = callbackURL;
            }
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }
        return requestBuilder;
    }

    public void gotoVV() {
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        String linkToVosViewer = ExportToVosViewer.finishOpsFromGraphAsJson(jobId, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
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
        if (jobId == null || jobId.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Navigation Error", "Analysis ID not set. Cannot navigate to Gephi Lite.");
            return;
        }
        Path userGeneratedGephiLiteDirectoryFullPath = applicationProperties.getUserGeneratedGephiLiteDirectoryFullPath(shareGephiLitePublicly);
        Path relativePathFromProjectRootToGephiLiteFolder = applicationProperties.getRelativePathFromProjectRootToGephiLiteFolder();
        Path gephiLiteRootFullPath = applicationProperties.getGephiLiteRootFullPath();
        String urlToGephiLite = ExportToGephiLite.exportAndReturnLink(jobId, userGeneratedGephiLiteDirectoryFullPath, relativePathFromProjectRootToGephiLiteFolder, gephiLiteRootFullPath);
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
        if (jobId == null || jobId.isEmpty()) {
            LOG.warning("Cannot provide GEXF file for download, jobId is null or empty.");
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "Job ID not set. Cannot download.");
            return new DefaultStreamedContent();
        }
        try {
            Path gexfFilePath = props.getGexfFilePath(jobId);
            if (Files.exists(gexfFilePath)) {
                String gexfAsString = Files.readString(gexfFilePath, StandardCharsets.UTF_8);
                return GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "results_gaze");
            } else {
                LOG.log(Level.WARNING, "GEXF result file not found for dataId: {0}", jobId);
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
        try {
            Path pathOfTopNodesData = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
            String json = Files.readString(pathOfTopNodesData);
            JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
            nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
            return nodesAsJson;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error loading nodes as json", ex);
        }
        return nodesAsJson;
    }

    public void setNodesAsJson(String nodesAsJson) {
        this.nodesAsJson = nodesAsJson;
    }

    public String getEdgesAsJson() {
        try {
            Path pathOfTopNodesData = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
            String json = Files.readString(pathOfTopNodesData);
            JsonObject jsonObject = Json.createReader(new StringReader(json)).readObject();
            edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
            return edgesAsJson;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error loading edges as json", ex);
        }
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