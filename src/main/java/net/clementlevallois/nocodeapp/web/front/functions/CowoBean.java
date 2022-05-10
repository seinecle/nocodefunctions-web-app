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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toSet;
import javax.annotation.PostConstruct;
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
import javax.servlet.annotation.MultipartConfig;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.lemmatizerlightweight.Lemmatizer;
import net.clementlevallois.ngramops.NGramDuplicatesCleaner;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonGoogle;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.importdata.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.io.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.textops.TextOps;
import net.clementlevallois.stopwords.StopWordsRemover;
import net.clementlevallois.stopwords.Stopwords;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.PerformCombinations;
import net.clementlevallois.utils.StatusCleaner;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphFactory;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.impl.GraphModelImpl;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.spi.CharacterExporter;
import org.gephi.io.exporter.spi.Exporter;
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
    private String sessionId;
    private String selectedLanguage;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private int minCoocFreqInt = 2;
    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private UploadedFile fileUserStopwords;
    private String vosviewerJsonFileName;
    private GraphModel gm;
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
    SingletonGoogle gDrive;

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

    @PostConstruct
    public void init() {
        sessionId = Faces.getSessionId();
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
            mapOfLines = StatusCleaner.doAllCleaningOps(mapOfLines);

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

            TextOps textOps = new TextOps();
            Multiset<String> freqNGramsGlobal = textOps.extractNGramsFromMapOfLines(mapOfLines);

            service.create(sessionBean.getLocaleBundle().getString("general.message.cleaning_key_terms"));
            progress = 30;
            Set<String> stopwords = Stopwords.getStopWords(selectedLanguage).get("long");
            NGramDuplicatesCleaner cleaner = new NGramDuplicatesCleaner(stopwords);
            cleaner.removeDuplicates(freqNGramsGlobal.getInternalMap(), 4, true);

            service.create(sessionBean.getLocaleBundle().getString("general.message.cleaning_key_terms"));
            progress = 35;

            service.create(sessionBean.getLocaleBundle().getString("general.message.remove_stopwords_small_words"));
            Set<String> userSuppliedStopwords = null;
            if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.opening_user_supplied_stopwords"));
                try ( BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                    userSuppliedStopwords = br.lines().collect(toSet());
                }
            }

            if (userSuppliedStopwords != null && !userSuppliedStopwords.isEmpty() && okToShareStopwords) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.sending_user_stopwords_to_dev"));
                SendReport sendReport = new SendReport();
                sendReport.initShareStopwords("stopwords shared", userSuppliedStopwords.toString());
                sendReport.start();
            }
            StopWordsRemover stopWordsRemover;
            stopWordsRemover = new StopWordsRemover(minCharNumber, selectedLanguage);
            if (userSuppliedStopwords != null && !userSuppliedStopwords.isEmpty()) {
                stopWordsRemover.useUSerSuppliedStopwords(userSuppliedStopwords, replaceStopwords);
            }
            if (scientificCorpus) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.opening_academic_stopwords"));
                if (selectedLanguage.equals("en")) {
                    Set<String> scientificStopwordsInEnglish = Stopwords.getScientificStopwordsInEnglish();
                    stopWordsRemover.addFieldSpecificStopWords(scientificStopwordsInEnglish);
                }
                if (selectedLanguage.equals("fr")) {
                    Set<String> scientificStopwordsInFrench = Stopwords.getScientificStopwordsInFrench();
                    stopWordsRemover.addFieldSpecificStopWords(scientificStopwordsInFrench);
                }
            }

            stopWordsRemover.addWordsToRemove(new HashSet());
            stopWordsRemover.addStopWordsToKeep(new HashSet());

            Iterator<Map.Entry<String, Integer>> it = freqNGramsGlobal.getInternalMap().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> next = it.next();
                if (stopWordsRemover.shouldItBeRemoved(next.getKey())) {
                    it.remove();
                }
            }
            Iterator<String> itNGrams = freqNGramsGlobal.getElementSet().iterator();
            while (itNGrams.hasNext()) {
                String string = itNGrams.next();
                if (string.length() < minCharNumber | string.matches(".*\\d.*")) {
                    itNGrams.remove();
                }
            }

            this.progress = 36;

            service.create(sessionBean.getLocaleBundle().getString("general_message.retaining_freq_terms"));
            progress = 40;
            freqNGramsGlobal = freqNGramsGlobal.keepMostfrequent(freqNGramsGlobal, 2_000);

            service.create(sessionBean.getLocaleBundle().getString("general_message.counting_pairs_terms"));
            progress = 50;

