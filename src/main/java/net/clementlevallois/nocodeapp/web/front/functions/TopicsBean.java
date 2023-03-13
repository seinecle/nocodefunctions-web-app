/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static java.util.stream.Collectors.toList;
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
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.TextCleaningOps;
import org.primefaces.event.SlideEndEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig

public class TopicsBean implements Serializable {

    private Integer progress = 0;
    private String jsonResultAsString;
    private Map<Integer, Multiset<String>> keywordsPerTopic;
    private Map<Integer, Multiset<Integer>> topicsPerLine;
    private Boolean runButtonDisabled = true;
    private StreamedContent excelFileToSave;
    private StreamedContent fileTopicsPerLineToSave;
    private StreamedContent gexfFile;
    private Boolean renderSeeResultsButton = false;
    private String selectedLanguage;
    private int precision = 50;
    private int minCharNumber = 4;
    private int minTermFreq = 2;

    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private UploadedFile fileUserStopwords;

    private Map<Integer, String> mapOfLines;

    @Inject
    NotificationService service;

    @Inject
    DataImportBean dataImportBean;

    @Inject
    SessionBean sessionBean;

    public TopicsBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("topics");
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

    public String runAnalysis() {
        progress = 0;
        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        try {
            sessionBean.sendFunctionPageReport();
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            mapOfLines = dataFormatConverter.convertToMapOfLines(dataImportBean.getBulkData(), dataImportBean.getDataInSheets(), dataImportBean.getSelectedSheetName(), dataImportBean.getSelectedColumnIndex(), dataImportBean.getHasHeaders());

            if (mapOfLines == null || mapOfLines.isEmpty()) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                return "";
            }

            if (selectedLanguage == null) {
                selectedLanguage = "en";
            }
            progress = 10;
            service.create(sessionBean.getLocaleBundle().getString("general.message.removing_punctuation_and_cleaning"));

            mapOfLines = TextCleaningOps.doAllCleaningOps(mapOfLines);
            mapOfLines = TextCleaningOps.putInLowerCase(mapOfLines);

            progress = 15;
            if (selectedLanguage.equals("en") | selectedLanguage.equals("fr")) {
                JsonObjectBuilder overallObject = Json.createObjectBuilder();
                JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
                for (Map.Entry<Integer, String> entryLines : mapOfLines.entrySet()) {
                    linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
                }
                overallObject.add("lines", linesBuilder);
                overallObject.add("lang", selectedLanguage);
                JsonObject build = overallObject.build();
                StringWriter sw = new StringWriter(128);
                try (JsonWriter jw = Json.createWriter(sw)) {
                    jw.write(build);
                }
                String jsonString = sw.toString();

                HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

                URI uri = new URI("http://localhost:7002/api/lemmatizer_light/");

                request = HttpRequest.newBuilder()
                        .POST(bodyPublisher)
                        .uri(uri)
                        .build();

                CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                    byte[] body = resp.body();
                    String jsonReceived = new String(body, StandardCharsets.UTF_8);
                    JsonObject jsonObjectReturned;
                    try (JsonReader reader = Json.createReader(new StringReader(jsonReceived))) {
                        jsonObjectReturned = reader.readObject();
                    }
                    for (String key : jsonObjectReturned.keySet()) {
                        mapOfLines.put(Integer.valueOf(key), jsonObjectReturned.getString(key));
                    }
                }
                );
                futures.add(future);
                CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
                combinedFuture.join();

            } else {
                String lemmatization = sessionBean.getLocaleBundle().getString("general.message.heavy_duty_lemmatization");
                String wait = sessionBean.getLocaleBundle().getString("general.message.please_wait_seconds");
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, lemmatization, wait));
                service.create(lemmatization + " " + wait);
                progress = 15;
                Runnable incrementProgress = () -> {
                    progress = progress + 1;
                };
                ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
                executor.scheduleAtFixedRate(incrementProgress, 0, 2, TimeUnit.SECONDS);
                try {
                    JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
                    for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                        objectBuilder.add(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    JsonObject jsonObject = objectBuilder.build();

                    URI uri = new URI("http://localhost:7000/lemmatize/" + selectedLanguage);

                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                            .build();
                    client = HttpClient.newHttpClient();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        String body = response.body();
                        JsonObject jsonObjectReturned;
                        try (JsonReader reader = Json.createReader(new StringReader(body))) {
                            jsonObjectReturned = reader.readObject();
                        }
                        for (String key : jsonObjectReturned.keySet()) {
                            mapOfLines.put(Integer.valueOf(key), jsonObjectReturned.getString(key));
                        }
                    }
                } catch (URISyntaxException | IOException | InterruptedException ex) {
                    System.out.println("ex:" + ex.getMessage());
                }
                executor.shutdown();
            }

            service.create(sessionBean.getLocaleBundle().getString("general.message.finding_key_terms"));
            progress = 20;

            client = HttpClient.newHttpClient();
            futures = new HashSet();
            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
            for (Map.Entry<Integer, String> entryLines : mapOfLines.entrySet()) {
                linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
            }

            JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
            if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
                List<String> userSuppliedStopwords;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                    userSuppliedStopwords = br.lines().collect(toList());
                }
                int index = 0;
                for (String stopword : userSuppliedStopwords) {
                    userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                }
            }

            for (Map.Entry<Integer, String> entryLines : mapOfLines.entrySet()) {
                linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
            }

            overallObject.add("lines", linesBuilder);
            overallObject.add("lang", selectedLanguage);
            overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);
            overallObject.add("replaceStopwords", replaceStopwords);
            overallObject.add("isScientificCorpus", scientificCorpus);
            overallObject.add("precision", precision);
            overallObject.add("minCharNumber", minCharNumber);
            overallObject.add("minTermFreq", minTermFreq);

            JsonObject build = overallObject.build();
            StringWriter sw = new StringWriter(128);
            try (JsonWriter jw = Json.createWriter(sw)) {
                jw.write(build);
            }
            String jsonString = sw.toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            URI uri = new URI("http://localhost:7002/api/topics/");

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                jsonResultAsString = new String(body, StandardCharsets.UTF_8);
            }
            );
            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

            if (jsonResultAsString == null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
                renderSeeResultsButton = true;
                runButtonDisabled = true;
                return "";
            }
            service.create(sessionBean.getLocaleBundle().getString("general.message.last_ops_creating_network"));
            progress = 60;

            keywordsPerTopic = new TreeMap();
            topicsPerLine = new TreeMap();
            JsonReader jsonReader = Json.createReader(new StringReader(jsonResultAsString));
            JsonObject jsonObject = jsonReader.readObject();

            
            String gexfSemanticNetwork = jsonObject.getString("gexf");
            gexfFile = GEXFSaver.exportGexfAsStreamedFile(gexfSemanticNetwork, "semantic_network");

            JsonObject keywordsPerTopicAsJson = jsonObject.getJsonObject("keywordsPerTopic");
            for (String keyCommunity : keywordsPerTopicAsJson.keySet()) {
                JsonObject termsAndFrequenciesForThisCommunity = keywordsPerTopicAsJson.getJsonObject(keyCommunity);
                Iterator<String> iteratorTerms = termsAndFrequenciesForThisCommunity.keySet().iterator();
                Multiset<String> termsAndFreqs = new Multiset();
                while (iteratorTerms.hasNext()) {
                    String nextTerm = iteratorTerms.next();
                    termsAndFreqs.addSeveral(nextTerm, termsAndFrequenciesForThisCommunity.getInt(nextTerm));
                }
                keywordsPerTopic.put(Integer.valueOf(keyCommunity), termsAndFreqs);
            }
            JsonObject topicsPerLineAsJson = jsonObject.getJsonObject("topicsPerLine");
            for (String lineNumber : topicsPerLineAsJson.keySet()) {
                JsonObject topicsAndTheirCountsForOneLine = topicsPerLineAsJson.getJsonObject(lineNumber);
                Iterator<String> iteratorTopics = topicsAndTheirCountsForOneLine.keySet().iterator();
                Multiset<Integer> topicsAndFreqs = new Multiset();
                while (iteratorTopics.hasNext()) {
                    String nextTopic = iteratorTopics.next();
                    topicsAndFreqs.addSeveral(Integer.valueOf(nextTopic), topicsAndTheirCountsForOneLine.getInt(nextTopic));
                }
                topicsPerLine.put(Integer.valueOf(lineNumber), topicsAndFreqs);
            }

            if (keywordsPerTopic.isEmpty()) {
                return null;
            }
            futures = new HashSet();
            byte[] topicsAsArray = Converters.byteArraySerializerForAnyObject(jsonResultAsString);

            HttpRequest.BodyPublisher bodyPublisherTopics = HttpRequest.BodyPublishers.ofByteArray(topicsAsArray);

            URI uriExportTopicsToExcel = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/export/xlsx/topics")
                    .addParameter("nbTerms", "10")
                    .toUri();

            HttpRequest requestExportTopicsToExcel = HttpRequest.newBuilder()
                    .POST(bodyPublisherTopics)
                    .uri(uriExportTopicsToExcel)
                    .build();

            CompletableFuture<Void> futureTopicsExport = client.sendAsync(requestExportTopicsToExcel, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                InputStream is = new ByteArrayInputStream(body);
                excelFileToSave = DefaultStreamedContent.builder()
                        .name("results_topics.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            }
            );
            futures.add(futureTopicsExport);

            combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

            progress = 100;

            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            renderSeeResultsButton = true;
            runButtonDisabled = true;

        } catch (IOException | NumberFormatException | URISyntaxException ex) {
            System.out.println("ex:" + ex.getMessage());
        }
        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
    }

    public Boolean getRenderSeeResultsButton() {
        return renderSeeResultsButton;
    }

    public void setRenderSeeResultsButton(Boolean renderSeeResultsButton) {
        this.renderSeeResultsButton = renderSeeResultsButton;
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

    public void dummy() {
    }

    public StreamedContent getExcelFileToSave() {
        return excelFileToSave;
    }

    public void setExcelFileToSave(StreamedContent excelFileToSave) {
        this.excelFileToSave = excelFileToSave;
    }

    public StreamedContent getGexfFile() {
        return gexfFile;
    }

    public void setGexfFile(StreamedContent gexfFile) {
        this.gexfFile = gexfFile;
    }
    
    

    public Map<Integer, Multiset<String>> getCommunitiesResult() {
        return keywordsPerTopic;
    }

    public void setCommunitiesResult(Map<Integer, Multiset<String>> communitiesResult) {
        this.keywordsPerTopic = communitiesResult;
    }

    public int getPrecision() {
        return precision;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }

    public void onSlideEnd(SlideEndEvent event) {
        this.precision = (int) event.getValue();
    }

    public Boolean getScientificCorpus() {
        return scientificCorpus;
    }

    public void setScientificCorpus(Boolean scientificCorpus) {
        this.scientificCorpus = scientificCorpus;
    }

    public UploadedFile getFileUserStopwords() {
        return fileUserStopwords;
    }

    public void setFileUserStopwords(UploadedFile file) {
        this.fileUserStopwords = file;
    }

    public void uploadFile() {
        if (fileUserStopwords != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            FacesMessage message = new FacesMessage(success, fileUserStopwords.getFileName() + " " + is_uploaded + ".");
            FacesContext.getCurrentInstance().addMessage(null, message);
        }
    }

    public boolean isOkToShareStopwords() {
        return okToShareStopwords;
    }

    public void setOkToShareStopwords(boolean okToShareStopwords) {
        System.out.println("ok to share stopwords");
        this.okToShareStopwords = okToShareStopwords;
    }

    public boolean isReplaceStopwords() {
        return replaceStopwords;
    }

    public void setReplaceStopwords(boolean replaceStopwords) {
        System.out.println("ok to replace stopwords");
        this.replaceStopwords = replaceStopwords;
    }

    public int getMinCharNumber() {
        return minCharNumber;
    }

    public void setMinCharNumber(int minCharNumber) {
        this.minCharNumber = minCharNumber;
    }

    public int getMinTermFreq() {
        return minTermFreq;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
    }
    
    

    public List<Locale> getAvailable() {
        List<Locale> available = new ArrayList();
        String[] availableStopwordLists = new String[]{"ar", "bg", "ca", "da", "nl", "en", "fr", "de", "el", "it", "no", "pl", "pt", "ru", "es"};
        for (String tag : availableStopwordLists) {
            available.add(Locale.forLanguageTag(tag));
        }
        Collections.sort(available, new TopicsBean.LocaleComparator());
        return available;

    }

    public class LocaleComparator implements Comparator<Locale> {

        Locale requestLocale = FacesContext.getCurrentInstance().getExternalContext().getRequestLocale();

        @Override
        public int compare(Locale firstLocale, Locale secondLocale) {
            return firstLocale.getDisplayName(requestLocale).compareTo(secondLocale.getDisplayName(requestLocale));
        }

    }

}
