package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URI;
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
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.importdata.ImportSimpleLinesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.umigon.model.classification.Document;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class UmigonBean implements Serializable {

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
    private Properties privateProperties;

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

    
    
    public UmigonBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("umigon");
        String positive_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_positive");
        String negative_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_negative");
        String neutral_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_neutral");
        sentiments = new String[]{positive_tone, negative_tone, neutral_tone};
        privateProperties = applicationProperties.getPrivateProperties();

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
        countTreated = 0;
        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        Map<Integer, String> mapOfLines;
        if (simpleLinesImportBean.getDataPersistenceUniqueId() != null) {
            mapOfLines = new HashMap();
            dataPersistenceUniqueId = simpleLinesImportBean.getDataPersistenceUniqueId();
            Path tempDataPath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId);
            if (Files.exists(tempDataPath) && !Files.isDirectory(tempDataPath)) {
                try {
                    List<String> readAllLines = Files.readAllLines(tempDataPath, StandardCharsets.UTF_8);
                    int i = 0;
                    for (String line : readAllLines) {
                        mapOfLines.put(i++, line.trim());
                    }
                    Files.delete(tempDataPath);
                } catch (IOException ex) {
                    Logger.getLogger(OrganicBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else {
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());
        }
        int maxRecords = Math.min(mapOfLines.size(), maxCapacity);
        tempResults = new ConcurrentHashMap(maxRecords + 1);
        filteredDocuments = new ArrayList(maxRecords + 1);
        results = Arrays.asList(new Document[maxRecords + 1]);

        HttpRequest request;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
        Set<CompletableFuture> futures = new HashSet();
        int i = 1;
        try {
            for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                if (i++ > maxCapacity) {
                    break;
                }
                if (entry.getValue() == null) {
                    continue;
                }
                String id = String.valueOf(entry.getKey());
                String text = entry.getValue();

                URI uri = UrlBuilder
                        .empty()
                        .withScheme("http")
                        .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                        .withHost("localhost")
                        .withPath("api/sentimentForAText")
                        .addParameter("text-lang", selectedLanguage)
                        .addParameter("id", id)
                        .addParameter("explanation", "on")
                        .addParameter("shorter", "true")
                        .addParameter("owner", privateProperties.getProperty("pwdOwner"))
                        .addParameter("output-format", "bytes")
                        .addParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                        .toUri();

                request = HttpRequest.newBuilder()
                        .POST(HttpRequest.BodyPublishers.ofString(text))
                        .uri(uri)
                        .build();

                CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                        .thenAccept(resp -> {
                            byte[] body = resp.body();
                            Float situation = (countTreated++ * 100 / (float) maxRecords);
                            if (situation.intValue() > progress) {
                                progress = situation.intValue();
                            }
                            if (resp.statusCode() == 200) {
                                if (body.length >= 100 && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("internal") && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("not found")) {
                                    try (
                                            ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                                        Document docReturn = (Document) ois.readObject();
                                        tempResults.put(Integer.valueOf(docReturn.getId()), docReturn);
                                    } catch (Exception ex) {
                                        System.out.println("error in body:");
                                        System.out.println(new String(body, StandardCharsets.UTF_8));
                                        Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }
                            }else{
                                System.out.println("umigon api did not return a 200 code");
                                System.out.println("body of response: " + new String(body, StandardCharsets.UTF_8));
                            }
                        }
                        ).exceptionally(exception -> {
                            System.err.println("exception: " + exception);
                            return null;
                        });
                futures.add(future);
                // this is because we need to slow down a bit the requests sending too many thros a
                // java.util.concurrent.CompletionException: java.io.IOException: too many concurrent streams
                Thread.sleep(8);
            }
            this.progress = 40;
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

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

        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
        runButtonDisabled = true;

        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
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
        SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
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
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(documentsAsByteArray);

            String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/export/xlsx/umigon")
                    .addParameter("lang", lang)
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            InputStream is = new ByteArrayInputStream(body);
            fileToSave = DefaultStreamedContent.builder()
                    .name("results.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
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
