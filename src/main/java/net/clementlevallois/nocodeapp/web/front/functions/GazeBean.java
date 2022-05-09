/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.enterprise.context.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import net.clementlevallois.gaze.controller.CosineCalculation;
import net.clementlevallois.gaze.controller.MatrixBuilder;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.CellRecord;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.importdata.SheetModel;
import net.clementlevallois.nocodeapp.web.front.io.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.utils.FindAllPairs;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.UnDirectedPair;
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
import org.openide.util.Lookup;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class GazeBean implements Serializable {

    private String option = "1";
    private Integer progress;
    private Graph graphResult;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String sessionId;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private String vosviewerJsonFileName;
    private Boolean shareVVPublicly;
    private String gephistoGexfFileName;
    private Boolean shareGephistoPublicly;
    private GraphModel gm;
    private Workspace workspace;
    private boolean applyPMI = false;

    @Inject
    NotificationService service;

    @Inject
    DataImportBean inputData;

    @Inject
    SessionBean sessionBean;

    public GazeBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("gaze");
        sessionBean.sendFunctionPageReport();
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public void onTabChange(String sheetName) {
        inputData.setSelectedSheetName(sheetName);
    }

    public String runCoocAnalysis() {
        try {
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = inputData.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(inputData.getSelectedSheetName())) {
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

            if (inputData.getHasHeaders()) {
                lines.remove(0);
            }

            callCooc(lines);
            progress = 100;
            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            renderSeeResultsButton = true;
            runButtonDisabled = true;

            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
        } catch (Exception ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }

    }

    public String runSimAnalysis(String sourceColIndex, String sheetName) {
        try {
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = inputData.getDataInSheets();
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
            if (inputData.getHasHeaders()) {
                mapOfCellRecordsPerRow.remove(0);
            }
            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();

            Map<String, Set<String>> sourcesAndTargets = new HashMap();
            Set<String> setTargets;
            String source = "";
            int sourceIndexInt = Integer.valueOf(sourceColIndex);
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
            renderSeeResultsButton = true;
            runButtonDisabled = true;

            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";
        } catch (Exception ex) {
            Logger.getLogger(CowoBean.class.getName()).log(Level.SEVERE, null, ex);
            return "";
        }

    }

    public void callCooc(Map<Integer, Multiset<String>> inputLines) throws Exception {

        progress = 5;

        Iterator<Map.Entry<Integer, Multiset<String>>> iterator = inputLines.entrySet().iterator();
        FindAllPairs pairFinder;
        Multiset<UnDirectedPair<String>> allUndirectedPairs = new Multiset();

        Multiset<String> nodesAsString = new Multiset();

        while (iterator.hasNext()) {
            Map.Entry<Integer, Multiset<String>> entry = iterator.next();
            pairFinder = new FindAllPairs();
            Multiset<String> itemsOnOneLine = entry.getValue();
            for (String item : itemsOnOneLine.toListOfAllOccurrences()) {
                nodesAsString.addOne(item);
            }
            Set<UnDirectedPair<String>> undirectedPairsAsListOneLine = pairFinder.getAllUndirectedPairsFromList(itemsOnOneLine.toListOfAllOccurrences());
            allUndirectedPairs.addAllFromListOrSet(undirectedPairsAsListOneLine);
        }

        service.create(sessionBean.getLocaleBundle().getString("general.message.last_ops_creating_network"));
        progress = 95;

        gm = new GraphModelImpl();
        gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
        if (applyPMI) {
            gm.getEdgeTable().addColumn("countEdge", Integer.TYPE);
        }
        GraphFactory factory = gm.factory();
        graphResult = gm.getGraph();

        Set<Node> nodes = new HashSet();
        Node node;
        for (String nodeString : nodesAsString.toListOfAllOccurrences()) {
            node = factory.newNode(nodeString);
            node.setLabel(nodeString);
            node.setAttribute("countTerms", nodesAsString.getCount(nodeString));
            nodes.add(node);
        }
        graphResult.addAllNodes(nodes);

        Set<Edge> edgesForGraph = new HashSet();
        Edge edge;
        for (UnDirectedPair<String> edgeToCreate : allUndirectedPairs.getElementSet()) {
            Node nodeSource = graphResult.getNode(edgeToCreate.getLeft());
            Node nodeTarget = graphResult.getNode(edgeToCreate.getRight());
            if (applyPMI) {
                int sourceCount = (Integer) nodeSource.getAttribute("countTerms");
                int targetCount = (Integer) nodeTarget.getAttribute("countTerms");
                float edgePMIWeight = (float) allUndirectedPairs.getCount(edgeToCreate) / (sourceCount * targetCount);
                edge = factory.newEdge(nodeSource, nodeTarget, 0, edgePMIWeight, false);
                edge.setAttribute("countEdge", allUndirectedPairs.getCount(edgeToCreate));
            } else {
                edge = factory.newEdge(nodeSource, nodeTarget, 0, allUndirectedPairs.getCount(edgeToCreate), false);
            }
            edgesForGraph.add(edge);
        }
        graphResult.addAllEdges(edgesForGraph);

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

    }

    public void callSim(Map<String, Set<String>> sourcesAndTargets) throws Exception {

        progress = 5;
        double cosineThreshold = 0.05;
        int maxNbTargetsPerSourceConsidered4CosineCalc = 1000;

        SparseDoubleMatrix2D similarityMatrixColt;

        MatrixBuilder matrixBuilder = new MatrixBuilder(sourcesAndTargets, maxNbTargetsPerSourceConsidered4CosineCalc);

        SparseDoubleMatrix1D[] listVectors = matrixBuilder.createListOfSparseVectorsFromEdgeList();

        similarityMatrixColt = new SparseDoubleMatrix2D(listVectors.length, listVectors.length);

        Thread t = new Thread(new CosineCalculation(listVectors, similarityMatrixColt));
        t.start();
        t.join();

        IntArrayList rowList = new IntArrayList();
        IntArrayList columnList = new IntArrayList();
        DoubleArrayList valueList = new DoubleArrayList();
        similarityMatrixColt.getNonZeros(rowList, columnList, valueList);

        int nonZeroCell = 0;
        int nbOfNonZeroCells = rowList.size();
        Set<String> nodesString = new HashSet();
        Map<UnDirectedPair, Double> mapUnDirectedPairsToTheirWeight = new HashMap();
        for (nonZeroCell = 0; nonZeroCell < nbOfNonZeroCells; nonZeroCell++) {
            int rowIndex = rowList.get(nonZeroCell);
            int colIndex = columnList.get(nonZeroCell);
            double cellValue = valueList.get(nonZeroCell);

            String sourceLabel = matrixBuilder.getMapSourcesIndexToLabel().get(rowIndex);
            String targetLabel = matrixBuilder.getMapSourcesIndexToLabel().get(colIndex);

            nodesString.add(sourceLabel);
            nodesString.add(targetLabel);

            UnDirectedPair newPair = new UnDirectedPair(sourceLabel, targetLabel);
            mapUnDirectedPairsToTheirWeight.put(newPair, cellValue);
        }

        service.create(sessionBean.getLocaleBundle().getString("general.message.last_ops_creating_network"));
        progress = 90;

        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        workspace = pc.getCurrentWorkspace();

        //Get a graph model - it exists because we have a workspace
        gm = Lookup.getDefault().lookup(GraphController.class).getGraphModel(workspace);

        GraphFactory factory = gm.factory();
        graphResult = gm.getGraph();

        Set<Node> nodes = new HashSet();
        Node node;
        for (String nodeString : nodesString) {
            node = factory.newNode(nodeString);
            node.setLabel(nodeString);
            nodes.add(node);
        }
        graphResult.addAllNodes(nodes);

        Map<Edge, Double> mapEdgesToTheirWeight = new HashMap();

        Set<Edge> edgesForGraph = new HashSet();
        Edge edge;
        Iterator<Map.Entry<UnDirectedPair, Double>> iteratorEdgesToCreate = mapUnDirectedPairsToTheirWeight.entrySet().iterator();
        while (iteratorEdgesToCreate.hasNext()) {
            Map.Entry<UnDirectedPair, Double> entry = iteratorEdgesToCreate.next();
            Node nodeSource = graphResult.getNode(entry.getKey().getLeft());
            Node nodeTarget = graphResult.getNode(entry.getKey().getRight());
            edge = factory.newEdge(nodeSource, nodeTarget, 0, entry.getValue(), false);
            edgesForGraph.add(edge);
            mapEdgesToTheirWeight.put(edge, entry.getValue());
        }

        graphResult.addAllEdges(edgesForGraph);

        JsonObjectBuilder objectBuilder = Json.createObjectBuilder();

        // we keep only the 30 strongest connections on the screen viz
        Map<Edge, Double> topWeightEdges
                = mapEdgesToTheirWeight.entrySet().stream()
                        .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                        .limit(30)
                        .collect(Collectors.toMap(
                                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        Set<String> nodesInGraph = new HashSet();

        Iterator<Map.Entry<Edge, Double>> iteratorTopWeightEdges = topWeightEdges.entrySet().iterator();

        while (iteratorTopWeightEdges.hasNext()) {
            Map.Entry<Edge, Double> next = iteratorTopWeightEdges.next();
            nodesInGraph.add((String) next.getKey().getSource().getId());
            nodesInGraph.add((String) next.getKey().getTarget().getId());
            objectBuilder.add((String) next.getKey().getSource().getId(), "1");
            objectBuilder.add((String) next.getKey().getTarget().getId(), "1");
        }
        nodesAsJson = objectBuilder.build().toString();

        objectBuilder = Json.createObjectBuilder();

        iteratorTopWeightEdges = topWeightEdges.entrySet().iterator();
        while (iteratorTopWeightEdges.hasNext()) {
            Map.Entry<Edge, Double> next = iteratorTopWeightEdges.next();
            if (nodesInGraph.contains((String) next.getKey().getSource().getId()) && nodesInGraph.contains((String) next.getKey().getTarget().getId())) {
                objectBuilder.add((String) next.getKey().getId(), Json.createObjectBuilder()
                        .add("source", (String) next.getKey().getSource().getId())
                        .add("target", ((String) next.getKey().getTarget().getId())));
            }
        }
        edgesAsJson = objectBuilder.build().toString();

    }

    public void gotoVV() throws IOException {
        if (gm == null) {
            System.out.println("gm object was null so gotoVV method exited");
            return;
        }
        GexfToVOSViewerJson converter = new GexfToVOSViewerJson(gm);

        converter.setMaxNumberNodes(500);
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
            path = "C:\\Users\\levallois\\Google Drive\\open\\no code app\\webapp\\jsf-app\\private\\";
        }

        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(path + vosviewerJsonFileName), StandardCharsets.UTF_8)) {
            bw.write(convertToJson);
        }

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String urlVV = "https://test.nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
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
}
