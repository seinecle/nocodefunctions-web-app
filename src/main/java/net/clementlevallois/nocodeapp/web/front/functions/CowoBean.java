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
import jakarta.json.JsonWriter;
import jakarta.servlet.annotation.MultipartConfig;
import java.util.Properties;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.utils.TextCleaningOps;
//import org.omnifaces.util.Faces;
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

public class CowoBean implements Serializable {

    private Integer progress = 0;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private String selectedLanguage;
    private String nodesAsJson;
    private String edgesAsJson;
    private int minFreqNode = 1000_000;
    private int maxFreqNode = 0;
    private int minTermFreq = 2;
    private int maxNGram = 4;
    private int minCoocFreqInt = 2;
    private boolean removeNonAsciiCharacters = false;
    private String typeCorrection = "none";
    private boolean scientificCorpus;
    private boolean okToShareStopwords = false;
    private boolean replaceStopwords = false;
    private boolean lemmatize = true;
    private boolean usePMI = false;
    private UploadedFile fileUserStopwords;
    private String vosviewerJsonFileName;
    private String graphAsJsonVosViewer;
    private String gexf;
    private Boolean shareVVPublicly;
    private String gephistoGexfFileName;
    private Boolean shareGephistoPublicly;
    private Integer minCharNumber = 4;
    private Map<Integer, String> mapOfLines;
    private final Properties privateProperties;

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
        privateProperties = SingletonBean.getPrivateProperties();
    }

    public void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        try {
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(severity, summary, detail));
        } catch (NullPointerException e) {
            System.out.println("FacesContext.getCurrentInstance was null. Detail: " + detail);
        }
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
        HttpClient client = null;
        Set<CompletableFuture> futures = null;

        if (usePMI) {
            typeCorrection = "pmi";
        }

        try {
            sessionBean.sendFunctionPageReport();
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
            progress = 10;
            service.create(sessionBean.getLocaleBundle().getString("general.message.removing_punctuation_and_cleaning"));

            mapOfLines = TextCleaningOps.doAllCleaningOps(mapOfLines, removeNonAsciiCharacters);
            mapOfLines = TextCleaningOps.putInLowerCase(mapOfLines);

            progress = 15;

            service.create(sessionBean.getLocaleBundle().getString("general.message.finding_key_terms"));
            progress = 20;
            Runnable incrementProgress = () -> {
                progress = progress + 1;
            };
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(incrementProgress, 0, 2, TimeUnit.SECONDS);

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

            futures = new HashSet();

            overallObject.add("lines", linesBuilder);
            overallObject.add("lang", selectedLanguage);
            overallObject.add("userSuppliedStopwords", userSuppliedStopwordsBuilder);
            overallObject.add("minCharNumber", minCharNumber);
            overallObject.add("replaceStopwords", replaceStopwords);
            overallObject.add("isScientificCorpus", scientificCorpus);
            overallObject.add("lemmatize", lemmatize);
            overallObject.add("removeAccents", removeNonAsciiCharacters);
            overallObject.add("minCoocFreq", minCoocFreqInt);
            overallObject.add("minTermFreq", minTermFreq);
            overallObject.add("maxNGram", maxNGram);
            overallObject.add("typeCorrection", typeCorrection);

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
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                    .withPath("api/cowo")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();
            client = HttpClient.newHttpClient();
            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                if (resp.statusCode() == 200) {
                    gexf = new String(body, StandardCharsets.UTF_8);
                } else {
                    System.out.println("cowo returned by the API was not a 200 code");
                    String errorMessage = new String(body, StandardCharsets.UTF_8);
                    service.create(errorMessage);
                    addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                }
            }
            );
            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();
            executor.shutdown();
            service.create(sessionBean.getLocaleBundle().getString("general.message.last_ops_creating_network"));
            progress = 60;

            if (gexf == null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
                renderSeeResultsButton = true;
                runButtonDisabled = true;
                return "";
            }
            futures = new HashSet();

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
                if (resp.statusCode() == 200) {
                    byte[] body = resp.body();
                    String jsonResult = new String(body, StandardCharsets.UTF_8);
                    JsonObject jsonObject = Json.createReader(new StringReader(jsonResult)).readObject();
                    nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
                    edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
                } else {
                    System.out.println("top nodes returned by the API was not a 200 code");
                    String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
                    service.create(error);
                    addMessage(FacesMessage.SEVERITY_WARN, "💔", error);
                }
            }
            );
            futures.add(future);

            combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

        } catch (IOException | NumberFormatException ex) {
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
        if (gexf == null) {
            System.out.println("gexf was null in cowo function");
            addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
            return new DefaultStreamedContent();
        }
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

    public int getMaxNGram() {
        return maxNGram;
    }

    public void setMaxNGram(int maxNGram) {
        this.maxNGram = maxNGram;
    }

    public int getMinTermFreq() {
        return minTermFreq;
    }

    public void setMinTermFreq(int minTermFreq) {
        this.minTermFreq = minTermFreq;
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

    public boolean isRemoveNonAsciiCharacters() {
        return removeNonAsciiCharacters;
    }

    public void setRemoveNonAsciiCharacters(boolean removeNonAsciiCharacters) {
        this.removeNonAsciiCharacters = removeNonAsciiCharacters;
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
            String message = fileUserStopwords.getFileName() + " " + is_uploaded + ".";
            addMessage(FacesMessage.SEVERITY_INFO, success, message);
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

    public boolean isUsePMI() {
        return usePMI;
    }

    public void setUsePMI(boolean usePMI) {
        this.usePMI = usePMI;
    }

    public boolean isLemmatize() {
        return lemmatize;
    }

    public void setLemmatize(boolean lemmatize) {
        this.lemmatize = lemmatize;
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
        String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(true);
        vosviewerJsonFileName = "vosviewer_" + sessionId.substring(0, 20) + ".json";
        if (shareVVPublicly) {
            subfolder = "public/";
        } else {
            subfolder = "private/";
        }
        path = path + subfolder;

        if (RemoteLocal.isLocal()) {
            path = SingletonBean.getRootOfProject() + "user_created_files";
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
        if (gexf == null) {
            System.out.println("gm object was null so gotoGephisto method exited");
            return;
        }
        byte[] readAllBytes = gexf.getBytes();
        InputStream inputStreamToSave = new ByteArrayInputStream(readAllBytes);

        String path = RemoteLocal.isLocal() ? "" : "gephisto/data/";
        String subfolder;
        String sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(true);
        gephistoGexfFileName = "gephisto_" + sessionId.substring(0, 20) + ".gexf";

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

    public List<Locale> getAvailable() {
        List<Locale> available = new ArrayList();
        String[] availableStopwordLists = new String[]{"ar", "bg", "ca", "da", "de", "el", "en", "es", "fr", "it", "ja", "nl", "no", "pl", "pt", "ro", "ru", "tr"};
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
