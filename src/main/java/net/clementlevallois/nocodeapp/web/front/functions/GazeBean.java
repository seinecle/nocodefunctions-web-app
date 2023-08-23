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
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import java.util.Properties;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.utils.Multiset;
import org.omnifaces.util.Faces;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class GazeBean implements Serializable {

    private Integer progress = 0;
    private Integer minSharedTargets = 1;
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
    private boolean applyPMI = false;

    private final Properties privateProperties;

    private String gexf;

    @Inject
    NotificationService service;

    @Inject
    DataImportBean dataImportBean;

    @Inject
    SessionBean sessionBean;

    public GazeBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("gaze");
        privateProperties = SingletonBean.getPrivateProperties();
    }

    public void onTabChange(String sheetName) {
        dataImportBean.setSelectedSheetName(sheetName);
    }

    public void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        try {
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(severity, summary, detail));
        } catch (NullPointerException e) {
            System.out.println("FacesContext.getCurrentInstance was null. Detail: " + detail);
        }
    }

    public String runCoocAnalysis() {
        try {
            progress = 0;
            sessionBean.sendFunctionPageReport();
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = dataImportBean.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(dataImportBean.getSelectedSheetName())) {
                    sheetWithData = sm;
                    break;
                }
            }
            if (sheetWithData == null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (1)");
                return "";
            }
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            Map<Integer, Multiset<String>> lines = new HashMap();

            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();
            int i = 0;
            Multiset<String> multiset;
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> next = iterator.next();
                multiset = new Multiset();
                for (CellRecord cr : next.getValue()) {
                    multiset.addOne(cr.getRawValue());
                }
                lines.put(i++, multiset);
            }
            if (lines.isEmpty()) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (2)");
                return "";
            }

            if (dataImportBean.getHasHeaders()) {
                lines.remove(0);
            }

            callCooc(lines);
            progress = 100;
            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            runButtonDisabled = true;

            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
        } catch (Exception ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }

    }

    public String runSimAnalysis(String sourceColIndex, String sheetName) {
        try {
            sessionBean.sendFunctionPageReport();
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = dataImportBean.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(sheetName)) {
                    sheetWithData = sm;
                    break;
                }
            }
            if (sheetWithData == null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (1)");
                return "";
            }
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            if (dataImportBean.getHasHeaders()) {
                mapOfCellRecordsPerRow.remove(0);
            }
            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();

            Map<String, Set<String>> sourcesAndTargets = new HashMap();
            Set<String> setTargets;
            String source = "";
            int sourceIndexInt = Integer.parseInt(sourceColIndex);
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> entryCellRecordsInRow = iterator.next();
                setTargets = new HashSet();
                for (CellRecord cr : entryCellRecordsInRow.getValue()) {
                    if (cr.getColIndex() == sourceIndexInt) {
                        source = cr.getRawValue();
                    } else {
                        setTargets.add(cr.getRawValue());
                    }
                }
                if (sourcesAndTargets.containsKey(source)) {
                    Set<String> existingTargetsForThisSource = sourcesAndTargets.get(source);
                    existingTargetsForThisSource.addAll(setTargets);
                    sourcesAndTargets.put(source, existingTargetsForThisSource);
                } else {
                    sourcesAndTargets.put(source, setTargets);
                }
            }
            if (sourcesAndTargets.isEmpty()) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (2)");
                return "";
            }

            callSim(sourcesAndTargets);
            progress = 100;
            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            runButtonDisabled = true;

            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
        } catch (Exception ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }

    }

    public void callCooc(Map<Integer, Multiset<String>> inputLines) throws Exception {

        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        JsonObjectBuilder overallObject = Json.createObjectBuilder();

        JsonObjectBuilder linesBuilder = Json.createObjectBuilder();
        for (Map.Entry<Integer, Multiset<String>> entryLines : inputLines.entrySet()) {
            JsonArrayBuilder createArrayBuilder = Json.createArrayBuilder();
            Multiset<String> targets = entryLines.getValue();
            for (String target : targets.toListOfAllOccurrences()) {
                createArrayBuilder.add(target);
            }
            linesBuilder.add(String.valueOf(entryLines.getKey()), createArrayBuilder);
        }

        overallObject.add("lines", linesBuilder);

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
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                .withHost("localhost")
                .withPath("api/gaze/cooc")
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
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                .withHost("localhost")
                .withPath("api/graphops/topnodes")
                .addParameter("nbNodes", "30")
                .toUri();

        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
            if (resp.statusCode() == 200) {
                byte[] body = resp.body();
                String jsonResult = new String(body, StandardCharsets.UTF_8);
                JsonObject jsonObject = Json.createReader(new StringReader(jsonResult)).readObject();
                nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
                edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
            } else {
                System.out.println("gaze results by the API was not a 200 code");
                String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
                addMessage(FacesMessage.SEVERITY_WARN, "💔", error);
            }
        }
        );
        futures.add(future);

        combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();
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
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                .withHost("localhost")
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
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
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
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
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
            path = SingletonBean.getRootOfProject() + "user_created_files";
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
            path = SingletonBean.getRootOfProject() + "user_created_files";
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

    public boolean isApplyPMI() {
        return applyPMI;
    }

    public void setApplyPMI(boolean applyPMI) {
        this.applyPMI = applyPMI;
    }

    public Integer getMinSharedTargets() {
        return minSharedTargets;
    }

    public void setMinSharedTargets(Integer minSharedTargets) {
        this.minSharedTargets = minSharedTargets;
    }
}
