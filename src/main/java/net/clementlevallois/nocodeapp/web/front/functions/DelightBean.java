/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.importdata.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.io.ExcelSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.umigon.model.Document;
import net.clementlevallois.utils.Clock;
import org.omnifaces.util.Faces;
import org.openide.util.Exceptions;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class DelightBean implements Serializable {

    private Integer progress;
    private List<Document> results;
    private String[] sentiments;
    private String selectedLanguage;
    private Boolean runButtonDisabled = true;
//    private List<String> sheetNames;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String sessionId;
    private List<Document> filteredDocuments;
    private ConcurrentHashMap<Integer, Document> tempResults = new ConcurrentHashMap();
    
    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    public DelightBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("delight");
        sessionBean.sendFunctionPageReport();
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

    @PostConstruct
    public void init() {
        sessionId = Faces.getSessionId();
        String delight =sessionBean.getLocaleBundle().getString("general.nouns.delight");
        String no_delight =sessionBean.getLocaleBundle().getString("general.nouns.no_delight");
        sentiments = new String[]{delight, no_delight};
    }

    public String runAnalysis() {
        try {
            if (selectedLanguage == null || selectedLanguage.isEmpty()) {
                selectedLanguage = "en";
            }
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            Map<Integer, String> mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());

            int maxRecords = mapOfLines.size();
            results = Arrays.asList(new Document[maxRecords]);

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            Clock clock = new Clock("concurrent");

            try {
                for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                    Document doc = new Document();
                    String id = String.valueOf(entry.getKey());
                    doc.setText(entry.getValue());
                    doc.setId(id);

                    URI uri = new URI("http://localhost:7002/api/sentimentForAText/bytes/" + selectedLanguage + "?id=" + doc.getId() + "&text=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));

                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .build();

                    CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                        byte[] body = resp.body();
                        try (
                                ByteArrayInputStream bis = new ByteArrayInputStream(body);
                                ObjectInputStream ois = new ObjectInputStream(bis)) {
                            Document docReturn = (Document) ois.readObject();
                            tempResults.put(Integer.valueOf(docReturn.getId()), docReturn);
                        } catch (IOException | ClassNotFoundException ex) {
                            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    );
                    futures.add(future);
                    // this is because we need to slow down a bit the requests to DeepL - sending too many thros a
                    // java.util.concurrent.CompletionException: java.io.IOException: too many concurrent streams
                    Thread.sleep(2);
                }
                this.progress = 40;
                service.create(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

                CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
                combinedFuture.join();

            } catch (URISyntaxException exception) {
                System.out.println("error!!");
            } catch (UnsupportedEncodingException ex) {
                System.out.println("encoding ex");
            }
            for (Map.Entry<Integer, Document> entry : tempResults.entrySet()) {
                results.set(entry.getKey(), entry.getValue());
            }
            clock.closeAndPrintClock();
            this.progress = 100;

            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            renderSeeResultsButton = true;
            runButtonDisabled = true;

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
        }
        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
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
        Document docFound;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
            if (docFound == null) {
                System.out.println("signaled doc not found in backing bean / filtered collection");
                return "";
            }
            docFound.setFlaggedAsFalseLabel(true);
        } else {
            docFound = results.get(rowId);
            if (docFound == null) {
                System.out.println("signaled doc not found in backing bean  / results collection");
                return "";
            }
            docFound.setFlaggedAsFalseLabel(true);
        }
        SendReport sender = new SendReport();
        sender.initErrorReport(docFound.getText() + " - should not be " + docFound.getCategorizationResult().toString());
        sender.start();
        return "";
    }

    public void dummy() {
    }

    public StreamedContent getFileToSave() {
        return ExcelSaver.exportUmigon(results, sessionBean.getLocaleBundle());
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
