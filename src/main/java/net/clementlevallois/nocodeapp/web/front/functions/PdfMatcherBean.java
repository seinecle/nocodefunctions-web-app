package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.clementlevallois.functions.model.FunctionPdfMatcher;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;

import org.primefaces.PrimeFaces;

@Named
@SessionScoped
public class PdfMatcherBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(PdfMatcherBean.class.getName());

    private Integer progress;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private String typeOfContext = "surroundingWords";
    private int nbWords = 5;
    private int nbLines = 2;
    private Boolean caseSensitive = false;
    private String searchedTerm;
    ConcurrentHashMap<String, List<Occurrence>> results = new ConcurrentHashMap();
    List<Match> resultsForDisplay = new ArrayList();
    private String sessionId;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    public PdfMatcherBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunctionName(FunctionPdfMatcher.NAME);
        results = new ConcurrentHashMap();
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);

    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String goToPdfUpload() {
        if (searchedTerm == null || searchedTerm.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", sessionBean.getLocaleBundle().getString("pdfmatcher.tool.error.empty_term"));
            return "";
        }
        searchedTerm = searchedTerm.replaceAll("\\R", "");
        long countDoubleQuotes = searchedTerm.codePoints().filter(ch -> ch == '"').count();
        long countOpeningBracket = searchedTerm.codePoints().filter(ch -> ch == '(').count();
        long countClosingBracket = searchedTerm.codePoints().filter(ch -> ch == ')').count();
        if ((countDoubleQuotes % 2) != 0) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", sessionBean.getLocaleBundle().getString("pdfmatcher.tool.error.quotes"));
            return "";
        }
        if (countOpeningBracket != countClosingBracket) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", sessionBean.getLocaleBundle().getString("pdfmatcher.tool.error.parentheses"));
            return "";
        }
        return "/import/import_your_data_bulk_text.xhtml?function=pdfmatcher&amp;faces-redirect=true";
    }

    public void cancel() {
        progress = null;
        // TODO: Implement actual cancellation logic if microservice supports it
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Analysis Cancelled", "Analysis cancelled.");
    }

      // --- Refactored runAnalysis method ---
    public String runAnalysis() {
        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        progress = 0;
        runButtonDisabled = true;
        results = new ConcurrentHashMap();
        resultsForDisplay = new ArrayList();

        List<SheetModel> dataInSheets = inputData.getDataInSheets();
        if (dataInSheets == null || dataInSheets.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
            runButtonDisabled = false;
            return null;
        }

        Set<CompletableFuture<Void>> futures = new HashSet();

        for (SheetModel oneDoc : dataInSheets) {
            Map<Integer, String> lines = new HashMap();
            int i = 0;
            List<CellRecord> cellRecords = oneDoc.getColumnIndexToCellRecords().get(0); // Assuming single column input
            if (cellRecords == null) {
                LOG.log(Level.WARNING, "No cell records found for document: {0}", oneDoc.getName());
                continue; // Skip this document
            }
            for (CellRecord cr : cellRecords) {
                if (cr.getRawValue() == null || cr.getRawValue().isBlank()) {
                    continue;
                }
                lines.put(i++, cr.getRawValue());
            }

            // Prepare JSON builders for 'lines' and 'pages' sub-objects
            JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
            for (Map.Entry<Integer, String> entryLines : lines.entrySet()) {
                if (entryLines.getKey() == null || entryLines.getValue() == null) {
                    continue;
                }
                linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
            }

            JsonObjectBuilder pagesBuilder = Json.createObjectBuilder();
            Map<Integer, Integer> pageAndStartingLine = oneDoc.getPageAndStartingLine();
            if (pageAndStartingLine != null) {
                for (Map.Entry<Integer, Integer> entryPages : pageAndStartingLine.entrySet()) {
                    pagesBuilder.add(String.valueOf(entryPages.getKey()), entryPages.getValue());
                }
            }

            // Refactored: Create JSON payload using FunctionPdfMatcher.BodyParams enum for exhaustiveness
            JsonObjectBuilder jsonPayloadBuilder = Json.createObjectBuilder();

            for (FunctionPdfMatcher.BodyParams param : FunctionPdfMatcher.BodyParams.values()) {
                Consumer<JsonObjectBuilder> jsonSetter = switch (param) {
                    case LINES -> builder -> builder.add(param.name(), linesBuilder);
                    case PAGES -> builder -> builder.add(param.name(), pagesBuilder);
                };
                jsonSetter.accept(jsonPayloadBuilder);
            }
            JsonObject jsonPayload = jsonPayloadBuilder.build();

            // Send async call for each document
            var requestBuilder = microserviceClient.api().post(FunctionPdfMatcher.ENDPOINT)
                    .withJsonPayload(jsonPayload);

            // Refactored: Add query parameters using FunctionPdfMatcher.QueryParams enum for exhaustiveness
            for (FunctionPdfMatcher.QueryParams param : FunctionPdfMatcher.QueryParams.values()) {
                String value = switch (param) {
                    case START_OF_PAGE -> sessionBean.getLocaleBundle().getString("pdfmatcher.tool.start_of_page");
                    case END_OF_PAGE -> sessionBean.getLocaleBundle().getString("pdfmatcher.tool.end_of_page");
                    case TYPE_OF_CONTEXT -> typeOfContext;
                    case CASE_SENSITIVE -> String.valueOf(caseSensitive);
                    case SEARCHED_TERM -> searchedTerm;
                    case FILE_NAME -> oneDoc.getName();
                    case NB_WORDS -> String.valueOf(nbWords);
                    case NB_LINES -> String.valueOf(nbLines);
                };
                requestBuilder.addQueryParameter(param.name(), value);
            }

            CompletableFuture<Void> future = requestBuilder
                    .sendAsync(HttpResponse.BodyHandlers.ofByteArray()) // Expecting byte array (serialized List<Occurrence>)
                    .thenAccept(resp -> {
                        if (resp.statusCode() == 200) {
                            byte[] body = resp.body();
                            try (ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                                List<Occurrence> occurrencesFound = (List<Occurrence>) ois.readObject();
                                results.put(oneDoc.getName(), occurrencesFound); // Store results by filename
                                LOG.log(Level.INFO, "Processed document {0}, found {1} occurrences.", new Object[]{oneDoc.getName(), occurrencesFound.size()});
                            } catch (IOException | ClassNotFoundException ex) {
                                LOG.log(Level.SEVERE, "Error deserializing Occurrence list for document " + oneDoc.getName(), ex);
                                results.put(oneDoc.getName(), Collections.emptyList()); // Store empty list on error
                                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Could not process results for " + oneDoc.getName() + ": " + ex.getMessage());
                            }
                        } else {
                            String errorBody = new String(resp.body(), StandardCharsets.UTF_8);
                            LOG.log(Level.SEVERE, "PdfMatcher microservice call failed for document {0}. Status: {1}, Body: {2}", new Object[]{oneDoc.getName(), resp.statusCode(), errorBody});
                            results.put(oneDoc.getName(), Collections.emptyList()); // Store empty list on error
                            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Microservice error for " + oneDoc.getName() + ": Status " + resp.statusCode() + ", " + errorBody);
                        }
                        updateProgress();
                    })
                    .exceptionally(exception -> {
                        LOG.log(Level.SEVERE, "Exception during async PdfMatcher call for document " + oneDoc.getName(), exception);
                        String errorMessage = "Communication error for " + oneDoc.getName() + ": " + exception.getMessage();
                        if (exception.getCause() instanceof MicroserviceCallException msce) {
                            errorMessage = "Communication error for " + oneDoc.getName() + ": Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
                        }
                        results.put(oneDoc.getName(), Collections.emptyList()); // Store empty list on error
                        sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", errorMessage);
                        updateProgress();
                        return null;
                    });
            futures.add(future);
        }

        this.progress = 1;
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));
        PrimeFaces.current().ajax().update("progressComponentId");

        try {
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            combinedFuture.join();
            LOG.info("All PdfMatcher microservice calls completed.");

            // Process results for display after all async calls are done
            resultsForDisplay = new ArrayList<>();
            for (Map.Entry<String, List<Occurrence>> entry : results.entrySet()) {
                List<Occurrence> occurrences = entry.getValue();
                Match match = new Match();
                match.setFileName(entry.getKey());
                StringBuilder sb = new StringBuilder();
                if (occurrences != null && !occurrences.isEmpty()) {
                    for (Occurrence occ : occurrences) {
                        sb.append("page ").append(occ.getPage()).append(": ");
                        sb.append("<br/>");
                        sb.append(occ.getContext());
                        sb.append("<br/>");
                    }
                } else {
                    sb.append("No matches found or error processing document.");
                }
                match.setListOfOccurrences(sb.toString());
                resultsForDisplay.add(match);
            }

            this.progress = 100;
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            runButtonDisabled = false;

            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");

            return "/" + sessionBean.getFunctionName() + "/results.xhtml?faces-redirect=true";

        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            LOG.log(Level.SEVERE, "Exception during completion of async PdfMatcher calls", cause);
            String errorMessage = "Analysis failed: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                errorMessage = "Analysis failed: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", errorMessage);
            logBean.addOneNotificationFromString(errorMessage);

            this.progress = 0;
            runButtonDisabled = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");

            return null;
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Unexpected error after sending PdfMatcher calls", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Failed", "An unexpected error occurred: " + ex.getMessage());
            logBean.addOneNotificationFromString("An unexpected error occurred: " + ex.getMessage());

            this.progress = 0;
            runButtonDisabled = false;
            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId");

            return null;
        }
    }

    private synchronized void updateProgress() {
        int totalDocs = inputData.getDataInSheets().size(); // Assuming dataInSheets size is the total number of documents/calls
        if (totalDocs > 0) {
            int currentProgress = (int) ((float) results.size() * 100 / totalDocs);
            if (currentProgress > progress) {
                progress = currentProgress;
                // Trigger UI update for progress
                PrimeFaces.current().ajax().update("progressComponentId"); // Replace with actual ID
            }
        }
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
        if (results == null || results.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "No results available to export.");
            return new DefaultStreamedContent();
        }
        try {
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);

            // Use MicroserviceHttpClient to call the export service
            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/pdfmatches")
                    .withByteArrayPayload(documentsAsByteArray) // Send the byte array payload
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray()); // Execute and get body as byte[]

            // Block to get the result for StreamedContent
            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                return DefaultStreamedContent.builder()
                        .name("results_pdfmatcher.xlsx")
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
            if (cause instanceof MicroserviceCallException) {
                MicroserviceCallException msce = (MicroserviceCallException) cause;
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

    public int getNbWords() {
        return nbWords;
    }

    public void setNbWords(int nbWords) {
        this.nbWords = nbWords;
    }

    public String getSearchedTerm() {
        return searchedTerm;
    }

    public void setSearchedTerm(String searchedTerm) {
        this.searchedTerm = searchedTerm;
    }

    public ConcurrentHashMap<String, List<Occurrence>> getResults() {
        return results;
    }

    public void setResults(ConcurrentHashMap<String, List<Occurrence>> results) {
        this.results = results;
    }

    public static class Match {

        public Match() {
        }

        String fileName;
        String listOfOccurrences;

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getListOfOccurrences() {
            return listOfOccurrences;
        }

        public void setListOfOccurrences(String listOfOccurrences) {
            this.listOfOccurrences = listOfOccurrences;
        }
    }

    public List<Match> getResultsForDisplay() {
        return resultsForDisplay;
    }

    public void setResultsForDisplay(List<Match> resultsForDisplay) {
        this.resultsForDisplay = resultsForDisplay;
    }

    public Boolean getCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public String getTypeOfContext() {
        return typeOfContext;
    }

    public void setTypeOfContext(String typeOfContext) {
        this.typeOfContext = typeOfContext;
    }

    public int getNbLines() {
        return nbLines;
    }

    public void setNbLines(int nbLines) {
        this.nbLines = nbLines;
    }
}