//        // #### COUNTING CO-OCCURRENCES PER LINE
            Set<String> ngramsInLine;
            Set<String> setOccForOneLine;
            Set<String> setOccForOneLineCleaned;
            Multiset<String> setCombinationsTotal = new Multiset();
            for (Integer lineNumber : mapOfLines.keySet()) {

                String line = mapOfLines.get(lineNumber);
                int countTermsInThisLine = 0;

                for (String term : freqNGramsGlobal.getElementSet()) {
                    if (line.contains(term)) {
                        countTermsInThisLine++;
                    }
                }

                if (countTermsInThisLine < 2) {
                    continue;
                }
                ngramsInLine = new HashSet();

                for (String currFreqTerm : freqNGramsGlobal.getElementSet()) {
                    if (line.contains(currFreqTerm)) {
                        ngramsInLine.add(currFreqTerm);
                    }
                }

                String arrayWords[] = new String[ngramsInLine.size()];
                if (arrayWords.length >= 2) {
                    setOccForOneLine = new HashSet();
                    setOccForOneLineCleaned = new HashSet();
                    setOccForOneLine.addAll(new PerformCombinations(ngramsInLine.toArray(arrayWords)).call());
                    for (String pairOcc : setOccForOneLine) {
                        //                        System.out.println("current pair is:"+ pairOcc);
                        String[] pair = pairOcc.split(",");

                        if (pair.length == 2
                                & !pair[0].trim().equals(pair[1].trim()) & !pair[0].contains(pair[1]) & !pair[1].contains(pair[0])) {
                            setOccForOneLineCleaned.add(pairOcc);
                        }
                    }
                    setCombinationsTotal.addAllFromListOrSet(setOccForOneLineCleaned);
                }

            }

            service.create(sessionBean.getLocaleBundle().getString("general_message.finished_pairs_removing_less_frequent"));
            progress = 85;
            List<Map.Entry<String, Integer>> sortDesckeepAboveMinFreq = setCombinationsTotal.sortDesckeepAboveMinFreq(setCombinationsTotal, minCoocFreqInt);
            Multiset<String> temp = new Multiset();
            for (Map.Entry entry : sortDesckeepAboveMinFreq) {
                temp.addSeveral((String) entry.getKey(), (Integer) entry.getValue());
            }
            setCombinationsTotal = new Multiset();
            setCombinationsTotal.addAllFromMultiset(temp);

            ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
            pc.newProject();
            workspace = pc.getCurrentWorkspace();

            //Get a graph model - it exists because we have a workspace
            gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);

            gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
            gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
            gm.getEdgeTable().addColumn("countPairsWithPMI", Float.TYPE);
            GraphFactory factory = gm.factory();
            graphResult = gm.getGraph();

            Set<Node> nodes = new HashSet();
            Node node;
            for (Map.Entry<String, Integer> entry : freqNGramsGlobal.getInternalMap().entrySet()) {
                node = factory.newNode(entry.getKey());
                node.setLabel(entry.getKey());
                node.setAttribute("countTerms", entry.getValue());
                nodes.add(node);
            }

            graphResult.addAllNodes(nodes);

//this is to rescale weights from 0 to 10 and apply PMI
            List<Map.Entry<String, Integer>> sortDesc = setCombinationsTotal.sortDesc(setCombinationsTotal);
            if (sortDesc.isEmpty()) {
                return "";
            }
            String[] pairEdgeMostOccurrences = sortDesc.get(0).getKey().split(",");
            Integer countEdgeMax = sortDesc.get(0).getValue();
            Integer weightSourceOfEdgeMaxCooc = freqNGramsGlobal.getCount(pairEdgeMostOccurrences[0]);
            Integer weightTargetOfEdgeMaxCooc = freqNGramsGlobal.getCount(pairEdgeMostOccurrences[1]);
            double maxValue = (double) countEdgeMax / (weightSourceOfEdgeMaxCooc * weightTargetOfEdgeMaxCooc);

            Set<Edge> edgesForGraph = new HashSet();
            Edge edge;
            for (String edgeToCreate : setCombinationsTotal.getElementSet()) {
                String[] pair = edgeToCreate.split(",");
                Integer countEdge = setCombinationsTotal.getCount(edgeToCreate);
                Integer freqSource = freqNGramsGlobal.getCount(pair[0]);
                Integer freqTarget = freqNGramsGlobal.getCount(pair[1]);
                // THIS IS THE PMI STEP
                double edgeWeight = (double) countEdge / (freqSource * freqTarget);
                double edgeWeightRescaledToTen = (double) edgeWeight * 10 / maxValue;
                Node nodeSource = graphResult.getNode(pair[0]);
                Node nodeTarget = graphResult.getNode(pair[1]);
                edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                edge.setAttribute("countPairs", countEdge);

                edgesForGraph.add(edge);
            }

            graphResult.addAllEdges(edgesForGraph);

//        System.out.println("graph contains " + graphResult.getNodeCount() + " nodes");
//        System.out.println("graph contains " + graphResult.getEdgeCount() + " edges");
//removing nodes (terms) that have zero connection
            Iterator<Node> iterator = graphResult.getNodes().toCollection().iterator();
            Set<Node> nodesToRemove = new HashSet();

            while (iterator.hasNext()) {
                Node next = iterator.next();
                if (graphResult.getNeighbors(next).toCollection().isEmpty()) {
                    nodesToRemove.add(next);
                }
            }

            graphResult.removeAllNodes(nodesToRemove);

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

        } catch (Exception ex) {
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

        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
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