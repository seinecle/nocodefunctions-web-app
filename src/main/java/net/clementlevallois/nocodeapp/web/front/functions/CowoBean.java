/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static java.util.stream.Collectors.toList;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.servlet.annotation.MultipartConfig;
import net.clementlevallois.lemmatizerlightweight.Lemmatizer;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean.Source;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Utils;
import net.clementlevallois.utils.TextCleaningOps;
import org.omnifaces.util.Faces;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig

public class CowoBean implements Serializable {

    private Integer progress;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String selectedLanguage;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private int minTermFreq = 2;
    private int minCoocFreqInt = 2;
    private String typeCorrection = "none";
    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private UploadedFile fileUserStopwords;
    private String vosviewerJsonFileName;
    private String graphAsJsonVosViewer;
    private String gexf;
    private Boolean shareVVPublicly;
    private String gephistoGexfFileName;
    private Boolean shareGephistoPublicly;
    private Integer minCharNumber = 5;
    private Lemmatizer lemmatizer;
    private Map<Integer, String> mapOfLines;

    @Inject
    NotificationService service;

    @Inject
    DataImportBean inputData;

    @Inject
    SessionBean sessionBean;

    public CowoBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("cowo");
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

    public String getReport() {
        return "";
    }

    public String runAnalysis() {
        try {
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());

            if (mapOfLines == null || mapOfLines.isEmpty()) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                return "";
            }

            if (selectedLanguage == null) {
                selectedLanguage = "en";
            }
            this.progress = 10;
            service.create(sessionBean.getLocaleBundle().getString("general.message.removing_punctuation_and_cleaning"));

