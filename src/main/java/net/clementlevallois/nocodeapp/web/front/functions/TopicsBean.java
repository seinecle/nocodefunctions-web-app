/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static java.util.stream.Collectors.toSet;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.servlet.annotation.MultipartConfig;
import net.clementlevallois.lemmatizerlightweight.Lemmatizer;
import net.clementlevallois.ngramops.NGramDuplicatesCleaner;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonGoogle;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.importdata.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.io.ExcelSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.networkops.Modularity;
import net.clementlevallois.nocodeapp.web.front.textops.TextOps;
import net.clementlevallois.stopwords.StopWordsRemover;
import net.clementlevallois.stopwords.Stopwords;
import net.clementlevallois.utils.Multiset;
import net.clementlevallois.utils.PerformCombinations;
import net.clementlevallois.utils.StatusCleaner;
import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphFactory;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.graph.impl.GraphModelImpl;
import org.omnifaces.util.Faces;
import org.openide.util.Exceptions;
import org.primefaces.event.SlideEndEvent;
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

    private Integer progress;
    private Map<Integer, Multiset<String>> communitiesResult;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String sessionId;
    private String selectedLanguage;
    private int precision = 50;
    private int minCoocFreqInt = 3;
    private int minCharNumber = 3;

    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private UploadedFile fileUserStopwords;

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

    public TopicsBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("topics");
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

            GraphModel gm = new GraphModelImpl();
            gm.getNodeTable().addColumn("countTerms", Integer.TYPE);
            gm.getEdgeTable().addColumn("countPairs", Integer.TYPE);
            gm.getEdgeTable().addColumn("countPairsWithPMI", Float.TYPE);
            GraphFactory factory = gm.factory();
            Graph graph = gm.getGraph();

            Set<Node> nodes = new HashSet();
            Node node;
            for (Map.Entry<String, Integer> entry : freqNGramsGlobal.getInternalMap().entrySet()) {
                node = factory.newNode(entry.getKey());
                node.setLabel(entry.getKey());
                node.setAttribute("countTerms", entry.getValue());
                nodes.add(node);
            }
            graph.addAllNodes(nodes);

//finding the edge with the max count (pair of terms that occur most frequently)
//this is to rescale weights from 0 to 10
            List<Map.Entry<String, Integer>> sortDesc = setCombinationsTotal.sortDesc(setCombinationsTotal);
            if (sortDesc.isEmpty()) {
                return "";
            }
            String[] pairEdgeMostOccurrences = sortDesc.get(0).getKey().split(",");
            Integer countEdgeMax = sortDesc.get(0).getValue();
            Integer weightSourceOfEdgeMaxCooc = freqNGramsGlobal.getCount(pairEdgeMostOccurrences[0]);
            Integer weightTargetOfEdgeMaxCooc = freqNGramsGlobal.getCount(pairEdgeMostOccurrences[1]);
            double maxValue = (double) countEdgeMax / (weightSourceOfEdgeMaxCooc * weightTargetOfEdgeMaxCooc);

//        System.out.println("max val of edges occurrences: " + countEdgeMax);
//        System.out.println("max val of edges occurrences discounted with PMI: " + maxValue);
            Set<Edge> edgesForGraph = new HashSet();
            Edge edge;
            for (String edgeToCreate : setCombinationsTotal.getElementSet()) {
                String[] pair = edgeToCreate.split(",");
                Integer countEdge = setCombinationsTotal.getCount(edgeToCreate);
                Integer weightSource = freqNGramsGlobal.getCount(pair[0]);
                Integer weightTarget = freqNGramsGlobal.getCount(pair[1]);
                double edgeWeight = (double) countEdge / (weightSource * weightTarget);
                double edgeWeightRescaledToTen = (double) edgeWeight * 10 / maxValue;
                Node nodeSource = graph.getNode(pair[0]);
                Node nodeTarget = graph.getNode(pair[1]);
                edge = factory.newEdge(nodeSource, nodeTarget, 0, edgeWeightRescaledToTen, false);
                edge.setAttribute("countPairs", countEdge);

//                if (Config.getPmi()){
//                    Integer freqNodeSource = (Integer)nodeSource.getAttribute("countTerms");
//                    Integer freqNodeTarget = (Integer)nodeTarget.getAttribute("countTerms");
//                    Float pmiEdge = (float)countEdge/ ((float)freqNodeSource * (float)freqNodeSource);
//                    
//                }
                edgesForGraph.add(edge);
            }
            graph.addAllEdges(edgesForGraph);

            System.out.println("graph contains " + graph.getNodeCount() + " nodes");
            System.out.println("graph contains " + graph.getEdgeCount() + " edges");

//removing nodes (terms) that have zero connection
            Iterator<Node> iterator = graph.getNodes().toCollection().iterator();
            Set<Node> nodesToRemove = new HashSet();
            while (iterator.hasNext()) {
                Node next = iterator.next();
                if (graph.getNeighbors(next).toCollection().isEmpty()) {
                    nodesToRemove.add(next);
                }
            }
            graph.removeAllNodes(nodesToRemove);
            this.progress = 95;

            service.create(sessionBean.getLocaleBundle().getString("back.topics.detecting_communities"));
            Modularity modularity = new Modularity();
            modularity.setUseWeight(true);
            modularity.setRandom(false);

//converting the value set by the user (between 0 and 100) to a value in the [0,2] range which fits the calculus of modularity by Gephi
// see https://stackoverflow.com/q/929103/798502
            double oldRange = 100d;
            double newRange = 2d;
            double resolution = (((double) precision) * newRange / oldRange);
            System.out.println("resolution is: " + resolution);
            modularity.setResolution(resolution);
            modularity.execute(graph);
            this.progress = 98;

//finding topics
            communitiesResult = new HashMap();

            Iterator<Node> iteratorNodes = graph.getNodes().iterator();
            while (iteratorNodes.hasNext()) {
                Node next = iteratorNodes.next();
                Integer v = (Integer) next.getAttribute(Modularity.MODULARITY_CLASS);
                if (!communitiesResult.containsKey(v)) {
                    communitiesResult.put(v, new Multiset());
                }
                Multiset<String> get = communitiesResult.get(v);
                get.addSeveral(next.getLabel(), (Integer) next.getAttribute("countTerms"));
                communitiesResult.put(v, get);
            }
            this.progress = 100;

            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            renderSeeResultsButton = true;
            runButtonDisabled = true;

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
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

    public StreamedContent getFileToSave() {
        if (communitiesResult == null || communitiesResult.isEmpty()) {
            return null;
        }
        return ExcelSaver.exportTopics(communitiesResult, 10);
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public Map<Integer, Multiset<String>> getCommunitiesResult() {
        return communitiesResult;
    }

    public void setCommunitiesResult(Map<Integer, Multiset<String>> communitiesResult) {
        this.communitiesResult = communitiesResult;
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