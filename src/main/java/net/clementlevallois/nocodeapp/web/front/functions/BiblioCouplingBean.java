package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import io.mikael.urlbuilder.util.RuntimeURISyntaxException;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephisto;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.PrimeFaces;
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
    private Boolean shareVVPublicly;
    private Boolean shareGephistoPublicly;
    private int maxSources = 300;
    private final Properties privateProperties;
    private ConcurrentHashMap<String, Set<String>> sourcesAndTargets;
    private int minSharedTargets;
    private Set<String> pubErrors;
    private String field;
    private boolean renderResultsButton = false;

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
            renderResultsButton = false;
            pubErrors = new HashSet();
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
            service.create("🎢" + sessionBean.getLocaleBundle().getString("bibliocoupling.info.startopenalexdataretrieval"));
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
                            .addParameter("identifier", entry.getValue())
                            .addParameter("fieldType", field)
                            .addParameter("number", String.valueOf(entry.getKey()))
                            .toUri();

                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .build();

                    CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                        byte[] body = resp.body();
                        String result = new String(body, StandardCharsets.UTF_8);
                        Float situation = (countTreated++ * 100 / (float) maxSources);
                        if (situation.intValue() > progress) {
                            progress = situation.intValue();
                        }
                        if (body.length >= 100
                                && !result.toLowerCase().startsWith("internal")
                                && !result.toLowerCase().startsWith("not found")) {
                            String[] fields = result.split("\\|");
                            if (fields.length > 1) {
                                String[] targets = fields[1].split(",");
                                Set<String> targetsSet = new HashSet(Arrays.asList(targets));
                                if (!targetsSet.isEmpty()) {
                                    sourcesAndTargets.put(fields[0], targetsSet);
                                } else {
                                    pubErrors.add(result);
                                }
                            } else {
                                pubErrors.add(result);
                            }
                        } else {
                            System.out.println("API biblio coupling returned an error:");
                            System.out.println(result);
                        }
                    }).exceptionally(exception -> {
                        System.err.println("exception: " + exception);
                        return null;
                    });
                    futures.add(future);
                    // this is because we need to slow down a bit the requests sending too many throws a
                    // java.util.concurrent.CompletionException: java.io.IOException: too many concurrent streams
                    Thread.sleep(2);
                }
                this.progress = 20;

                CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
                combinedFuture.join();

            } catch (InterruptedException ex) {
                System.out.println("ex:" + ex.getMessage());
            }
            service.create("🏁" + sessionBean.getLocaleBundle().getString("bibliocoupling.info.openalexdataretrieved"));
            service.create("💻" + sessionBean.getLocaleBundle().getString("bibliocoupling.info.startsim"));

            boolean callSimReturned = callSim(sourcesAndTargets);
            this.progress = 100;
            renderResultsButton = true;
            PrimeFaces.current().ajax().update("form");

            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
        } catch (RuntimeURISyntaxException | NumberFormatException ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }
    }

    public String goSeeResults() {
        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
    }

    public boolean callSim(Map<String, Set<String>> sourcesAndTargets) {

        try {

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
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
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            if (resp.statusCode() != 200) {
                String errorMessage = sessionBean.getLocaleBundle().getString("general.message.internal_server_error");
                service.create(errorMessage);
                System.out.println(errorMessage);
                runButtonDisabled = true;
                return false;
            }
            gexf = new String(body, StandardCharsets.UTF_8);
            this.progress = 80;

            if (gexf == null) {
                String errorMessage = sessionBean.getLocaleBundle().getString("general.message.internal_server_error");
                service.create(errorMessage);
                System.out.println("gexf was null in sim call for biblio coupling");
                runButtonDisabled = true;
                return false;
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

            resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            body = resp.body();
            if (resp.statusCode() != 200) {
                String errorMessage = sessionBean.getLocaleBundle().getString("general.message.internal_server_error");
                service.create(errorMessage);
                System.out.println("top nodes did not return a 200 code for biblio coupling");
                runButtonDisabled = true;
                return false;
            }
            String jsonResult = new String(body, StandardCharsets.UTF_8);
            JsonObject jsonObject = Json.createReader(new StringReader(jsonResult)).readObject();
            nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
            edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));

        } catch (IOException | InterruptedException ex) {
            System.out.println("exception when getting top nodes: " + ex);
            return false;
        }
        return true;
    }

    private void redirectAfterCompletion() {
        try {
            ExternalContext externalContext
                    = FacesContext.getCurrentInstance().getExternalContext();
            externalContext.redirect("/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true");
        } catch (IOException ex) {
            Logger.getLogger(BiblioCouplingBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void gotoVV() {
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromGexf(gexf, shareVVPublicly, privateProperties);
        if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(linkToVosViewer);
            } catch (IOException ex) {
                System.out.println("error in ops for export to vv");
            }
        }
    }

    public void gotoGephisto() {
        String urlToGephisto = ExportToGephisto.exportAndReturnLink(gexf, shareGephistoPublicly);
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        try {
            externalContext.redirect(urlToGephisto);
        } catch (IOException ex) {
            System.out.println("error in redirect to Gephisto");
        }
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

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public boolean isRenderResultsButton() {
        return renderResultsButton;
    }

    public void setRenderResultsButton(boolean renderResultsButton) {
        this.renderResultsButton = renderResultsButton;
    }
    
    

}