            /* this step addresses the case of text imported from a PDF source.
            
            PDF imports can have their last words truncated at the end, like so:
            
            "Inbound call centers tend to focus on assistance for customers who need to solve their problems, ques-
            tions bout a product or service, schedule appointments, dispatch technicians, or need instructions"

            In this case, the last word of the first sentence should be removed,
            and so should be the first word of the following line.
            
            Thx to https://twitter.com/Verukita1 for reporting the issue with a test case.
            
             */
            if (inputData.getSource().equals(Source.PDF)) {
                boolean cutWordDetected = false;
                List<String> hyphens = List.of(
                        Character.toString('\u2010'),
                        Character.toString('\u2011'),
                        Character.toString('\u2012'),
                        Character.toString('\u2013'),
                        Character.toString('\u002D'),
                        Character.toString('\u007E'),
                        Character.toString('\u00AD'),
                        Character.toString('\u058A'),
                        Character.toString('\u05BE'),
                        Character.toString('\u1806'),
                        Character.toString('\u2014'),
                        Character.toString('\u2015'),
                        Character.toString('\u2053'),
                        Character.toString('\u207B'),
                        Character.toString('\u208B'),
                        Character.toString('\u2212'),
                        Character.toString('\u301C'),
                        Character.toString('\uFE58'),
                        Character.toString('\uFE63'),
                        Character.toString('\uFF0D'));

                for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                    String line = entry.getValue().trim();
                    if (cutWordDetected) {
                        int indexFirstSpace = line.indexOf(" ");
                        if (indexFirstSpace > 0) {
                            line = line.substring(indexFirstSpace + 1);
                            entry.setValue(line);
                        }
                        cutWordDetected = false;
                    }
                    for (String hyphen : hyphens) {
                        if (line.endsWith(hyphen)) {
                            cutWordDetected = true;
                            int indexLastSpace = line.lastIndexOf(" ");
                            if (indexLastSpace > 0) {
                                line = line.substring(0, indexLastSpace);
                                entry.setValue(line);
                            }
                            break;
                        }
                    }
                }
            }
            mapOfLines = TextCleaningOps.doAllCleaningOps(mapOfLines);
            mapOfLines = TextCleaningOps.putInLowerCase(mapOfLines);

            this.progress = 15;
            if (selectedLanguage.equals("en") | selectedLanguage.equals("fr")) {
                try {
                    lemmatizer = new Lemmatizer(selectedLanguage);
                    mapOfLines.keySet()
                            .stream().forEach((key) -> {
                                mapOfLines.put(key, lemmatizer.sentenceLemmatizer(mapOfLines.get(key)));
                            });
                } catch (Exception ex) {
                    System.out.println("ex:" + ex.getMessage());
                }
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

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(uri)
                            .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                            .build();
                    HttpClient client = HttpClient.newHttpClient();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        String body = response.body();
                        JsonObject jsonObjectReturned;
                        try ( JsonReader reader = Json.createReader(new StringReader(body))) {
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

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
            for (Map.Entry<Integer, String> entryLines : mapOfLines.entrySet()) {
                linesBuilder.add(String.valueOf(entryLines.getKey()), entryLines.getValue());
            }

            JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
            if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
                List<String> userSuppliedStopwords;
                try ( BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                    userSuppliedStopwords = br.lines().collect(toList());
                }
                int index = 0;
                for (String stopword : userSuppliedStopwords) {
                    userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                }
            }

            overallObject.add("lines", linesBuilder);
            overallObject.add("lang", selectedLanguage);
            overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);
            overallObject.add("minCharNumber", minCharNumber);
            overallObject.add("replaceStopwords", replaceStopwords);
            overallObject.add("isScientificCorpus", scientificCorpus);
            overallObject.add("minCoocFreq", minCoocFreqInt);
            overallObject.add("minTermFreq", minTermFreq);
            overallObject.add("typeCorrection", typeCorrection);

            JsonObject build = overallObject.build();
            StringWriter sw = new StringWriter(128);
            try ( JsonWriter jw = Json.createWriter(sw)) {
                jw.write(build);
            }
            String jsonString = sw.toString();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            URI uri = new URI("http://localhost:7002/api/cowo/");

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                gexf = new String(body, StandardCharsets.UTF_8);
            }
            );
            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

            if (gexf == null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
                renderSeeResultsButton = true;
                runButtonDisabled = true;
                return "";
            }

            bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

            uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7002)
                    .withHost("localhost")
                    .withPath("api/graphops/topnodes")
                    .addParameter("nbNodes", "30")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                String jsonResult = new String(body, StandardCharsets.UTF_8);
                JsonObject jsonObject = Json.createReader(new StringReader(jsonResult)).readObject();
                nodesAsJson = Utils.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
                edgesAsJson = Utils.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
            }
            );
            futures.add(future);

            combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

        } catch (IOException | NumberFormatException | URISyntaxException ex) {
            System.out.println("ex:" + ex.getMessage());
        }

        return "/" + sessionBean.getFunction()
                + "/results.xhtml?faces-redirect=true";

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

    public StreamedContent getFileToSave() {
        return GEXFSaver.exportGexfAsStreamedFile(gexf, "results");
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public void execGraph(int maxNodes) {

    }

    public String getNodesAsJson() {
        return nodesAsJson;
    }

    public void setNodesAsJson(String nodesAsJson) {
        this.nodesAsJson = nodesAsJson;
    }

    public String getEdgesAsJson() {
        return edgesAsJson;
    }

    public void setEdgesAsJson(String edgesAsJson) {
        this.edgesAsJson = edgesAsJson;
    }

    public int getMinFreqNode() {
        return minFreqNode;
    }

    public void setMinFreqNode(int minFreqNode) {
        this.minFreqNode = minFreqNode;
    }

    public int getMaxFreqNode() {
        return maxFreqNode;
    }

    public void setMaxFreqNode(int maxFreqNode) {
        this.maxFreqNode = maxFreqNode;
    }

    public boolean isScientificCorpus() {
        return scientificCorpus;
    }

    public void setScientificCorpus(boolean scientificCorpus) {
        this.scientificCorpus = scientificCorpus;
    }

    public UploadedFile getFileUserStopwords() {
        return fileUserStopwords;
    }

    public void setFileUserStopwords(UploadedFile file) {
        this.fileUserStopwords = file;
    }

    public void uploadFile() {
        if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {

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
        this.okToShareStopwords = okToShareStopwords;
    }

    public boolean isReplaceStopwords() {
        return replaceStopwords;
    }

    public void setReplaceStopwords(boolean replaceStopwords) {
        this.replaceStopwords = replaceStopwords;
    }

    public Boolean getShareVVPublicly() {
        return shareVVPublicly;
    }

    public void setShareVVPublicly(Boolean shareVVPublicly) {
        this.shareVVPublicly = shareVVPublicly;
    }

    public Boolean getShareGephistoPublicly() {
        return shareGephistoPublicly;
    }

    public void setShareGephistoPublicly(Boolean shareGephistoPublicly) {
        this.shareGephistoPublicly = shareGephistoPublicly;
    }

    public Integer getMinCharNumber() {
        return minCharNumber;
    }

    public void setMinCharNumber(Integer minCharNumber) {
        this.minCharNumber = minCharNumber;
    }

    public void gotoVV() throws IOException {
        if (gexf == null) {
            System.out.println("gm object was null so gotoVV method exited");
            return;
        }

        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(gexf.getBytes(StandardCharsets.UTF_8));

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(7002)
                .withHost("localhost")
                .withPath("api/convert2vv")
                .addParameter("item", "Term")
                .addParameter("items", "Terms")
                .addParameter("link", "Co-occurrence link")
                .addParameter("links", "Co-occurrence links")
                .addParameter("linkStrength", "Number of co-occurrences")
                .addParameter("totalLinkStrength", "Total number of co-occurrences")
                .addParameter("descriptionData", "Made with nocodefunctions.com")
                .toUri();

        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                resp -> {
                    byte[] body = resp.body();
                    graphAsJsonVosViewer = new String(body, StandardCharsets.UTF_8);
                }
        );

        futures.add(future);

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();
        String path = RemoteLocal.isLocal() ? "" : "html/vosviewer/data/";
        String subfolder;
        vosviewerJsonFileName = "vosviewer_" + Faces.getSessionId().substring(0, 20) + ".json";
        if (shareVVPublicly) {
            subfolder = "public/";
        } else {
            subfolder = "private/";
        }
        path = path + subfolder;

        if (RemoteLocal.isLocal()) {
            path = "C:\\Users\\levallois\\Google Drive\\open\\no code app\\webapp\\jsf-app\\private";
        }

        BufferedWriter bw = Files.newBufferedWriter(Path.of(path + vosviewerJsonFileName), StandardCharsets.UTF_8);
        bw.write(graphAsJsonVosViewer);
        bw.close();

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String urlVV;
        if (RemoteLocal.isTest()) {
            urlVV = "https://test.nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;

        } else {
            urlVV = "https://nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
        }
        externalContext.redirect(urlVV);
    }

    public void gotoGephisto() throws IOException {

        byte[] readAllBytes = gexf.getBytes();
        InputStream inputStreamToSave = new ByteArrayInputStream(readAllBytes);

        String path = RemoteLocal.isLocal() ? "" : "gephisto/data/";
        String subfolder;
        gephistoGexfFileName = "gephisto_" + Faces.getSessionId().substring(0, 20) + ".gexf";

        if (shareGephistoPublicly) {
            subfolder = "public/";
        } else {
            subfolder = "private/";
        }
        path = path + subfolder;

        if (RemoteLocal.isLocal()) {
            path = "C:\\Users\\levallois\\Google Drive\\open\\no code app\\webapp\\jsf-app\\private\\";
        }

        File file = new File(path + gephistoGexfFileName);
        try ( OutputStream output = new FileOutputStream(file, false)) {
            inputStreamToSave.transferTo(output);
        }

        String urlGephisto;
        if (RemoteLocal.isTest()) {
            urlGephisto = "https://test.nocodefunctions.com/gephisto/index.html?gexf-file=" + subfolder + gephistoGexfFileName;

        } else {
            urlGephisto = "https://nocodefunctions.com/gephisto/index.html?gexf-file=" + subfolder + gephistoGexfFileName;
        }
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        externalContext.redirect(urlGephisto);
    }

    public List<Locale> getAvailable() {
        List<Locale> available = new ArrayList();
        String[] availableStopwordLists = new String[]{"ar", "bg", "ca", "da", "nl", "en", "fr", "de", "el", "it", "no", "pl", "pt", "ru", "es"};
        for (String tag : availableStopwordLists) {
            available.add(Locale.forLanguageTag(tag));
        }
        Collections.sort(available, new CowoBean.LocaleComparator());
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
