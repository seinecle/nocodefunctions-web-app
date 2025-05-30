package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
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
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.AjaxBehaviorEvent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;

import org.primefaces.PrimeFaces;

@Named
@SessionScoped
@MultipartConfig
public class WorkflowCowoBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(WorkflowCowoBean.class.getName());

    private Integer progress = 0;
    private Boolean runButtonDisabled = false;
    private StreamedContent fileToSave;
    private List<String> selectedLanguages = new ArrayList();
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private int minTermFreq = 2;
    private int maxNGram = 4;
    private final int minCoocFreqInt = 2;
    private boolean removeNonAsciiCharacters = false;
    private boolean scientificCorpus;
    private boolean firstNames = true;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private boolean lemmatize = true;
    private boolean usePMI = false;
    private UploadedFile fileUserStopwords;
    private Boolean shareVVPublicly;
    private Boolean shareGephiLitePublicly;
    private Integer minCharNumber = 4;
    private Map<Integer, String> mapOfLines;
    private String dataPersistenceUniqueId = "";
    private String sessionId;
    private String runButtonText = "";
    private WorkflowCowoProps functionProps;
    private Globals globals;

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

    public WorkflowCowoBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction(WorkflowCowoProps.NAME);
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
        logBean.setSessionId(sessionId);
        runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
        functionProps = new WorkflowCowoProps(applicationProperties.getTempFolderFullPath());
        globals = new Globals(applicationProperties.getTempFolderFullPath());
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
        try {
            if (simpleLinesImportBean.getDataPersistenceUniqueId() != null) {
                this.dataPersistenceUniqueId = simpleLinesImportBean.getDataPersistenceUniqueId();
            } else {
                generateInputDataAndDataId();
                if (this.dataPersistenceUniqueId == null || this.dataPersistenceUniqueId.isEmpty()) {
                    throw new IllegalStateException("Input data could not be prepared.");
                }
            }
            sendCallToCowoWorkflow();
        } catch (IllegalStateException e) {
            LOG.log(Level.SEVERE, "Error preparing analysis", e);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not prepare data for analysis: " + e.getMessage());
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            runButtonDisabled = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
        } catch (Exception e) { // Catch any other unexpected errors during initiation
            LOG.log(Level.SEVERE, "Unexpected error initiating analysis", e);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "An unexpected error occurred: " + e.getMessage());
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            runButtonDisabled = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
        }
    }

    public void pollingDidTopNodesArrive() {
        Path pathOfTopNodesData = globals.getTopNetworkVivaGraphFormattedFilePath(dataPersistenceUniqueId);
        boolean topNodesHaveArrivedSignal = Files.exists(pathOfTopNodesData);

        if (topNodesHaveArrivedSignal) {
            runButtonDisabled = false;
            runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
            progress = 100;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "pollingPanel", "progressComponentId");
            FacesContext context = FacesContext.getCurrentInstance();
            context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowCowoProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
        }
    }

    public void navigateToResults(AjaxBehaviorEvent event) {
        FacesContext context = FacesContext.getCurrentInstance();
        context.getApplication().getNavigationHandler().handleNavigation(context, null, "/" + WorkflowCowoProps.NAME + "/" + Globals.RESULTS_PAGE + Globals.FACES_REDIRECT);
    }

    private void sendCallToCowoWorkflow() {
        String correctionType = usePMI ? "pmi" : "none";

        JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
        if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> userSuppliedStopwords = br.lines().collect(toList());
                int index = 0;
                for (String stopword : userSuppliedStopwords) {
                    userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                }
            } catch (IOException exIO) {
                LOG.log(Level.SEVERE, "Error reading user supplied stopwords file", exIO);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "stopwords", "Could not read stopwords file: " + exIO.getMessage());
            }
        }
        JsonObject jsonPayload = Json.createObjectBuilder()
                .add("userSuppliedStopwords", userSuppliedStopwordsBuilder)
                .build();

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowCowoProps.ENDPOINT;
        microserviceClient.api().post(WorkflowCowoProps.ENDPOINT)
                .withJsonPayload(jsonPayload)
                .addQueryParameter("lang", String.join(",", selectedLanguages))
                .addQueryParameter("minCharNumber", String.valueOf(minCharNumber))
                .addQueryParameter("replaceStopwords", String.valueOf(replaceStopwords))
                .addQueryParameter("isScientificCorpus", String.valueOf(scientificCorpus))
                .addQueryParameter("lemmatize", String.valueOf(lemmatize))
                .addQueryParameter("removeAccents", String.valueOf(removeNonAsciiCharacters))
                .addQueryParameter("minCoocFreq", String.valueOf(minCoocFreqInt))
                .addQueryParameter("minTermFreq", String.valueOf(minTermFreq))
                .addQueryParameter("firstNames", String.valueOf(firstNames))
                .addQueryParameter("maxNGram", String.valueOf(maxNGram))
                .addQueryParameter("typeCorrection", correctionType)
                .addQueryParameter("sessionId", sessionId)
                .addQueryParameter("jobId", dataPersistenceUniqueId)
                .addQueryParameter("callbackURL", callbackURL)
                .sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        String errorBody = response.body();
                        LOG.log(Level.SEVERE, "Cowo task submission failed for dataId {0}. Status: {1}, Body: {2}", new Object[]{dataPersistenceUniqueId, response.statusCode(), errorBody});
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cowo Failed", "Could not send to Cowo microservice: " + errorBody);
                        logBean.addOneNotificationFromString("Cowo submission failed. Status: " + response.statusCode() + ", Error: " + errorBody);
                        runButtonDisabled = false; // Enable button on failure
                        runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
                        PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during Cowo task submission for dataId " + dataPersistenceUniqueId, e);
                    String errorMessage = "Exception communicating with Cowo microservice: " + e.getMessage();
                    if (e.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage += " (Status: " + msce.getStatusCode() + ", URI: " + msce.getUri() + ", Body: " + msce.getErrorBody() + ")";
                    }
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Cowo Failed", "Communication error with Cowo microservice.");
                    logBean.addOneNotificationFromString(errorMessage);

                    runButtonDisabled = false; // Enable button on failure
                    runButtonText = sessionBean.getLocaleBundle().getString("general.verbs.compute");
                    PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");

                    return null;
                });
    }

    public void generateInputDataAndDataId() {
        dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
        LOG.log(Level.INFO, "Generating input data for dataId: {0}", dataPersistenceUniqueId);
        try {
            Path tempFolderForAllTasks = applicationProperties.getTempFolderFullPath();
            Path tempFolderForThisTask = Path.of(tempFolderForAllTasks.toString(), dataPersistenceUniqueId);
            Files.createDirectories(tempFolderForThisTask);

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

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                sb.append(entry.getValue().trim()).append("\n");
            }
            Path fullPathToInputFile = functionProps.getOriginalTextInputFilePath(dataPersistenceUniqueId);
            Files.writeString(fullPathToInputFile, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error generating input data file", ex);
            logBean.addOneNotificationFromString("Error preparing input data: " + ex.getMessage());
            dataPersistenceUniqueId = null;
        }
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

    public StreamedContent getFileToSave() {
        if (dataPersistenceUniqueId == null || dataPersistenceUniqueId.isEmpty()) {
            LOG.warning("Cannot provide GEXF file for download, dataPersistenceUniqueId is null or empty.");
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "Analysis ID not set. Cannot download.");
            return new DefaultStreamedContent();
        }
        try {
            Path gexfFilePath = functionProps.getGexfFilePath(dataPersistenceUniqueId);
            if (Files.exists(gexfFilePath)) {
                String gexfAsString = Files.readString(gexfFilePath, StandardCharsets.UTF_8);
                return GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "results_cowo");
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
        try {
            Path pathOfTopNodesData = globals.getTopNetworkVivaGraphFormattedFilePath(dataPersistenceUniqueId);
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
            Path pathOfTopNodesData = globals.getTopNetworkVivaGraphFormattedFilePath(dataPersistenceUniqueId);
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

    public String getRunButtonText() {
        return runButtonText;
    }

    public void setRunButtonText(String runButtonText) {
        this.runButtonText = runButtonText;
    }

    public int getMaxNGram() {
        try {
            // These are defensive measures based on input file size
            if (dataPersistenceUniqueId == null || dataPersistenceUniqueId.isEmpty()) {
                return 3; // Default if data ID is not set
            }
            Path fullPathForFileContainingTextInput = functionProps.getOriginalTextInputFilePath(dataPersistenceUniqueId);
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
            LOG.log(Level.SEVERE, "Error determining file size for maxNGram", ex);
        }
        return maxNGram;
    }

    public void gotoVV() {
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexfWithGet(microserviceClient, dataPersistenceUniqueId, shareVVPublicly, applicationProperties);
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
        String urlToGephiLite = ExportToGephiLite.exportAndReturnLink(shareGephiLitePublicly, dataPersistenceUniqueId, applicationProperties);
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

    public List<String> getSelectedLanguages() {
        return selectedLanguages;
    }

    public void setSelectedLanguages(List<String> selectedLanguages) {
        this.selectedLanguages = selectedLanguages;
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

    public Boolean getShareGephiLitePublicly() {
        return shareGephiLitePublicly;
    }

    public void setShareGephiLitePublicly(Boolean shareGephiLitePublicly) {
        this.shareGephiLitePublicly = shareGephiLitePublicly;
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

    public boolean isFirstNames() {
        return firstNames;
    }

    public void setFirstNames(boolean firstNames) {
        this.firstNames = firstNames;
    }

    public String getDataPersistenceUniqueId() {
        return dataPersistenceUniqueId;
    }

    public void setDataPersistenceUniqueId(String dataPersistenceUniqueId) {
        this.dataPersistenceUniqueId = dataPersistenceUniqueId;
    }
}
