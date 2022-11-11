/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.servlet.annotation.MultipartConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;
import net.clementlevallois.functions.model.Prediction;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig
public class LinkPredictionBean implements Serializable {

    private UploadedFile uploadedFile;
    private InputStream is;
    private String uploadButtonMessage;
    private boolean renderGephiWarning = true;
    private int nbPredictions = 1;
    private JsonObject jsonObjectReturned;
    private String augmentedGexf;
    private List<Prediction> topPredictions;
    private Prediction selectedLink;

    private StreamedContent fileToSave;

    private boolean success = false;

    @Inject
    SessionBean sessionBean;

    public LinkPredictionBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("linkprediction");
        sessionBean.sendFunctionPageReport();
    }

    @PostConstruct
    void init() {
        uploadButtonMessage = sessionBean.getLocaleBundle().getString("general.message.choose_gexf_file");
    }

    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/index?faces-redirect=true";
    }

    public UploadedFile getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(UploadedFile uploadedFile) {
        this.uploadedFile = uploadedFile;
    }

    public void upload() {
        if (uploadedFile != null) {
            String successMsg = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            FacesMessage message = new FacesMessage(successMsg, uploadedFile.getFileName() + " " + is_uploaded + ".");
            FacesContext.getCurrentInstance().addMessage(null, message);
        }
    }

    public String handleFileUpload(FileUploadEvent event) throws IOException, URISyntaxException {
        success = false;
        System.out.println("we are in handleFileUpload");
        String successMsg = sessionBean.getLocaleBundle().getString("general.nouns.success");
        String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
        FacesMessage message = new FacesMessage(successMsg, event.getFile().getFileName() + " " + is_uploaded + ".");
        FacesContext.getCurrentInstance().addMessage(null, message);
        uploadedFile = event.getFile();
        System.out.println("file: " + uploadedFile.getFileName());
        try {
            is = uploadedFile.getInputStream();
        } catch (IOException ex) {
            System.out.println("ex:" + ex.getMessage());
        }
        showPredictions();
        return "";
    }

    public void showPredictions() throws IOException, URISyntaxException {
        if (uploadedFile == null) {
            System.out.println("no file found for link prediction");
            return;
        }
        String uniqueId = UUID.randomUUID().toString().substring(0, 5);

        System.out.println("file: " + uploadedFile.getFileName());
        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());

        URI uri = new URI("http://localhost:7002/api/linkprediction/");

        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
            byte[] body = resp.body();
            String jsonResultsAsString = new String(body, StandardCharsets.UTF_8);
            try ( JsonReader reader = Json.createReader(new StringReader(jsonResultsAsString))) {
                jsonObjectReturned = reader.readObject();
            }

        }
        );
        futures.add(future);

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();

        JsonObject predictions = jsonObjectReturned.getJsonObject("predictions");
        augmentedGexf = jsonObjectReturned.getString("gexf augmented");

        List<String> orderedListOfPredictions = predictions.keySet().stream().sorted().collect(Collectors.toList());

        for (String predictionKey : orderedListOfPredictions) {
            JsonObject predictionJson = predictions.getJsonObject(predictionKey);
            Prediction prediction = new Prediction(
                    predictionJson.getString("source node id"),
                    predictionJson.getString("source node label"),
                    predictionJson.getInt("source node degree"),
                    predictionJson.getString("target node id"),
                    predictionJson.getString("target node label"),
                    predictionJson.getInt("target node degree"),
                    predictionJson.getInt("prediction value")
            );
            topPredictions.add(prediction);

        }

//        predictor = new LinkPredictionController();
//        predictor.runPrediction(is, nbPredictions, uniqueId);
//        topPredictions = predictor.getTopPredictions();
    }

    public int getNbPredictions() {
        return nbPredictions;
    }

    public void setNbPredictions(int nbPredictions) {
        this.nbPredictions = nbPredictions;
    }

    public String getUploadButtonMessage() {
        return uploadButtonMessage;
    }

    public void setUploadButtonMessage(String uploadButtonMessage) {
        this.uploadButtonMessage = uploadButtonMessage;
    }

    public boolean isRenderGephiWarning() {
        return renderGephiWarning;
    }

    public void setRenderGephiWarning(boolean renderGephiWarning) {
        this.renderGephiWarning = renderGephiWarning;
    }

    public StreamedContent getFileToSave() throws FileNotFoundException {
        success = true;
        if (is == null) {
            System.out.println("no file found for link prediction");
            return null;
        }
        return GEXFSaver.exportGexfAsStreamedFile(augmentedGexf, "initial_graph_augmented_with_predicted_links");
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public List<Prediction> getTopPredictions() {
        return topPredictions;
    }

    public void setTopPredictions(List<Prediction> topPredictions) {
        this.topPredictions = topPredictions;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Prediction getSelectedLink() {
        return selectedLink;
    }

    public void setSelectedLink(Prediction selectedLink) {
        this.selectedLink = selectedLink;
    }

}
