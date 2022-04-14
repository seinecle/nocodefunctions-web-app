/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonGoogle;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.importdata.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.io.ExcelSaver;
import net.clementlevallois.nocodeapp.web.front.io.SlidesOperations;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.umigon.model.Categories.Category;
import net.clementlevallois.umigon.model.Document;
import net.clementlevallois.utils.Clock;
import net.clementlevallois.utils.Multiset;
import org.omnifaces.util.Faces;
import org.openide.util.Exceptions;
import org.primefaces.PrimeFaces;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class UmigonBean implements Serializable {

    private Integer progress;
    private List<Document> results;
    private ConcurrentHashMap<Integer, Document> tempResults = new ConcurrentHashMap();
    private String[] sentiments;
    private String selectedLanguage;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String sessionId;
    private List<Document> filteredDocuments;

    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    SingletonGoogle gDrive;

    public UmigonBean() {
    }

    @PostConstruct
    void init() {
        sessionId = Faces.getSessionId();
        sessionBean.setFunction("umigon");
        sessionBean.sendFunctionPageReport();
        String positive_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_positive");
        String negative_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_negative");
        String neutral_tone = sessionBean.getLocaleBundle().getString("general.nouns.sentiment_neutral");

        sentiments = new String[]{positive_tone, negative_tone, neutral_tone};
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
        try {
            Iterator<Document> iterator = results.iterator();
            int percentNegative = 0;
            int percentNeutral = 0;
            int percentPositive = 0;
            int nbNegative = 0;
            int nbNeutral = 0;
            int nbPositive = 0;
            int totalDocs = results.size();
            Multiset<String> positiveTerms = new Multiset();
            Multiset<String> negativeTerms = new Multiset();
            Multiset<String> termsInPositiveDocs = new Multiset();
            Multiset<String> termsInNegativeDocs = new Multiset();
            Multiset<String> emojisInPositiveDocs = new Multiset();
            Multiset<String> emojisInNegativeDocs = new Multiset();
            Multiset<String> hashtagsInPositiveDocs = new Multiset();
            Multiset<String> hashtagsInNegativeDocs = new Multiset();

            while (iterator.hasNext()) {
                Document doc = iterator.next();
                if (doc.isIsNegative()) {
                    nbNegative++;
                    negativeTerms.addAllFromListOrSet(doc.getListNegative());
                    Set<String> diff = new HashSet();
                    termsInNegativeDocs.addAllFromListOrSet(diff);
                    emojisInNegativeDocs.addAllFromListOrSet(doc.getAllEmojis());
                    hashtagsInNegativeDocs.addAllFromListOrSet(doc.getHashtags());
                } else if (doc.isIsPositive()) {
                    nbPositive++;
                    positiveTerms.addAllFromListOrSet(doc.getListPositive());
                    Set<String> diff = new HashSet();
                    termsInPositiveDocs.addAllFromListOrSet(diff);
                    emojisInPositiveDocs.addAllFromListOrSet(doc.getAllEmojis());
                    hashtagsInPositiveDocs.addAllFromListOrSet(doc.getHashtags());

                }
                Set<String> terms = new HashSet(doc.getListNegative());
                negativeTerms.addAllFromListOrSet(terms);
                terms = new HashSet(doc.getListPositive());
                positiveTerms.addAllFromListOrSet(terms);
            }
            percentNegative = (int) Math.round((float) nbNegative / totalDocs * 100.0);
            percentPositive = (int) Math.round((float) nbPositive / totalDocs * 100.0);
            percentNeutral = 100 - (percentNegative + percentPositive);
            nbNeutral = totalDocs - (nbNegative + nbPositive);

//            System.out.println("terms in negative docs:");
//            System.out.println("terms in positive docs:");
//            termsInPositiveDocs.printTopRankedElements(10);
//            termsInNegativeDocs.printTopRankedElements(10);
            List<Map.Entry<String, Integer>> topTermsInNegativeDocs = termsInNegativeDocs.sortDesckeepMostfrequent(termsInNegativeDocs, 10);
            List<Map.Entry<String, Integer>> topTermsInPositiveDocs = termsInPositiveDocs.sortDesckeepMostfrequent(termsInPositiveDocs, 10);
            List<Map.Entry<String, Integer>> topEmojisInNegativeDocs = emojisInNegativeDocs.sortDesckeepMostfrequent(emojisInNegativeDocs, 10);
            List<Map.Entry<String, Integer>> topEmojisInPositiveDocs = emojisInPositiveDocs.sortDesckeepMostfrequent(emojisInPositiveDocs, 10);
            List<Map.Entry<String, Integer>> topHashtagsInNegativeDocs = hashtagsInNegativeDocs.sortDesckeepMostfrequent(hashtagsInNegativeDocs, 10);
            List<Map.Entry<String, Integer>> topHashtagsInPositiveDocs = hashtagsInPositiveDocs.sortDesckeepMostfrequent(hashtagsInPositiveDocs, 10);

            int rows = 11;
            int columns = 4;

            String prezId = gDrive.createNewSlidePresentationWithOnePage(Faces.getSessionId(), SingletonGoogle.TEMP_FOLDER_UMIGON_REPORTS);
            SlidesOperations slidesOps = new SlidesOperations();
            slidesOps.init(gDrive.getGoogleSlides());

            //customize title page
            slidesOps.customizeTitlePage(prezId);

            // add slide with key metrics
            slidesOps.createSlideWithKeyMetricsUmigon("key_metrics", prezId, nbNegative, nbPositive, nbNeutral, percentNegative, percentPositive, percentNeutral, totalDocs, selectedLanguage);

            // insert a table of top ten freq words on the right of the key results
            slidesOps.insertTable("tableFreqTerms", "key_metrics", prezId, rows, columns);
            List<String> freqTermsheaders = List.of("Most frequent terms in positive texts", "count", "Most frequent terms in negative texts", "count");
            slidesOps.addHeaders("tableFreqTerms", prezId, freqTermsheaders);
            slidesOps.insertContentInTable(prezId, "tableFreqTerms", 0, topTermsInPositiveDocs);
            slidesOps.insertContentInTable(prezId, "tableFreqTerms", 2, topTermsInNegativeDocs);

            // add slide and content with top emojis
            slidesOps.createNewSlide("slideFreqEmojis", prezId, "Top emojis");
            slidesOps.insertTable("tableFreqEmojis", "slideFreqEmojis", prezId, rows, columns);
            List<String> freqEmojisheaders = List.of("Most frequent emojis in positive texts", "count", "Most frequent emojis in negative texts", "count");
            slidesOps.addHeaders("tableFreqEmojis", prezId, freqEmojisheaders);
            slidesOps.insertContentInTable(prezId, "tableFreqEmojis", 0, topEmojisInPositiveDocs);
            slidesOps.insertContentInTable(prezId, "tableFreqEmojis", 2, topEmojisInNegativeDocs);

            // add slide and content with top hashtags
            slidesOps.createNewSlide("slideFreqHashtags", prezId, "Top hashtags");
            slidesOps.insertTable("tableFreqHashtags", "slideFreqHashtags", prezId, rows, columns);
            List<String> freqHashtagsheaders = List.of("Most frequent hashtags in positive texts", "count", "Most frequent hashtags in negative texts", "count");
            slidesOps.addHeaders("tableFreqHashtags", prezId, freqHashtagsheaders);
            slidesOps.insertContentInTable(prezId, "tableFreqHashtags", 0, topHashtagsInPositiveDocs);
            slidesOps.insertContentInTable(prezId, "tableFreqHashtags", 2, topHashtagsInNegativeDocs);

            // add slide with notes on methodo
            slidesOps.createNewSlide("notes_methodo", prezId, "Notes on methodology");
            slidesOps.insertTextCommentSlide("notes_methodo", prezId);

            String urlSlideReport = "http://docs.google.com/presentation/d/" + prezId + "/edit?usp=sharing";
//            FacesContext context = FacesContext.getCurrentInstance();
//            context.getExternalContext().redirect();

            PrimeFaces.current().executeScript("window.open('" + urlSlideReport + "');");
//            RequestContext.getCurrentInstance().execute("window.open('reportURL');");

        } catch (IOException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public String runAnalysis() {
        try {
            if (selectedLanguage == null || selectedLanguage.isEmpty()) {
                selectedLanguage = "en";
            }
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            DataFormatConverter dataFormatConverter = new DataFormatConverter();
            Map<Integer, String> mapOfLines = dataFormatConverter.convertToMapOfLines(inputData.getBulkData(), inputData.getDataInSheets(), inputData.getSelectedSheetName(), inputData.getSelectedColumnIndex(), inputData.getHasHeaders());
            int maxRecords = mapOfLines.size();

            results = Arrays.asList(new Document[maxRecords]);

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            try {
                for (Map.Entry<Integer, String> entry : mapOfLines.entrySet()) {
                    Document doc = new Document();
                    String id = String.valueOf(entry.getKey());
                    doc.setText(entry.getValue());
                    doc.setId(id);
                    doc.setSentiment(Category._10);

                    URI uri = new URI("http://localhost:7002/api/sentimentForAText/bytes/" + selectedLanguage + "?id=" + doc.getId() + "&text=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));

                    request = HttpRequest.newBuilder()
                            .uri(uri)
                            .build();

                    CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                        byte[] body = resp.body();
                        try (
                                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
                            Document docReturn = (Document) ois.readObject();
                            tempResults.put(Integer.valueOf(docReturn.getId()), docReturn);
                        } catch (IOException | ClassNotFoundException ex) {
                            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    );
                    futures.add(future);
                    // this is because we need to slow down a bit the requests to DeepL - sending too many thros a
                    // java.util.concurrent.CompletionException: java.io.IOException: too many concurrent streams
                    Thread.sleep(2);
                }
                this.progress = 40;
                service.create(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

                CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
                combinedFuture.join();

            } catch (URISyntaxException exception) {
                System.out.println("error!!");
            } catch (UnsupportedEncodingException ex) {
                System.out.println("encoding ex");
            }
            for (Map.Entry<Integer, Document> entry : tempResults.entrySet()) {
                results.set(entry.getKey(), entry.getValue());
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
        SendReport sender = new SendReport();
        sender.initErrorReport(docFound.getText() + " - should not be " + docFound.getSentiment().toString());
        sender.start();
        return "";
    }

    public void dummy() {
    }

    public StreamedContent getFileToSave() {
        return ExcelSaver.exportUmigon(results);
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
