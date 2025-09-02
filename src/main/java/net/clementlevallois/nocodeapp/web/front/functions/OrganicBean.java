package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import net.clementlevallois.functions.model.FunctionOrganic;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.umigon.model.classification.Document;
import org.primefaces.PrimeFaces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;



@Named
@SessionScoped
public class OrganicBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(OrganicBean.class.getName());

    private Integer progress = 3;
    private Integer countTreated = 0;
    private List<Document> results;
    private ConcurrentHashMap<Integer, Document> tempResults;
    private String[] tones;
    private String selectedLanguage;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private List<Document> filteredDocuments;
    private Integer maxCapacity = 10_000;

    private String dataPersistenceUniqueId = "";

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    ImportSimpleLinesBean simpleLinesImportBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;


    public OrganicBean() {
    }

    @PostConstruct
    public void init() {
        String promoted_tone = sessionBean.getLocaleBundle().getString("organic.general.soundspromoted");
        String neutral_tone = sessionBean.getLocaleBundle().getString("organic.general.soundsorganic");
        tones = new String[]{promoted_tone, neutral_tone};
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public void cancel() {
        progress = null;
        // TODO: Implement actual cancellation logic if the microservice supports it
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Analysis Cancelled", "Attempting to cancel analysis.");
    }

    public String runAnalysis() {
        progress = 3;
        countTreated = 0;
        runButtonDisabled = true;
        renderSeeResultsButton = false;

        if (selectedLanguage == null || selectedLanguage.isEmpty()) {
            selectedLanguage = "en";
        }
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        Map<Integer, String> mapOfLines = new HashMap();
        if (simpleLinesImportBean.getJobId() != null) {
            dataPersistenceUniqueId = simpleLinesImportBean.getJobId();
            Path tempDataPath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId);
            if (Files.exists(tempDataPath) && !Files.isDirectory(tempDataPath)) {
                try {
                    List<String> readAllLines = Files.readAllLines(tempDataPath, StandardCharsets.UTF_8);
                    int i = 0;
                    for (String line : readAllLines) {
                        mapOfLines.put(i++, line.trim());
                    }
                    // Files.delete(tempDataPath); // Consider if you want to delete the temp file immediately
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error reading temp data file", ex);
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Could not read temporary data file: " + ex.getMessage());
                    runButtonDisabled = false;
                    return null;
                }
            } else {
                LOG.log(Level.WARNING, "Temp data file not found for dataId: {0}", dataPersistenceUniqueId);
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Temporary data file not found. Please re-import data.");
                runButtonDisabled = false;
                return null;
            }
        } else {
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
        }

        if (mapOfLines == null || mapOfLines.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "No data found for analysis.");
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
            runButtonDisabled = false;
            return null;
        }

        int maxRecords = Math.min(mapOfLines.size(), maxCapacity);
        tempResults = new ConcurrentHashMap(maxRecords + 1);
        filteredDocuments = new ArrayList(maxRecords + 1);
        results = Arrays.asList(new Document[maxRecords + 1]); // Initialize results list with nulls (size for pre-allocation)


        Set<CompletableFuture<Void>> futures = new HashSet<>();
        int currentRecordIndex = 0;

        String owner = applicationProperties.getPrivateProperties().getProperty("pwdOwner");
        if (owner == null) {
            LOG.severe("pwdOwner property is not set!");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Configuration Error", "Owner password not configured.");
            runButtonDisabled = false;
            return null;
        }

        for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
            if (currentRecordIndex++ >= maxCapacity) {
                break;
            }
            Document doc = new Document(); // Create a Document object to pass ID and text consistently
            String id = String.valueOf(entry.getKey());
            doc.setText(entry.getValue());
            doc.setId(id);

            var requestBuilder = microserviceClient.api().get(FunctionOrganic.ENDPOINT);
            for (FunctionOrganic.QueryParams param : FunctionOrganic.QueryParams.values()) {
                String value = switch (param) {
                    case TEXT_LANG -> selectedLanguage;
                    case ID -> doc.getId();
                    case TEXT -> doc.getText();
                    case EXPLANATION -> "on";
                    case SHORTER -> "true";
                    case OWNER -> owner;
                    case OUTPUT_FORMAT -> "bytes";
                    case EXPLANATION_LANG -> sessionBean.getCurrentLocale().toLanguageTag();
                };
                requestBuilder.addQueryParameter(param.name(), value);
            }

            // Launch async call for each document
            CompletableFuture<Void> future = requestBuilder
                .sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
                    // Note: Lambdas capture 'doc' (which is the loop variable, effectively final in each iteration)
                    // and 'maxRecords' (effectively final). 'progress' needs to be mutable via a trick.
                    // For progress update, it's better to make 'progress' volatile or use AtomicInteger
                    // if multiple threads can update it concurrently and you need visibility guaranteed.
                    // For UI updates (PrimeFaces), make sure you're on the correct thread (JSF's RequestContext or similar).
                    // This example keeps the original progress update logic, assuming it's handled by PrimeFaces.

                    int currentProgress = (int) ((float) tempResults.size() * 100 / maxRecords);
                    if (currentProgress > progress) {
                        progress = currentProgress; // Reassigning `progress` from lambda needs it to be non-final.
                                                    // In a real concurrent scenario, use AtomicInteger for progress:
                                                    // `final AtomicInteger progressAtomic = new AtomicInteger(3);`
                                                    // then `progressAtomic.set(currentProgress);`
                                                    // and access `progressAtomic.get()`.
                        PrimeFaces.current().ajax().update("progressComponentId");
                    }

                    if (resp.statusCode() == 200) {
                        byte[] body = resp.body();
                        try (ByteArrayInputStream bis = new ByteArrayInputStream(body);
                             ObjectInputStream ois = new ObjectInputStream(bis)) {
                            Document docReturn = (Document) ois.readObject();
                            tempResults.put(Integer.valueOf(docReturn.getId()), docReturn);
                            LOG.log(Level.FINE, "Processed document with ID: {0}", docReturn.getId());
                        } catch (IOException | ClassNotFoundException ex) {
                            LOG.log(Level.SEVERE, "Error deserializing Document object for ID " + doc.getId(), ex);
                            Document errorDoc = new Document();
                            errorDoc.setId(doc.getId());
                            errorDoc.setText(doc.getText());
                            errorDoc.setExplanationPlainText("Error processing result: " + ex.getMessage());
                            tempResults.put(Integer.valueOf(doc.getId()), errorDoc);
                        }
                    } else {
                        String errorBody = new String(resp.body(), StandardCharsets.UTF_8);
                        LOG.log(Level.SEVERE, "Organic microservice call failed for ID {0}. Status: {1}, Body: {2}", new Object[]{doc.getId(), resp.statusCode(), errorBody});
                        Document errorDoc = new Document();
                        errorDoc.setId(doc.getId());
                        errorDoc.setText(doc.getText());
                        errorDoc.setExplanationHtml("Microservice error: Status " + resp.statusCode() + ", " + errorBody);
                        tempResults.put(Integer.valueOf(doc.getId()), errorDoc);
                    }
                })
                .exceptionally(exception -> {
                    LOG.log(Level.SEVERE, "Exception during async Organic call for ID " + doc.getId(), exception);
                    String errorMessage = "Communication error: " + exception.getMessage();
                    if (exception.getCause() instanceof MicroserviceCallException msce) {
                        errorMessage = "Communication error: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
                    }
                    Document errorDoc = new Document();
                    errorDoc.setId(doc.getId());
                    errorDoc.setText(doc.getText());
                    errorDoc.setExplanationHtml(errorMessage);
                    tempResults.put(Integer.valueOf(doc.getId()), errorDoc);
                    return null;
                });
            futures.add(future);

            try {
                Thread.sleep(2); // Small delay to prevent overwhelming the server/client
            } catch (InterruptedException ex) {
                LOG.log(Level.WARNING, "Thread interrupted during request sending sleep", ex);
                Thread.currentThread().interrupt(); // Restore interrupt flag
                break;
            }
        }

        this.progress = 40; // Update progress after sending requests
        PrimeFaces.current().ajax().update("progressComponentId");
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

        try {
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            combinedFuture.join();
            LOG.info("All Organic microservice calls completed.");

            // Transfer results from tempResults to the final results list in order
            for (int j = 0; j < maxRecords; j++) {
                results.set(j, tempResults.get(j)); // Assumes IDs (keys) match indices. Adjust if not.
            }

            this.progress = 100;
            PrimeFaces.current().ajax().update("progressComponentId");

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            renderSeeResultsButton = true;
            runButtonDisabled = false;

            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "resultsButtonPanel");

            return "/" + "/results.xhtml?faces-redirect=true";

        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            LOG.log(Level.SEVERE, "Exception during completion of async Organic calls", cause);
            String errorMessage = "Analysis failed: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                errorMessage = "Analysis failed: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", errorMessage);
            logBean.addOneNotificationFromString(errorMessage);

            this.progress = 0;
            runButtonDisabled = false;
            renderSeeResultsButton = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "resultsButtonPanel", "progressComponentId");

            return null;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unexpected error after sending Organic calls", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", "An unexpected error occurred: " + ex.getMessage());
            logBean.addOneNotificationFromString("An unexpected error occurred: " + ex.getMessage());

            this.progress = 0;
            runButtonDisabled = false;
            renderSeeResultsButton = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "resultsButtonPanel", "progressComponentId");

            return null;
        }
    }


    public Boolean getRenderSeeResultsButton() {
        return renderSeeResultsButton;
    }

    public void setRenderSeeResultsButton(Boolean renderSeeResultsButton) {
        this.renderSeeResultsButton = renderSeeResultsButton;
    }

    public List<Document> getResults() {
        return results;
    }

    public void setResults(List<Document> results) {
        this.results = results;
    }

    public String[] getTones() {
        return tones;
    }

    public void setTones(String[] tones) {
        this.tones = tones;
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

    public String signal(int rowId) {
        Document docFound = null;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
        } else if (results != null && rowId < results.size()) {
            docFound = results.get(rowId);
        }

        if (docFound == null) {
            LOG.log(Level.WARNING, "Signalled document not found in backing bean collections for rowId: {0}", rowId);
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Could not find document to signal.");
            return "";
        }

        docFound.setFlaggedAsFalseLabel(true);
        // Assuming SendReport uses applicationProperties internally or is thread-safe
        // SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        // sender.initErrorReport(docFound.getText() + " - should not be " + docFound.getCategorizationResult().toString());
        // sender.start();
        LOG.log(Level.INFO, "Document ID {0} signalled as potentially misclassified.", docFound.getId());
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Feedback Sent", "Thank you for your feedback!");

        return "";
    }

    public String showExplanation(int rowId) {
         Document docFound = null;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
        } else if (results != null && rowId < results.size()) {
            docFound = results.get(rowId);
        }

        if (docFound == null) {
             LOG.log(Level.WARNING, "Document to explain not found in backing bean collections for rowId: {0}", rowId);
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Could not find document to show explanation.");
            return "";
        }
        docFound.setShowExplanation(true);
        return "";
    }

    public String hideExplanation(int rowId) {
         Document docFound = null;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
        } else if (results != null && rowId < results.size()) {
            docFound = results.get(rowId);
        }

        if (docFound == null) {
             LOG.log(Level.WARNING, "Document explanation to hide not found in backing bean collections for rowId: {0}", rowId);
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "Could not find document to hide explanation.");
            return "";
        }
        docFound.setShowExplanation(false);
        return "";
    }

    public void dummy() {
        // Used by PrimeFaces for AJAX updates without explicit action
    }

    public StreamedContent getFileToSave() {
        if (results == null || results.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "No results available to export.");
            return new DefaultStreamedContent();
        }
        try {
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);

            String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag();

            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/organic")
                 .addQueryParameter("lang", lang)
                 .withByteArrayPayload(documentsAsByteArray) // Send the byte array payload
                 .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray()); // Execute and get body as byte[]

            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                return DefaultStreamedContent.builder()
                        .name("results_organic.xlsx") // Use a specific name
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            } catch (IOException e) {
                 LOG.log(Level.SEVERE, "Error creating StreamedContent from export response body", e);
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Download Error", "Could not prepare download file.");
                 return new DefaultStreamedContent();
            }

        } catch (CompletionException cex) {
             Throwable cause = cex.getCause();
             LOG.log(Level.SEVERE, "Error during asynchronous export service call (CompletionException)", cause);
             String errorMessage = "Error exporting data: " + cause.getMessage();
              if (cause instanceof MicroserviceCallException msce) {
                  errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
              }
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", errorMessage);
             return new DefaultStreamedContent();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error serializing results before export", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "Error preparing data for export: " + ex.getMessage());
            return new DefaultStreamedContent();
        } catch (Exception ex) {
             LOG.log(Level.SEVERE, "Unexpected error in getFileToSave", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "An unexpected error occurred: " + ex.getMessage());
             return new DefaultStreamedContent();
        }
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public List<Document> getFilteredDocuments() {
        return filteredDocuments;
    }

    public void setFilteredDocuments(List<Document> filteredDocuments) {
        this.filteredDocuments = filteredDocuments;
    }
}
