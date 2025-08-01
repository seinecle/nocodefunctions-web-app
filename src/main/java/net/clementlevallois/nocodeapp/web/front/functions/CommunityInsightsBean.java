package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportGraphBean;
import org.primefaces.PrimeFaces;
import org.primefaces.model.StreamedContent;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;

@Named
@SessionScoped
@MultipartConfig
public class CommunityInsightsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CommunityInsightsBean.class.getName());

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
    private boolean insightsHaveArrived = false; // This flag might be redundant if taskComplete/taskSuccess are used

    private volatile boolean taskComplete = false;
    private volatile boolean taskSuccess = false;

    // Removed unused mapOfLines and inputData injections as per TopicsBean model
    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    ImportGraphBean importGraphBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Resource
    private ManagedExecutorService managedExecutorService;

    public CommunityInsightsBean() {
    }

    @PostConstruct
    public void init() {
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
        return ""; // Assuming report is generated from results later
    }

    public void runAnalysis() {
        runButtonText = sessionBean.getLocaleBundle().getString("general.message.wait_long_operation");
        progress = 0;
        runButtonDisabled = true;
        taskComplete = false;
        taskSuccess = false;
        insightsHaveArrived = false; // Reset flag

        try {
            if (importGraphBean.getJobId() != null) {
                this.dataPersistenceUniqueId = importGraphBean.getJobId();
            } else {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
                runButtonDisabled = false;
                PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
                return;
            }

            managedExecutorService.submit(() -> {
                sendRequestToMicroserviceAsync();
            });

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        } catch (Exception e) { // Catch potential exceptions during parameter building or executor submission
            LOG.log(Level.SEVERE, "Error initiating analysis", e);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis: " + e.getMessage());
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            runButtonDisabled = false;
            taskComplete = false;
            taskSuccess = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
        }
    }

    private void sendRequestToMicroserviceAsync() {
        String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/workflow/community-insights";

        microserviceClient.api().post("/api/workflow/community-insights")
                .addQueryParameter("selectedLanguage", selectedLanguage)
                .addQueryParameter("selectedAttributeForText", selectedAttributeForText)
                .addQueryParameter("selectedAttributeForCommunity", selectedAttributeForCommunity)
                .addQueryParameter("minCommunitySize", String.valueOf(minCommunitySize))
                .addQueryParameter("maxKeyNodesPerCommunity", String.valueOf(maxKeyNodesPerCommunity))
                .addQueryParameter("sessionId", sessionId)
                .addQueryParameter("jobId", dataPersistenceUniqueId)
                .addQueryParameter("callbackURL", callbackURL)
                .sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    LOG.info("Successfully submitted task for dataId: " + dataPersistenceUniqueId);
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during microservice task submission for dataId " + dataPersistenceUniqueId, e);

                    // Unwrap the cause to get to the specific exception
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    String errorMessage;
                    if (cause instanceof MicroserviceCallException msce) {
                        errorMessage = "Microservice rejected task. Status: " + msce.getStatusCode() + ", Error: " + msce.getErrorBody();
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Submission Failed", "Microservice rejected task. Status: " + msce.getStatusCode());
                    } else {
                        errorMessage = "Communication error with microservice: " + cause.getMessage();
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Submission Failed", "Communication error with microservice.");
                    }
                    logBean.addOneNotificationFromString(errorMessage);
                    return null;
                });
    }

    public void checkTaskStatusForPolling() {
        if (sessionId == null || dataPersistenceUniqueId == null || taskComplete) {
            return;
        }

        Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
        Path pathOfTopNodesData = Path.of(tempFolderRelativePath.toString(), dataPersistenceUniqueId + Globals.WORKFLOW_COMPLETE_FILE_NAME_EXTENSION);
        boolean topNodesHaveArrivedSignal = Files.exists(pathOfTopNodesData);

        if (topNodesHaveArrivedSignal) {
            LOG.log(Level.INFO, "Polling detected topnodes arrival signal for dataId: {0}", dataPersistenceUniqueId);
            runButtonDisabled = false;
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            progress = 100;

            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "pollingPanel", "progressComponentId"); // Update progress component ID

            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/cowo/results.xhtml?faces-redirect=true");
        }

        ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
        if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
            Iterator<MessageFromApi> it = messagesFromApi.iterator();
            while (it.hasNext()) {
                MessageFromApi msg = it.next();
                if (msg.getjobId() != null && msg.getjobId().equals(dataPersistenceUniqueId)) {
                    LOG.log(Level.INFO, "Polling detected message for dataId {0}: {1}", new Object[]{dataPersistenceUniqueId, msg.getInfo()});

                    switch (msg.getInfo()) {
                        case ERROR -> {
                            LOG.log(Level.WARNING, "Polling detected ERROR message for dataId {0}: {1}", new Object[]{dataPersistenceUniqueId, msg.getMessage()});
                            taskComplete = true;
                            taskSuccess = false; // Indicate failure
                            runButtonDisabled = false; // Enable button
                            progress = 0; // Reset progress or indicate error state
                            logBean.addOneNotificationFromString("Analysis failed: " + msg.getMessage()); // Display error message
                            it.remove(); // Remove the message

                            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "pollingPanel");
                            PrimeFaces.current().executeScript("stopPollingWidget();");

                        }
                        case PROGRESS -> {
                            if (msg.getMessage() != null) {
                                try {
                                    this.progress = Integer.valueOf(msg.getMessage());
                                    PrimeFaces.current().ajax().update("progressComponentId"); // Update with your progress component ID
                                } catch (NumberFormatException e) {
                                    LOG.log(Level.WARNING, "Invalid progress value received: " + msg.getMessage(), e);
                                }
                            }
                            it.remove(); // Remove progress messages after processing
                        }
                        default -> {
                        }
                    }
                }
            }
        }
    }

    public void navigatToResults(AjaxBehaviorEvent event) {
        // This method can be triggered by the UI after checkTaskStatusForPolling finishes and sets flags
        if (taskComplete && taskSuccess) {
            FacesContext context = FacesContext.getCurrentInstance();
            // Navigate to the results page. The results page should use dataPersistenceUniqueId
            // to load the saved results from disk.
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/community-insights/results.xhtml?faces-redirect=true");
        } else if (taskComplete && !taskSuccess) {
            logBean.addOneNotificationFromString("Cannot navigate to results: Task failed.");
            // Stay on the current page and show error messages
        }
        // If task not complete, navigation is likely blocked by UI logic
    }

    // Removed generateInputDataAndDataId as it's not present in the original CommunityInsightsBean code
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
        // Used by PrimeFaces for AJAX updates without explicit action
    }

    public StreamedContent getExcelFileToSave() {
        // This method is responsible for providing the download stream.
        // It needs to read the results from disk (using dataPersistenceUniqueId)
        // and potentially call an export microservice (like in TopicsBean).
        // The original code didn't show the implementation, so leaving as is,
        // but note it would likely use microserviceClient.importService() if an export service is involved.
        return excelFileToSave; // Placeholder
    }

    public void setExcelFileToSave(StreamedContent excelFileToSave) {
        this.excelFileToSave = excelFileToSave;
    }

    public StreamedContent getGexfFile() {
        // Similar to getExcelFileToSave, needs implementation to read/export GEXF.
        // If the GEXF is saved to disk by the analysis microservice, read it here.
        // If an export service is needed, use microserviceClient.importService().
        return gexfFile; // Placeholder
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
        FacesContext context = FacesContext.getCurrentInstance();
        Locale requestLocale = (context != null) ? context.getExternalContext().getRequestLocale() : Locale.getDefault();
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
