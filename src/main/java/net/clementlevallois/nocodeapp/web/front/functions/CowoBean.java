/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import javax.servlet.annotation.MultipartConfig;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.lemmatizerlightweight.Lemmatizer;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean.Source;
import net.clementlevallois.nocodeapp.web.front.io.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.utils.TextCleaningOps;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.spi.CharacterExporter;
import org.gephi.io.exporter.spi.Exporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerUnloader;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.importer.plugin.file.ImporterGEXF;
import org.gephi.io.importer.spi.FileImporter;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.omnifaces.util.Faces;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
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
    private Graph graphResult;
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
    private GraphModel gm;
    private String gexf;
    private Workspace workspace;
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
                    Exceptions.printStackTrace(ex);
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
                    Exceptions.printStackTrace(ex);
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
                gexf = new String (body, StandardCharsets.UTF_8);
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

            /* TURN THE GEXF RETURNED FROM THE COWO API INTRO A GRAPH MODEL  */
            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            workspace = pc.getCurrentWorkspace();

            //Get controllers and models
            ImportController importController = Lookup.getDefault().lookup(ImportController.class);
            //Import file
            Container container;
            FileImporter fi = new ImporterGEXF();
            InputStream is = new ByteArrayInputStream(gexf.getBytes());
            container = importController.importFile(is, fi);
            container.closeLoader();
            DefaultProcessor processor = new DefaultProcessor();

            processor.setWorkspace(pc.getCurrentWorkspace());
            processor.setContainers(new ContainerUnloader[]{container.getUnloader()});
            processor.process();

            GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
            gm = graphController.getGraphModel();
            graphResult = gm.getGraph();

            Iterator<Node> iteratorNodes = graphResult.getNodes().iterator();
            Iterator<Edge> iteratorEdges = graphResult.getEdges().iterator();
            JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            Map<Node, Integer> nodesToFreq = new HashMap();

            while (iteratorNodes.hasNext()) {
                Node nodeGraph = iteratorNodes.next();
                int freqNode = (int) nodeGraph.getAttribute("countTerms");
                nodesToFreq.put(nodeGraph, freqNode);
                if (minFreqNode > freqNode) {
                    minFreqNode = freqNode;
                }
                if (maxFreqNode < freqNode) {
                    maxFreqNode = freqNode;
                }
            }

// we keep only the 30 most freq nodes for the graph viz
            Map<Node, Integer> topFreq
                    = nodesToFreq.entrySet().stream()
                            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .limit(30)
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            Iterator<Map.Entry<Node, Integer>> iteratorNodes2 = topFreq.entrySet().iterator();

            Set<String> nodesInGraph = new HashSet();

            while (iteratorNodes2.hasNext()) {
                Map.Entry<Node, Integer> next = iteratorNodes2.next();
                nodesInGraph.add((String) next.getKey().getId());
                objectBuilder.add((String) next.getKey().getId(), String.valueOf(next.getKey().getAttribute("countTerms")));
            }
            nodesAsJson = objectBuilder.build().toString();

            objectBuilder = Json.createObjectBuilder();

            while (iteratorEdges.hasNext()) {
                Edge edgeGraph = iteratorEdges.next();
                if (nodesInGraph.contains((String) edgeGraph.getSource().getId()) && nodesInGraph.contains((String) edgeGraph.getTarget().getId())) {
                    objectBuilder.add((String) edgeGraph.getId(), Json.createObjectBuilder()
                            .add("source", (String) edgeGraph.getSource().getId())
                            .add("target", ((String) edgeGraph.getTarget().getId())));
                }
            }
            edgesAsJson = objectBuilder.build().toString();

            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            renderSeeResultsButton = true;
            runButtonDisabled = true;

            if (graphResult.getEdges().toCollection().isEmpty()) {
                service.create(sessionBean.getLocaleBundle().getString("general_message.empty_network_no_connection"));
                return "";
            }

        } catch (IOException | NumberFormatException | URISyntaxException ex) {
            Exceptions.printStackTrace(ex);
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
        return GEXFSaver.exportGexfAsStreamedFile(workspace, "results");
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
        GexfToVOSViewerJson converter = new GexfToVOSViewerJson(gm);
        converter.setMaxNumberNodes(250);
        converter.setTerminologyData(new Terminology());
        converter.getTerminologyData().setItem("Term");
        converter.getTerminologyData().setItems("Terms");
        converter.getTerminologyData().setLink("Co-occurrence link");
        converter.getTerminologyData().setLinks("Co-occurrence links");
        converter.getTerminologyData().setLink_strength("Number of co-occurrences");
        converter.getTerminologyData().setTotal_link_strength("Total number of co-occurrences");
        converter.setMetadataData(new Metadata());
        converter.getMetadataData().setAuthorCanBePlural("");
        converter.getMetadataData().setDescriptionOfData("Made with nocodefunctions.com");

        String convertToJson = converter.convertToJson();
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
        bw.write(convertToJson);
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

        ExportController ec = Lookup.getDefault().lookup(ExportController.class
        );
        Exporter exporterGexf = ec.getExporter("gexf");
        exporterGexf.setWorkspace(workspace);
        StringWriter stringWriter = new StringWriter();
        ec.exportWriter(stringWriter, (CharacterExporter) exporterGexf);
        byte[] readAllBytes = stringWriter.toString().getBytes();
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
