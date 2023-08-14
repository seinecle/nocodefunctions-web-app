/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.omnifaces.util.Faces;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class BiblioCouplingBean implements Serializable {

    private Integer progress = 0;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private String graphAsJsonVosViewer;
    private String vosviewerJsonFileName;
    private Boolean shareVVPublicly;
    private String gephistoGexfFileName;
    private Boolean shareGephistoPublicly;
    private int maxSources = 300;
    private final Properties privateProperties;
    private ConcurrentHashMap<String, Set<String>> sourcesAndTargets;
    private int minSharedTargets;

    private int countTreated = 0;

    String gexf;

    @Inject
    NotificationService service;

    @Inject
    DataImportBean dataImportBean;

    @Inject
    SessionBean sessionBean;

    public BiblioCouplingBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("bibliocoupling");
        privateProperties = SingletonBean.getPrivateProperties();
    }

    public String runBiblioCouplingAnalysis() {
        try {
            progress = 0;
            sessionBean.sendFunctionPageReport();
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            Map<Integer, String> mapOfLines = dataFormatConverter.convertToMapOfLines(dataImportBean.getBulkData(), dataImportBean.getDataInSheets(), dataImportBean.getSelectedSheetName(), dataImportBean.getSelectedColumnIndex(), dataImportBean.getHasHeaders());

            maxSources = Math.min(mapOfLines.size(), maxSources);
            sourcesAndTargets = new ConcurrentHashMap(maxSources + 1);
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            int i = 1;
            service.create(sessionBean.getLocaleBundle().getString("bibliocoupling.info.startopenalexdataretrieval"));
            try {
                for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                    if (i++ > maxSources) {
                        break;
                    }

                    URI uri = UrlBuilder
                            .empty()
                            .withScheme("http")
                            .withHost("localhost")
                            .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))))
                            .withPath("api/bibliocoupling")
                            .addParameter("title", entry.getValue())
                            .addParameter("number", String.valueOf(entry.getKey()))
                            .toUri();

                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .build();

                    CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                        byte[] body = resp.body();
                        Float situation = (countTreated++ * 100 / (float) maxSources);
                        if (situation.intValue() > progress) {
                            progress = situation.intValue();
                        }
                        if (body.length >= 100 && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("internal") && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("not found")) {
                            try (
                                    ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                                String result = (String) ois.readObject();
                                String[] fields = result.split("\\|");
                                if (fields.length > 1) {

                                    String[] targets = fields[1].split(",");
                                    Set<String> targetsSet = new HashSet(Arrays.asList(targets));
                                    sourcesAndTargets.put(fields[0], targetsSet);
                                } else {
                                    service.create(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
                                }
                            } catch (Exception ex) {
                                System.out.println("error in body:");
                                System.out.println(new String(body, StandardCharsets.UTF_8));
                                Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    }
                    );
                    futures.add(future);
                    // this is because we need to slow down a bit the requests sending too many throws a
                    // java.util.concurrent.CompletionException: java.io.IOException: too many concurrent streams

                    // also the function calls the OpenAlex API which can't be called more than 10 times per seconds.
                    Thread.sleep(250);
                }
                this.progress = 40;
                service.create(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

                CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
                combinedFuture.join();

            } catch (InterruptedException ex) {
                System.out.println("ex:" + ex.getMessage());
            }
            service.create(sessionBean.getLocaleBundle().getString("bibliocoupling.info.openalexdataretrieved"));
            service.create(sessionBean.getLocaleBundle().getString("bibliocoupling.info.startsim"));

            callSim(sourcesAndTargets);
            this.progress = 100;

            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
        } catch (Exception ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }

    public void callSim(Map<String, Set<String>> sourcesAndTargets) throws Exception {

        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        JsonObjectBuilder overallObject = Json.createObjectBuilder();

        JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
        for (Map.Entry<String, Set<String>> entryLines : sourcesAndTargets.entrySet()) {
            JsonArrayBuilder createArrayBuilder = Json.createArrayBuilder();
            Set<String> targets = entryLines.getValue();
            for (String target : targets) {
                createArrayBuilder.add(target);
            }
            linesBuilder.add(entryLines.getKey(), createArrayBuilder);
        }

        JsonObjectBuilder parametersBuilder = Json.createObjectBuilder();
        parametersBuilder.add("minSharedTarget", minSharedTargets);

        overallObject.add("lines", linesBuilder);
        overallObject.add("parameters", parametersBuilder);

        JsonObject build = overallObject.build();
        StringWriter sw = new StringWriter(128);
        try (JsonWriter jw = Json.createWriter(sw)) {
            jw.write(build);
        }
        String jsonString = sw.toString();

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withHost("localhost")
                .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))))
                .withPath("api/gaze/sim")
                .toUri();

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
        this.progress = 80;

        if (gexf == null) {
            service.create(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
            runButtonDisabled = true;
            return;
        }
        progress = 80;

        bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(gexf.getBytes(StandardCharsets.UTF_8));

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
            nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
            edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
        }
        );
        futures.add(future);

        combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();
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
            path = SingletonBean.getPATHLOCALE() + "user_created_files";
        }

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(path + vosviewerJsonFileName), StandardCharsets.UTF_8)) {
            bw.write(graphAsJsonVosViewer);
        }

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String domain;
        if (RemoteLocal.isTest()) {
            domain = "https://test.nocodefunctions.com";
        } else {
            domain = "https://nocodefunctions.com";
        }
        String urlVV = domain + "/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
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
            path = SingletonBean.getPATHLOCALE() + "user_created_files";
        }

        File file = new File(path + gephistoGexfFileName);
        try (OutputStream output = new FileOutputStream(file, false)) {
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

    public Boolean getShareVVPublicly() {
        return shareVVPublicly;
    }

    public void setShareVVPublicly(Boolean shareVVPublicly) {
        this.shareVVPublicly = shareVVPublicly;
    }

    public String getGephistoGexfFileName() {
        return gephistoGexfFileName;
    }

    public void setGephistoGexfFileName(String gephistoGexfFileName) {
        this.gephistoGexfFileName = gephistoGexfFileName;
    }

    public Boolean getShareGephistoPublicly() {
        return shareGephistoPublicly;
    }

    public void setShareGephistoPublicly(Boolean shareGephistoPublicly) {
        this.shareGephistoPublicly = shareGephistoPublicly;
    }

    public int getMaxSources() {
        return maxSources;
    }

    public void setMaxSources(int maxSources) {
        this.maxSources = maxSources;
    }

    public int getMinSharedTargets() {
        return minSharedTargets;
    }

    public void setMinSharedTargets(int minSharedTargets) {
        this.minSharedTargets = minSharedTargets;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

}
