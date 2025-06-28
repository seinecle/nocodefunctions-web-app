package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import jakarta.json.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.SESSION_ID;
import net.clementlevallois.functions.model.WorkflowCoocProps;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.PostRequestBuilder;

@Named
@SessionScoped
public class WorkflowCoocBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(WorkflowCoocBean.class.getName());

    private Integer progress = 0;
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

    private String gexf;

    private WorkflowCoocProps props;
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

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    public WorkflowCoocBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunctionName(WorkflowCoocProps.NAME);
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
        props = new WorkflowCoocProps(applicationProperties.getTempFolderFullPath());
        globals = new Globals(applicationProperties.getTempFolderFullPath());
    }

    public void onTabChange(String sheetName) {
        dataImportBean.setSelectedSheetName(sheetName);
    }

    public void pollingDidTopNodesArrive() {
        Path pathSignalWorkflowComplete = globals.getWorkflowCompleteFilePath(jobId);
        boolean workflowComplete = Files.exists(pathSignalWorkflowComplete);
        if (workflowComplete) {
            runButtonDisabled = false;
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowCoocProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
        }
    }

    public void navigatToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowCoocProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
    }

    public void runCoocAnalysis() {
        progress = 0;
        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        runButtonDisabled = true;
        var requestBuilder = microserviceClient.api().post(WorkflowCoocProps.ENDPOINT);

        addGlobalQueryParams(requestBuilder);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String error = response.body();
                        LOG.log(Level.SEVERE, "Cooc call failed. Status: {0}, Body: {1}", new Object[]{response.statusCode(), error});
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cooc Failed", "Could not send to Cooc microservice: " + error);
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

    private MicroserviceHttpClient.PostRequestBuilder addGlobalQueryParams(PostRequestBuilder requestBuilder) {

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowCoocProps.ENDPOINT;

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case SESSION_ID ->
                    sessionId;
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }
        return requestBuilder;
    }

    public void gotoVV() {
        String linkToVosViewer = exportToVosViewer.finishOpsFromGraphAsJson(jobId, shareVVPublicly);
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
        String urlToGephiLite = exportToGephiLite.exportAndReturnLinkFromId(jobId, shareGephiLitePublicly);
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
}
