/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
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
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ActiveLocale;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.umigon.model.Document;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class OrganicBean implements Serializable {

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

    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ActiveLocale activeLocale;

    public OrganicBean() {
    }

    @PostConstruct
    private void init() {
        sessionBean.setFunction("organic");
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

    public void onComplete() {
    }

    public void cancel() {
        progress = null;
    }

    public String runAnalysis() {
        progress = 3;
        if (selectedLanguage == null || selectedLanguage.isEmpty()) {
            selectedLanguage = "en";
        }
        sessionBean.sendFunctionPageReport();
        service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        DataFormatConverter dataFormatConverter = new DataFormatConverter();
        Map<Integer, String> mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());

        int maxRecords = Math.min(mapOfLines.size(), maxCapacity);
        tempResults = new ConcurrentHashMap(maxRecords + 1);
        filteredDocuments = new ArrayList(maxRecords + 1);
        results = Arrays.asList(new Document[maxRecords + 1]);

        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        int i = 1;
        try {
            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                if (i++ > maxCapacity) {
                    break;
                }
                Document doc = new Document();
                String id = String.valueOf(entry.getKey());
                doc.setText(entry.getValue());
                doc.setId(id);

                StringBuilder sb = new StringBuilder();
                sb.append("http://localhost:7002/api/organicForAText");
                sb.append("?text-lang=").append(selectedLanguage);
                sb.append("&id=").append(doc.getId());
                sb.append("&text=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));
                sb.append("&explanation=on");
                sb.append("&shorter=true");
                sb.append("&owner=").append(SingletonBean.getPrivateProperties().getProperty("pwdOwner"));
                sb.append("&output-format=bytes");
                sb.append("&explanation-lang=").append(activeLocale.getLanguageTag());
                String uriAsString = sb.toString();

                URI uri = new URI(uriAsString);

                request = HttpRequest.newBuilder()
                        .uri(uri)
                        .build();

                CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                    byte[] body = resp.body();
                    Float situation = (countTreated++ * 100 / (float) maxRecords);
                    if (situation.intValue() > progress) {
                        progress = situation.intValue();
                    }
                    if (body.length >= 100 && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("internal") && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("not found")) {
                        try (
                                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
                            Document docReturn = (Document) ois.readObject();
                            tempResults.put(Integer.valueOf(docReturn.getId()), docReturn);
                        } catch (Exception ex) {
                            System.out.println("error in body:");
                            System.out.println(new String(body, StandardCharsets.UTF_8));
                            Logger.getLogger(OrganicBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                }
                );
                futures.add(future);
                // this is because we need to slow down a bit the requests sending too many thros a
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
        } catch (InterruptedException ex) {
            System.out.println("ex:" + ex.getMessage());
        }

        /* this little danse between tempResults and results is because:
            -> we use tempResults which is a ConcurrentMap to handle the concurrent inserts following the parallel calls to the Umigon API
            -> we use results which is a List to regain the original order of texts (notice that we insert the docs in a precise order)
         */
        for (Map.Entry<Integer, Document> entry : tempResults.entrySet()) {
            results.set(entry.getKey(), entry.getValue());
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
        Document docFound;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
            if (docFound == null) {
                System.out.println("signalled doc not found in backing bean / filtered collection");
                return "";
            }
            docFound.setFlaggedAsFalseLabel(true);
        } else {
            docFound = results.get(rowId);
            if (docFound == null) {
                System.out.println("signalled doc not found in backing bean  / results collection");
                return "";
            }
            docFound.setFlaggedAsFalseLabel(true);
        }
        SendReport sender = new SendReport();
        sender.initErrorReport(docFound.getText() + " - should not be " + docFound.getCategorizationResult().toString());
        sender.start();
        return "";
    }

    public String showExplanation(int rowId) {
        Document docFound;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
            if (docFound == null) {
                System.out.println("doc to explain not found in backing bean / filtered collection");
                return "";
            }
            docFound.setShowExplanation(true);
        } else {
            docFound = results.get(rowId);
            if (docFound == null) {
                System.out.println("doc to explain not found in backing bean  / results collection");
                return "";
            }
            docFound.setShowExplanation(true);
        }
        return "";
    }

    public String hideExplanation(int rowId) {
        Document docFound;

        if (filteredDocuments != null && rowId < filteredDocuments.size() && filteredDocuments.size() != results.size()) {
            docFound = filteredDocuments.get(rowId);
            if (docFound == null) {
                System.out.println("doc explanation to hide not found in backing bean / filtered collection");
                return "";
            }
            docFound.setShowExplanation(false);
        } else {
            docFound = results.get(rowId);
            if (docFound == null) {
                System.out.println("doc explanation to hide not found in backing bean  / results collection");
                return "";
            }
            docFound.setShowExplanation(false);
        }
        return "";
    }

    public void dummy() {
    }

    public StreamedContent getFileToSave() {
        try {
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(documentsAsByteArray);

            String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/export/xlsx/organic")
                    .addParameter("lang", lang)
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
            Logger.getLogger(OrganicBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return fileToSave;
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
