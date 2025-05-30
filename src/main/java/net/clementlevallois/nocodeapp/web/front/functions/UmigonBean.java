package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Asynchronous; // NEW IMPORT for @Asynchronous
import jakarta.ejb.Stateless;    // Often @Asynchronous requires a stateless bean, but with CDI 4.0 it might work with @SessionScoped. If not, consider a helper Stateless bean.
import jakarta.enterprise.concurrent.ManagedExecutorService;
                                 // Let's try directly within @SessionScoped first as modern containers are more flexible.

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
import net.clementlevallois.functions.model.FunctionUmigon;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
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
public class UmigonBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(UmigonBean.class.getName());

    private Integer progress = 3;
    private Integer countTreated = 0;
    private List<Document> results;
    private ConcurrentHashMap<Integer, Document> tempResults;
    private String[] sentiments;
    private String selectedLanguage;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private List<Document> filteredDocuments;
    private Integer maxCapacity = 10_000;

    private String dataPersistenceUniqueId = "";

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    ImportSimpleLinesBean simpleLinesImportBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;
    
    public UmigonBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction(FunctionUmigon.NAME);
        String positive_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_positive");
        String negative_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_negative");
        String neutral_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_neutral");
        sentiments = new String[]{positive_tone, negative_tone, neutral_tone};
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

    public void onComplete() {
    }

    public void cancel() {
        progress = null;
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Analysis Cancelled", "Attempting to cancel analysis.");
    }

    public String runAnalysis() {
        // Initial UI state updates on the JSF request thread
        progress = 3;
        countTreated = 0;
        runButtonDisabled = true;

        if (selectedLanguage == null || selectedLanguage.isEmpty()) {
            selectedLanguage = "en";
        }
        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        
        PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");
        startAnalysisAsync();

        return null;
    }

    @Asynchronous
    public void startAnalysisAsync() {
        Map<Integer, String> mapOfLines = new HashMap();
        try {
            if (simpleLinesImportBean.getDataPersistenceUniqueId() != null) {
                dataPersistenceUniqueId = simpleLinesImportBean.getDataPersistenceUniqueId();
                Path tempDataPath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId);
                if (Files.exists(tempDataPath) && !Files.isDirectory(tempDataPath)) {
                    List<String> readAllLines = Files.readAllLines(tempDataPath, StandardCharsets.UTF_8);
                    int i = 0;
                    for (String line : readAllLines) {
                        mapOfLines.put(i++, line.trim());
                    }
                } else {
                    LOG.log(Level.WARNING, "Temp data file not found for dataId: {0}", dataPersistenceUniqueId);
                    sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Temporary data file not found. Please re-import data.");
                    PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
                    return; // Exit the async task
                }
            } else {
                DataFormatConverter dataFormatConverter = new DataFormatConverter();
                mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());
            }

            if (mapOfLines == null || mapOfLines.isEmpty()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "No data found for analysis.");
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications");
                return; // Exit the async task
            }

            int maxRecords = Math.min(mapOfLines.size(), maxCapacity);
            tempResults = new ConcurrentHashMap(maxRecords);
            filteredDocuments = new ArrayList();
            results = new ArrayList();

            Set<CompletableFuture<Void>> futures = new HashSet<>();

            String owner = applicationProperties.getPrivateProperties().getProperty("pwdOwner");
            if (owner == null) {
                LOG.severe("pwdOwner property is not set!");
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Configuration Error", "Owner password not configured.");
                return; // Exit the async task
            }

            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                if (countTreated >= maxCapacity) {
                    break;
                }
                String text = entry.getValue();
                if (text == null || text.isBlank()) {
                    countTreated++;
                    continue;
                }
                String id = String.valueOf(entry.getKey());

                CompletableFuture<Void> future = microserviceClient.api().post(FunctionUmigon.ENDPOINT)
                        .withByteArrayPayload(text.getBytes(StandardCharsets.UTF_8))
                        .addQueryParameter("text-lang", selectedLanguage)
                        .addQueryParameter("id", id)
                        .addQueryParameter("explanation", "on")
                        .addQueryParameter("shorter", "true")
                        .addQueryParameter("owner", owner)
                        .addQueryParameter("output-format", "bytes")
                        .addQueryParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                        .sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                        .thenAccept(resp -> {
                            // This block runs on HttpClient's thread pool
                            int currentProgress = (int) ((float) tempResults.size() * 100 / maxRecords);
                            if (currentProgress > progress) {
                                progress = currentProgress;
                            }

                            if (resp.statusCode() == 200) {
                                byte[] body = resp.body();
                                try (ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                                    Document docReturn = (Document) ois.readObject();
                                    tempResults.put(Integer.valueOf(docReturn.getId()), docReturn);
                                    LOG.log(Level.FINE, "Processed document with ID: {0}", docReturn.getId());
                                } catch (IOException | ClassNotFoundException ex) {
                                    LOG.log(Level.SEVERE, "Error deserializing Document object for ID " + id, ex);
                                    Document errorDoc = new Document();
                                    errorDoc.setId(id);
                                    errorDoc.setText(text);
                                    errorDoc.setExplanationPlainText("Error processing result: " + ex.getMessage());
                                    tempResults.put(Integer.valueOf(id), errorDoc);
                                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Could not process result for ID " + id);
                                }
                            } else {
                                String errorBody = new String(resp.body(), StandardCharsets.UTF_8);
                                LOG.log(Level.SEVERE, "Umigon microservice call failed for ID {0}. Status: {1}, Body: {2}", new Object[]{id, resp.statusCode(), errorBody});
                                Document errorDoc = new Document();
                                errorDoc.setId(id);
                                errorDoc.setText(text);
                                errorDoc.setExplanationPlainText("Microservice error: Status " + resp.statusCode() + ", " + errorBody);
                                tempResults.put(Integer.valueOf(id), errorDoc);
                                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Microservice error for ID " + id);
                            }
                            countTreated++;
                        })
                        .exceptionally(exception -> {
                            LOG.log(Level.SEVERE, "Exception during async Umigon call for ID " + id, exception);
                            String errorMessage = "Communication error: " + exception.getMessage();
                            if (exception.getCause() instanceof MicroserviceCallException msce) {
                                errorMessage = "Communication error: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
                            }
                            Document errorDoc = new Document();
                            errorDoc.setId(id);
                            errorDoc.setText(text);
                            errorDoc.setExplanationPlainText(errorMessage);
                            tempResults.put(Integer.valueOf(id), errorDoc);
                            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", errorMessage);

                            countTreated++;
                            return null;
                        });
                futures.add(future);

                try {
                    Thread.sleep(8);
                } catch (InterruptedException ex) {
                    LOG.log(Level.WARNING, "Thread interrupted during request sending sleep", ex);
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            progress = 40;
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            combinedFuture.join(); // This blocking call is now on the @Asynchronous thread

            for (Map.Entry<Integer, Document> entry : tempResults.entrySet()) {
                results.add(entry.getKey(), entry.getValue());
            }

            progress = 100;
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            runButtonDisabled = false;

            PrimeFaces.current().executeScript("window.location.href = '" + FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/" + sessionBean.getFunction() + "/results.html?faces-redirect=true';");

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error during data loading or file operations in UmigonBean", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Failed to prepare data for analysis: " + ex.getMessage());
            handleAnalysisFailure();
        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            LOG.log(Level.SEVERE, "Exception during completion of async Umigon calls", cause);
            String errorMessage = "Analysis failed: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                errorMessage = "Analysis failed: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", errorMessage);
            logBean.addOneNotificationFromString(errorMessage);
            handleAnalysisFailure();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unexpected error in Umigon analysis", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", "An unexpected error occurred: " + ex.getMessage());
            logBean.addOneNotificationFromString("An unexpected error occurred: " + ex.getMessage());
            handleAnalysisFailure();
        }
    }

    // Helper method to reset UI state on analysis failure
    private void handleAnalysisFailure() {
        progress = 0;
        runButtonDisabled = false;
        PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");
    }

    public List<Document> getResults() {
        return results;
    }

    public void setResults(List<Document> results) {
        this.results = results;
    }

    public String[] getSentiments() {
        return sentiments;
    }

    public void setSentiments(String[] sentiments) {
        this.sentiments = sentiments;
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
    }

    public StreamedContent getFileToSave() {
        if (results == null || results.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "No results available to export.");
            return new DefaultStreamedContent();
        }
        try {
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);

            String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag();

            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/umigon")
                    .addQueryParameter("lang", lang)
                    .withByteArrayPayload(documentsAsByteArray)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                return DefaultStreamedContent.builder()
                        .name("results_umigon.xlsx")
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