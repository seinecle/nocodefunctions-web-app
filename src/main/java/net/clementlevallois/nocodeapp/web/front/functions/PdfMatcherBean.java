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
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
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
    private String typeOfContext = "surroundingWords";
    private int nbWords = 5;
    private int nbLines = 2;
    private Boolean caseSensitive = false;
    private String searchedTerm;
    ConcurrentHashMap<String, List<Occurrence>> results = new ConcurrentHashMap();
    List<Match> resultsForDisplay = new ArrayList();
    private Properties privateProperties;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ApplicationPropertiesBean applicationProperties;
        
    public PdfMatcherBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("pdfmatcher");
        privateProperties = applicationProperties.getPrivateProperties();
        results = new ConcurrentHashMap();
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String goToPdfUpload() {
        searchedTerm = searchedTerm.replaceAll("\\R", "");
        long countDoubleQuotes = searchedTerm.codePoints().filter(ch -> ch == '"').count();
        long countOpeningBracket = searchedTerm.codePoints().filter(ch -> ch == '(').count();
        long countClosingBracket = searchedTerm.codePoints().filter(ch -> ch == ')').count();
        if ((countDoubleQuotes % 2) != 0) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("pdfmatcher.tool.error.quotes"));
            return "";
        }
        if (countOpeningBracket != countClosingBracket) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("pdfmatcher.tool.error.parentheses"));
            return "";
        }
        return "/import/import_your_data_bulk_text.xhtml?function=pdfmatcher&amp;faces-redirect=true";
    }

    public void cancel() {
        progress = null;
    }

    public String runAnalysis() throws URISyntaxException {
        String endOfPage = sessionBean.getLocaleBundle().getString("pdfmatcher.tool.end_of_page");
        String startOfPage = sessionBean.getLocaleBundle().getString("pdfmatcher.tool.start_of_page");

        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        List<SheetModel> dataInSheets = inputData.getDataInSheets();
        HttpRequest request;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
        Set<CompletableFuture> futures = new HashSet();
        results = new ConcurrentHashMap();
        BodyPublisher bodyPublisher;
        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))))
                .withHost("localhost")
                .withPath("api/pdfmatcher")
                .toUri();

        for (SheetModel oneDoc : dataInSheets) {

            Map<Integer, String> lines = new HashMap();
            int i = 0;
            List<CellRecord> cellRecords = oneDoc.getColumnIndexToCellRecords().get(0);
            for (CellRecord cr : cellRecords) {
                if (cr.getRawValue() == null || cr.getRawValue().isBlank()) {
                    continue;
                }
                lines.put(i++, cr.getRawValue());
            }

            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
            for (Map.Entry<Integer, String> entryLines : lines.entrySet()) {
                if (entryLines.getKey() == null || entryLines.getValue() == null) {
                    continue;
                }
                linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
            }

            JsonObjectBuilder pagesBuilder = Json.createObjectBuilder();
            Map<Integer, Integer> pageAndStartingLine = oneDoc.getPageAndStartingLine();
            for (Map.Entry<Integer, Integer> entryPages : pageAndStartingLine.entrySet()) {
                pagesBuilder.add(String.valueOf(entryPages.getKey()), entryPages.getValue());
            }

            overallObject.add("lines", linesBuilder);
            overallObject.add("pages", pagesBuilder);
            overallObject.add("startOfPage", startOfPage);
            overallObject.add("endOfPage", endOfPage);
            if (typeOfContext.equals("surroundingWords")) {
                overallObject.add("nbWords", nbWords);
            } else {
                overallObject.add("nbLines", nbLines);
            }
            overallObject.add("caseSensitive", caseSensitive);
            overallObject.add("searchedTerm", searchedTerm);

            JsonObject build = overallObject.build();
            StringWriter sw = new StringWriter(128);
            try (JsonWriter jw = Json.createWriter(sw)) {
                jw.write(build);
            }
            String jsonString = sw.toString();

            bodyPublisher = BodyPublishers.ofString(jsonString);

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
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();

        for (Map.Entry<String, List<Occurrence>> entry : results.entrySet()) {
            List<Occurrence> occurrences = entry.getValue();
            Match match = new Match();
            match.setFileName(entry.getKey());
            StringBuilder sb = new StringBuilder();
            for (Occurrence occ : occurrences) {
                sb.append("page ").append(occ.getPage()).append(": ");
                sb.append("<br/>");
                sb.append(occ.getContext());
                sb.append("<br/>");
            }
            match.setListOfOccurrences(sb.toString());
            resultsForDisplay.add(match);
        }

        this.progress = 100;

        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
        runButtonDisabled = true;

        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
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
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(documentsAsByteArray);

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/export/xlsx/pdfmatches")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            try {
                byte[] body = resp.body();
                try (InputStream is = new ByteArrayInputStream(body)) {
                    fileToSave = DefaultStreamedContent.builder()
                            .name("results.xlsx")
                            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .stream(() -> is)
                            .build();
                }
            } catch (IOException ex) {
                Logger.getLogger(PdfMatcherBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(PdfMatcherBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return fileToSave;
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
