/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import java.io.InputStream;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.utils.TextCleaningOps;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.omnifaces.util.Faces;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class PdfMatcherBean implements Serializable {

    private Integer progress;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String sessionId;
    private int nbContext = 5;
    private String searchedTerm;
    Map<String, List<Occurrence>> results = new HashMap();
    List<Match> resultsForDisplay = new ArrayList();

    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    public PdfMatcherBean() {
    }

    @PostConstruct
    void init() {
        sessionId = Faces.getSessionId();
        sessionBean.setFunction("pdfmatcher");
        results = new HashMap();
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public void onComplete() {
    }

    public void cancel() {
        progress = null;
    }

    public String runAnalysis() throws URISyntaxException {
        sessionBean.sendFunctionPageReport();
        service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        List<SheetModel> dataInSheets = inputData.getDataInSheets();
        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        results = new HashMap();

        for (SheetModel oneDoc : dataInSheets) {

            Map<Integer, String> lines = new HashMap();
            int i = 0;
            for (SheetModel sm : dataInSheets) {
                List<CellRecord> cellRecords = sm.getColumnIndexToCellRecords().get(0);
                for (CellRecord cr : cellRecords) {
                    lines.put(i++, cr.getRawValue());
                }
            }

            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
            for (Map.Entry<Integer, String> entryLines : lines.entrySet()) {
                linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
            }

            JsonObjectBuilder pagesBuilder = Json.createObjectBuilder();
            Map<Integer, Integer> pageAndStartingLine = oneDoc.getPageAndStartingLine();
            for (Map.Entry<Integer, Integer> entryPages : pageAndStartingLine.entrySet()) {
                pagesBuilder.add(String.valueOf(entryPages.getKey()), entryPages.getValue());
            }

            overallObject.add("lines", linesBuilder);
            overallObject.add("pages", pagesBuilder);
            overallObject.add("nbContext", nbContext);
            overallObject.add("searchedTerm", searchedTerm);

            JsonObject build = overallObject.build();
            StringWriter sw = new StringWriter(128);
            try (JsonWriter jw = Json.createWriter(sw)) {
                jw.write(build);
            }
            String jsonString = sw.toString();

            BodyPublisher bodyPublisher = BodyPublishers.ofString(jsonString);

            URI uri = new URI("http://localhost:7002/api/pdfmatcher/");

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    List<Occurrence> occurrencesFound = (List<Occurrence>) ois.readObject();
                    results.put(oneDoc.getName(), occurrencesFound);
                } catch (IOException | ClassNotFoundException ex) {
                    Logger.getLogger(PdfMatcherBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            );
            futures.add(future);
        }

        this.progress = 40;
        service.create(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();

        searchedTerm = TextCleaningOps.doAllCleaningOps(searchedTerm);
        searchedTerm = searchedTerm.toLowerCase();

        for (Map.Entry<String, List<Occurrence>> entry : results.entrySet()) {
            System.out.println("doc: " + entry.getKey());
            List<Occurrence> occurrences = entry.getValue();
            Match match = new Match();
            match.setFileName(entry.getKey());
            StringBuilder sb = new StringBuilder();
            for (Occurrence occ : occurrences) {
                sb.append("page ").append(occ.getPage()).append(": ");
                String formattedTerm = "<span style=\"color: #FF0000;\">" + searchedTerm + "</span>";
                String context = occ.getContext().replace(searchedTerm, formattedTerm);
                sb.append(context);
                sb.append("<br/>");
            }
            match.setListOfOccurrences(sb.toString());
            resultsForDisplay.add(match);
        }

        this.progress = 100;

        service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
        renderSeeResultsButton = true;
        runButtonDisabled = true;

        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
    }

    public Boolean getRenderSeeResultsButton() {
        return renderSeeResultsButton;
    }

    public void setRenderSeeResultsButton(Boolean renderSeeResultsButton) {
        this.renderSeeResultsButton = renderSeeResultsButton;
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
        try {
            if (results == null || results.isEmpty()) {
                return null;
            }
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(documentsAsByteArray);

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/export/xlsx/pdfmatches")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                InputStream is = new ByteArrayInputStream(body);
                fileToSave = DefaultStreamedContent.builder()
                        .name("results.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            }
            );
            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();
        } catch (IOException ex) {
            Logger.getLogger(PdfMatcherBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return fileToSave;
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public int getNbContext() {
        return nbContext;
    }

    public void setNbContext(int nbContext) {
        this.nbContext = nbContext;
    }

    public String getSearchedTerm() {
        return searchedTerm;
    }

    public void setSearchedTerm(String searchedTerm) {
        this.searchedTerm = searchedTerm;
    }

    public Map<String, List<Occurrence>> getResults() {
        return results;
    }

    public void setResults(Map<String, List<Occurrence>> results) {
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

}
