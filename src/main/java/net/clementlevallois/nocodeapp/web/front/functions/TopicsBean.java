package net.clementlevallois.nocodeapp.web.front.functions;

// --- Keep necessary imports ---
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy; // Add for cleanup
import jakarta.annotation.Resource; // Add for ManagedExecutorService
import jakarta.enterprise.concurrent.ManagedExecutorService; // Add
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException; // Add
import java.util.concurrent.TimeUnit; // Add for potential timeouts
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.servlet.annotation.MultipartConfig;
import net.clementlevallois.nocodeapp.web.front.async.AnalysisCompletionService; // Import
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import io.mikael.urlbuilder.UrlBuilder; // Keep if used
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleComparator;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;


@Named
@SessionScoped
@MultipartConfig // Keep if file uploads are directly handled here
public class TopicsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(TopicsBean.class.getName());
    private static final long serialVersionUID = 1L; // Good practice for Serializable

    // --- Injected Beans ---
    @Inject
    BackToFrontMessengerBean logBean;
    @Inject
    DataImportBean inputData; // Still injected to READ data initially
    @Inject
    ImportSimpleLinesBean simpleLinesImportBean; // Still injected to READ data initially
    @Inject
    SessionBean sessionBean;
    @Inject
    ApplicationPropertiesBean applicationProperties;
    @Inject
    AnalysisCompletionService completionService; // Inject completion service

    @Resource // Inject container-managed executor service
    private ManagedExecutorService executorService;

    // --- Config & Parameters ---
    private Properties privateProperties;
    private String selectedLanguage;
    private int precision = 50;
    private int progress = 0;
    private int minCharNumber = 4;
    private int minTermFreq = 2;
    private boolean scientificCorpus;
    private boolean replaceStopwords = false;
    private boolean lemmatize = true;
    private boolean removeNonAsciiCharacters = false;
    private UploadedFile fileUserStopwords;

    // --- State Variables ---
    private transient HttpClient httpClient; // Use transient for non-serializable fields if needed, or initialize in PostConstruct
    private String dataPersistenceUniqueId; // ID for the current analysis run
    private volatile boolean resultsReady = false; // Flag for UI polling - must be volatile
    private volatile boolean analysisRunning = false; // Flag to disable button
    private volatile String statusMessage = ""; // To provide feedback
    private transient CompletableFuture<?> analysisFuture; // Holds the running analysis future for potential cancellation
    private volatile String completionError = null; // Stores error message from async task

    // --- Result Holders ---
    private transient Map<Integer, Multiset<String>> keywordsPerTopic;
    private transient Map<Integer, Multiset<Integer>> topicsPerLine;
    private transient StreamedContent excelFileToSave;
    private transient StreamedContent gexfFile;

    @PostConstruct
    public void init() {
        sessionBean.setFunction("topics");
        privateProperties = applicationProperties.getPrivateProperties();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        statusMessage = sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    // --- Main Action Method ---
    public void runAnalysis() {
        if (analysisRunning) {
            logBean.addOneNotificationFromString("Analysis is already running.");
            return;
        }
        resultsReady = false;
        analysisRunning = true;
        completionError = null; // Reset error state
        statusMessage = sessionBean.getLocaleBundle().getString("general.message.wait_long_operation");
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        sessionBean.sendFunctionPageReport();

        this.dataPersistenceUniqueId = UUID.randomUUID().toString();
        LOG.log(Level.INFO, "[{0}] Starting analysis run.", dataPersistenceUniqueId);

        CompletableFuture<String> resultPathFuture = completionService.registerFuture(dataPersistenceUniqueId);

        // **** VITAL CHANGE ****
        // Read data from Session Scoped beans HERE, before starting async chain
        String simpleLinesId = null;
        Path simpleLinesPath = null;
        boolean bulkData = false;
        List<SheetModel> sheetsData = null;
        String sheetName = null;
        String columnIndex = "";
        boolean hasHeaders = false;

        try {
            // Safely access session-scoped beans
            if (simpleLinesImportBean != null) {
                simpleLinesId = simpleLinesImportBean.getDataPersistenceUniqueId();
                simpleLinesPath = simpleLinesImportBean.getPathOfTempData();
            }
            if (inputData != null) {
                bulkData = inputData.getBulkData();
                sheetsData = (inputData.getDataInSheets() != null) ? new ArrayList(inputData.getDataInSheets()) : new ArrayList<>(); // Defensive copy
                sheetName = inputData.getSelectedSheetName();
                columnIndex = inputData.getSelectedColumnIndex();
                hasHeaders = inputData.getHasHeaders();

                // Clear beans immediately AFTER reading to free memory, if appropriate
                inputData.setDataInSheets(new ArrayList<>());
                inputData.setBulkData(null);
            } else {
                 // Handle case where inputData bean might not be initialized?
                 sheetsData = new ArrayList<>();
                 LOG.log(Level.WARNING, "[{0}] DataImportBean was null during runAnalysis setup.", dataPersistenceUniqueId);
            }

        } catch (NumberFormatException e) {
            // Handle potential errors during initial data access
            LOG.log(Level.SEVERE, "[{0}] Error accessing session-scoped beans during setup", dataPersistenceUniqueId);
            handleSyncSetupError("Error reading initial data", e);
            return; // Stop execution
        }
        // **** END VITAL CHANGE ****


        // Start async chain, passing the read data as parameters
        this.analysisFuture = prepareInputDataAsync(dataPersistenceUniqueId, simpleLinesId, simpleLinesPath, bulkData, sheetsData, sheetName, columnIndex, hasHeaders)
                .thenComposeAsync(inputFilePath -> sendInitiationRequestAsync(dataPersistenceUniqueId, inputFilePath), executorService)
                .thenComposeAsync(initiationResponse -> {
                    if (initiationResponse.statusCode() >= 300) {
                        LOG.log(Level.SEVERE, "[{0}] Initiation request failed with code {1}: {2}", new Object[]{dataPersistenceUniqueId, initiationResponse.statusCode(), initiationResponse.body()});
                        throw new CompletionException(new IOException("Microservice initiation failed with code " + initiationResponse.statusCode() + ": " + initiationResponse.body()));
                    }
                    LOG.log(Level.INFO, "[{0}] Initiation successful, waiting for result callback...", dataPersistenceUniqueId);
                    return resultPathFuture.orTimeout(15, TimeUnit.MINUTES);
                }, executorService)
                .thenApplyAsync(this::readAndParseResultFile, executorService) // Pass method reference
                .thenComposeAsync(parsedJson -> {
                    LOG.log(Level.INFO, "[{0}] Result file parsed. Processing and exporting...", dataPersistenceUniqueId);
                    progress = 50;
                    processParsedResults(parsedJson);
                    return callExportServiceAsync(parsedJson);
                }, executorService)
                .whenCompleteAsync((excelBytes, exception) -> {
                    // This block ONLY sets state flags and stores results/errors
                    if (exception != null) {
                        Throwable cause = (exception instanceof CompletionException && exception.getCause() != null) ? exception.getCause() : exception;
                        LOG.log(Level.SEVERE, "[{0}] Analysis workflow failed: {1}", new Object[]{dataPersistenceUniqueId, cause.getMessage()});
                        this.completionError = "Analysis workflow failed: " + cause.getMessage();
                    } else {
                        progress = 80;
                        LOG.log(Level.INFO, "[{0}] Analysis workflow completed successfully. Storing results.", dataPersistenceUniqueId);
                        this.excelFileToSave = createExcelStream(excelBytes);
                        this.completionError = null;
                    }
                    this.analysisRunning = false;
                    this.resultsReady = true;
                    progress = 100;
                }, executorService);

        // Catch synchronous exceptions during chain setup
        if (this.analysisFuture.isCompletedExceptionally()) {
             try {
                  this.analysisFuture.getNow(null); // Trigger exception propagation immediately if already failed
             } catch (CompletionException e) {
                  handleSyncSetupError("Analysis setup failed", e);
             } catch (Exception e) { // Catch other potential exceptions during setup
                  handleSyncSetupError("Unexpected analysis setup error", e);
             }
        }
    }

    // --- NEW Method called by Poll Listener ---
    public void handleCompletion() {
        if (!this.resultsReady) {
            return;
        }
        LOG.log(Level.INFO, "[{0}] handleCompletion invoked by poll listener.", dataPersistenceUniqueId);

        if (this.completionError != null) {
            LOG.log(Level.WARNING, "[{0}] Handling completion error state.", dataPersistenceUniqueId);
            // Safely access session beans
            try {
                logBean.addOneNotificationFromString(this.completionError);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", this.completionError);
                this.statusMessage = sessionBean.getLocaleBundle().getString("general.nouns.error");
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[{0}] Error accessing session beans during error handling in handleCompletion", dataPersistenceUniqueId);
                this.statusMessage = "An error occurred (Session context issue?).";
            }
        } else {
            LOG.log(Level.INFO, "[{0}] Handling completion success state.", dataPersistenceUniqueId);
            try {
                this.statusMessage = sessionBean.getLocaleBundle().getString("general.message.analysis_complete");
                logBean.addOneNotificationFromString(this.statusMessage);

                FacesContext context = FacesContext.getCurrentInstance();
                if (context != null && !context.getResponseComplete()) {
                    LOG.log(Level.INFO, "[{0}] Navigating to results page.", dataPersistenceUniqueId);
                    context.getApplication().getNavigationHandler().handleNavigation(context, null, "/topics/results.xhtml?faces-redirect=true");
                } else {
                    LOG.log(Level.SEVERE, "[{0}] FacesContext was null or response complete during handleCompletion, cannot navigate.", dataPersistenceUniqueId);
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[{0}] Error accessing session beans during success handling in handleCompletion", dataPersistenceUniqueId);
                 this.statusMessage = "Analysis complete but failed to update status/navigate (Session context issue?).";
            }
        }
    }

    // --- Helper Methods for Workflow Steps ---

    // **** VITAL CHANGE ****
    // Method signature changed to accept data as parameters
    // Removed direct access to simpleLinesImportBean and inputData
    private CompletableFuture<Path> prepareInputDataAsync(String uniqueId, String simpleLinesId, Path simpleLinesPath, boolean bulkData, List<SheetModel> sheetsData, String sheetName, String columnIndex, boolean hasHeaders) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
                Path fullPathForInput = Path.of(tempFolderRelativePath.toString(), uniqueId + "_input"); // Unique input name

                // **** VITAL CHANGE ****
                // Use passed parameters instead of injected beans
                if (simpleLinesId != null && simpleLinesPath != null) {
                    LOG.log(Level.INFO,"[{0}] Using pre-existing input data from path: {1}", new Object[]{uniqueId, simpleLinesPath});
                     if (!Files.exists(simpleLinesPath)) {
                          LOG.log(Level.SEVERE, "[{0}] Provided simpleLinesPath does not exist: {1}", new Object[]{uniqueId, simpleLinesPath});
                          throw new IOException("Provided simpleLinesPath does not exist: " + simpleLinesPath);
                     }
                     // Security check: Ensure path is within expected bounds if necessary
                     // Path validation logic could go here
                     return simpleLinesPath;
                } else {
                    LOG.log(Level.INFO,"[{0}] Processing new input data.", uniqueId);
                    DataFormatConverter dataFormatConverter = new DataFormatConverter();
                    // **** VITAL CHANGE ****
                    // Use passed parameters
                    Map<Integer, String> mapOfLines = dataFormatConverter.convertToMapOfLines(bulkData, sheetsData, sheetName, columnIndex, hasHeaders);
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                        if (entry.getValue() != null) {
                             sb.append(entry.getValue().trim()).append("\n");
                        }
                    }
                    Files.writeString(fullPathForInput, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    return fullPathForInput;
                }
            } catch (IOException | NullPointerException e) { // Catch potential NPE if parameters are unexpectedly null
                LOG.log(Level.SEVERE, "[{0}] Error preparing input data", uniqueId);
                throw new CompletionException("Failed to prepare input data file for " + uniqueId, e);
            }
        }, executorService);
    }


    private CompletableFuture<HttpResponse<String>> sendInitiationRequestAsync(String uniqueId, Path inputFilePath /* pass other params */) {
        try {
            String language = (selectedLanguage != null) ? selectedLanguage : "en";
            String callbackURL = RemoteLocal.getDomain() + "/internalapi/messageFromAPI/topics";
            // **** VITAL CHANGE ****
            // Get Session ID safely here if needed by the API, before going async
            // Or better, ensure the API doesn't strictly need it if the callback identifies the task via uniqueId
            String currentSessionId = sessionBean != null ? sessionBean.getSessionId() : "unknown-session"; // Get it safely

            JsonObjectBuilder overallObject = Json.createObjectBuilder()
                    .add("lang", language)
                    .add("replaceStopwords", replaceStopwords)
                    .add("isScientificCorpus", scientificCorpus)
                    .add("lemmatize", lemmatize)
                    .add("removeAccents", removeNonAsciiCharacters)
                    .add("precision", precision)
                    .add("minCharNumber", minCharNumber)
                    .add("minTermFreq", minTermFreq)
                    .add("sessionId", currentSessionId) // Pass the retrieved ID
                    .add("dataPersistenceId", uniqueId)
                    .add("callbackURL", callbackURL)
                    .add("inputFilePath", inputFilePath.toString());

             JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
             // Reading the uploaded file content needs care - it might be gone by the time this runs async
             // Best practice: Read the file content *before* starting the async chain if needed here,
             // or pass the UploadedFile object carefully (might not be thread-safe or serializable).
             // Simplest for now: Assume reading it here is acceptable, but be aware of potential issues.
             if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
                 try (InputStream is = fileUserStopwords.getInputStream();
                      BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                     List<String> userSuppliedStopwords = br.lines().toList();
                     int index = 0;
                     for (String stopword : userSuppliedStopwords) {
                         userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                     }
                 } catch (Exception e) { // Catch broader exception as getInputStream might fail later
                     LOG.log(Level.WARNING, "[{0}] Failed to read user supplied stopwords: {1}", new Object[]{uniqueId, e.getMessage()});
                 }
             }
             overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);

            String jsonPayload = buildJsonString(overallObject.build());
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8);

            URI uri = UrlBuilder.empty()
                .withScheme("http")
                .withHost("localhost")
                .withPort(Integer.parseInt(privateProperties.getProperty("nocode_api_port")))
                .withPath("api/topics")
                .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .build();

            LOG.log(Level.INFO, "[{0}] Sending POST to {1}", new Object[]{uniqueId, uri});
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[{0}] Error building/sending initiation request", uniqueId);
            return CompletableFuture.failedFuture(new CompletionException("Failed to build or send initiation request for " + uniqueId, e));
        }
    }

    private JsonObject readAndParseResultFile(String resultPath) {
         try {
            // Security consideration: Validate the resultPath - ensure it points to an expected location
            // to prevent path traversal attacks if the path comes directly from the callback.
            Path path = Path.of(resultPath);
             if (!Files.exists(path) /* || !isPathValid(path) */ ) { // Add path validation if needed
                  throw new IOException("Result file not found or invalid at: " + resultPath);
             }
            String jsonResultAsString = Files.readString(path, StandardCharsets.UTF_8);
            try (JsonReader jsonReader = Json.createReader(new StringReader(jsonResultAsString))) {
                return jsonReader.readObject();
            }
        } catch (IOException | jakarta.json.JsonException e) { // Catch JSON parsing exceptions too
             LOG.log(Level.SEVERE, "[{0}] Failed to read or parse result file: {1}", new Object[]{dataPersistenceUniqueId, resultPath});
            throw new CompletionException("Failed to read or parse result file: " + resultPath, e);
        }
    }

     private void processParsedResults(JsonObject jsonObject) {
         try {
             this.keywordsPerTopic = new TreeMap<>();
             JsonObject keywordsPerTopicAsJson = jsonObject.getJsonObject("keywordsPerTopic");
             for (String keyCommunity : keywordsPerTopicAsJson.keySet()) {
                 JsonObject termsAndFrequenciesForThisCommunity = keywordsPerTopicAsJson.getJsonObject(keyCommunity);
                 Multiset<String> termsAndFreqs = new Multiset<>();
                 for(String term : termsAndFrequenciesForThisCommunity.keySet()){
                      termsAndFreqs.addSeveral(term, termsAndFrequenciesForThisCommunity.getInt(term));
                 }
                 keywordsPerTopic.put(Integer.valueOf(keyCommunity), termsAndFreqs);
             }

             this.topicsPerLine = new TreeMap<>();
             JsonObject topicsPerLineAsJson = jsonObject.getJsonObject("topicsPerLine");
             for (String lineNumber : topicsPerLineAsJson.keySet()) {
                 JsonObject topicsAndTheirCountsForOneLine = topicsPerLineAsJson.getJsonObject(lineNumber);
                  Multiset<Integer> topicsAndFreqs = new Multiset<>();
                  for(String topic : topicsAndTheirCountsForOneLine.keySet()){
                      topicsAndFreqs.addSeveral(Integer.valueOf(topic), topicsAndTheirCountsForOneLine.getInt(topic));
                  }
                 topicsPerLine.put(Integer.valueOf(lineNumber), topicsAndFreqs);
             }

             String gexfSemanticNetwork = jsonObject.getString("gexf", null);
             if (gexfSemanticNetwork != null) {
                 this.gexfFile = GEXFSaver.exportGexfAsStreamedFile(gexfSemanticNetwork, "semantic_network_" + dataPersistenceUniqueId);
             } else {
                  this.gexfFile = null;
             }
         } catch (Exception e) { // Catch broader exception for unexpected JSON issues
             LOG.log(Level.SEVERE, "[{0}] Failed during processing of parsed JSON results.", dataPersistenceUniqueId);
             throw new CompletionException("Failed during processing of parsed JSON results.", e);
         }
     }

    private CompletableFuture<byte[]> callExportServiceAsync(JsonObject jsonResultForExport) {
        try {
             String jsonToExport = buildJsonString(jsonResultForExport);
             HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonToExport, StandardCharsets.UTF_8);

            URI uri = UrlBuilder.empty()
                .withScheme("http")
                .withHost("localhost")
                .withPort(Integer.parseInt(privateProperties.getProperty("nocode_import_port")))
                .withPath("api/export/xlsx/topics")
                .addParameter("nbTerms", "10")
                .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .header("Content-Type", "application/json")
                 .timeout(Duration.ofMinutes(2))
                .build();

            LOG.log(Level.INFO, "[{0}] Calling export service at {1}", new Object[]{dataPersistenceUniqueId, uri});
             return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                      .thenApplyAsync(resp -> {
                          if (resp.statusCode() >= 300) {
                               LOG.log(Level.SEVERE, "[{0}] Export service failed with code {1}", new Object[]{dataPersistenceUniqueId, resp.statusCode()});
                               // Consider reading response body for error details if possible
                               throw new CompletionException(new IOException("Export service failed with status code: " + resp.statusCode()));
                          }
                          return resp.body();
                      }, executorService);

        } catch (Exception e) {
             LOG.log(Level.SEVERE, "[{0}] Error building/sending export request", dataPersistenceUniqueId);
            return CompletableFuture.failedFuture(new CompletionException("Failed to build or send export request for " + dataPersistenceUniqueId, e));
        }
    }

    private StreamedContent createExcelStream(byte[] excelBytes) {
         if (excelBytes == null || excelBytes.length == 0) {
             return null;
         }
        InputStream is = new ByteArrayInputStream(excelBytes);
        return DefaultStreamedContent.builder()
            .name("results_topics_" + dataPersistenceUniqueId + ".xlsx")
            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .stream(() -> is)
            .contentLength((long)excelBytes.length)
            .build();
    }

    private String buildJsonString(JsonObject jsonObject) {
        StringWriter sw = new StringWriter(128);
        try (JsonWriter jw = Json.createWriter(sw)) {
            jw.write(jsonObject);
        }
        return sw.toString();
    }

    // --- Added handler for synchronous setup errors ---
    private void handleSyncSetupError(String message, Throwable throwable) {
         Throwable cause = (throwable instanceof CompletionException && throwable.getCause() != null) ? throwable.getCause() : throwable;
         LOG.log(Level.SEVERE, "[{0}] " + message, new Object[]{dataPersistenceUniqueId});
         LOG.log(Level.SEVERE, "[{0}] Exception: {1}", new Object[]{dataPersistenceUniqueId, cause.getMessage()});

         this.completionError = message + ": " + cause.getMessage();
         this.resultsReady = true; // Mark as 'ready' to stop polling, even though it failed
         this.analysisRunning = false;
         // We might need to manually update status message here as handleCompletion won't run yet
         try {
             if (sessionBean != null) {
                 this.statusMessage = sessionBean.getLocaleBundle().getString("general.message.an_error_occured");
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", this.completionError);
             } else {
                  this.statusMessage = "An error occurred during setup.";
             }
             if (logBean != null) logBean.addOneNotificationFromString(this.completionError);
         } catch (Exception e) {
              LOG.log(Level.SEVERE, "[{0}] Error accessing session beans during sync error handling", dataPersistenceUniqueId);
              this.statusMessage = "An error occurred during setup (session context issue?).";
         }

         // Ensure future is cleaned up if it was registered
         if(dataPersistenceUniqueId != null){
             completionService.cancelAndCleanupFuture(dataPersistenceUniqueId);
         }
    }


    // --- Cleanup ---
    @PreDestroy
    public void destroy() {
        LOG.log(Level.INFO, "Session bean @PreDestroy called for workflow ID: {0}", dataPersistenceUniqueId);
        if (dataPersistenceUniqueId != null) {
             completionService.cancelAndCleanupFuture(dataPersistenceUniqueId);
        }
        if (analysisFuture != null && !analysisFuture.isDone()) {
            LOG.log(Level.INFO, "Attempting to cancel running analysis future: {0}", dataPersistenceUniqueId);
            analysisFuture.cancel(true);
        }
    }

    // --- Getters and Setters (Keep necessary ones) ---
    public boolean isResultsReady() { return resultsReady; }
    public boolean isAnalysisRunning() { return analysisRunning; }
    public String getStatusMessage() { return statusMessage; }
    public Map<Integer, Multiset<String>> getKeywordsPerTopic() { return keywordsPerTopic; }
    public StreamedContent getExcelFileToSave() { return excelFileToSave; }
    public StreamedContent getGexfFile() { return gexfFile; }
    public String getSelectedLanguage() { return selectedLanguage; }
    public void setSelectedLanguage(String selectedLanguage) { this.selectedLanguage = selectedLanguage; }
    public int getPrecision() { return precision; }
    public void setPrecision(int precision) { this.precision = precision; }
    public int getMinCharNumber() { return minCharNumber; }
    public void setMinCharNumber(int minCharNumber) { this.minCharNumber = minCharNumber; }
    public int getMinTermFreq() { return minTermFreq; }
    public void setMinTermFreq(int minTermFreq) { this.minTermFreq = minTermFreq; }
    public boolean isScientificCorpus() { return scientificCorpus; }
    public void setScientificCorpus(boolean scientificCorpus) { this.scientificCorpus = scientificCorpus; }
    public boolean isReplaceStopwords() { return replaceStopwords; }
    public void setReplaceStopwords(boolean replaceStopwords) { this.replaceStopwords = replaceStopwords; }
    public boolean isLemmatize() { return lemmatize; }
    public void setLemmatize(boolean lemmatize) { this.lemmatize = lemmatize; }
    public boolean isRemoveNonAsciiCharacters() { return removeNonAsciiCharacters; }
    public void setRemoveNonAsciiCharacters(boolean removeNonAsciiCharacters) { this.removeNonAsciiCharacters = removeNonAsciiCharacters; }
    public UploadedFile getFileUserStopwords() { return fileUserStopwords; }
    public void setFileUserStopwords(UploadedFile fileUserStopwords) { this.fileUserStopwords = fileUserStopwords; }
    // Getter needed for view logic
    public String getCompletionError() { return completionError; }

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


    // Add navigation method if not present
    public String navigateToResults() {
        if (resultsReady && completionError == null) { // Only navigate on success
            return "/topics/results.xhtml?faces-redirect=true";
        }
        return null; // Stay on page otherwise
    }
}