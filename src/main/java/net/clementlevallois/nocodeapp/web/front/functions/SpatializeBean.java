package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import java.time.Duration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import org.primefaces.event.FileUploadEvent;
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
public class SpatializeBean implements Serializable {

    private UploadedFile uploadedFile;
    private byte[] uploadedFileAsByteArray;
    private boolean displayDownloadButton = false;

    private byte[] gexfAsByteArrayResult;

    private StreamedContent fileToSave;

    private Integer progress = 0;
    private float progressFloat = 0f;
    private Integer durationInSeconds = 20;

    private final Properties privateProperties;

    @Inject
    SessionBean sessionBean;

    public SpatializeBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("spatialize");
        privateProperties = SingletonBean.getPrivateProperties();
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
            displayDownloadButton = false;
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, uploadedFile.getFileName() + " " + is_uploaded + ".");
        }
    }

    public String handleFileUpload(FileUploadEvent event) {
        progress = 0;
        progressFloat = 0f;
        sessionBean.sendFunctionPageReport();
        String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
        String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, event.getFile().getFileName() + " " + is_uploaded + ".");
        uploadedFile = event.getFile();
        try {
            uploadedFileAsByteArray = uploadedFile.getInputStream().readAllBytes();
        } catch (IOException ex) {
            System.out.println("ex:" + ex.getMessage());
        }
        return "";
    }

    public StreamedContent getFileToSave() throws FileNotFoundException {
        progress = 0;
        progressFloat = 0f;
        if (gexfAsByteArrayResult == null) {
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            String details = sessionBean.getLocaleBundle().getString("general.message.data_not_found");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, details);
            return null;
        }
        StreamedContent fileStream = null;
        InputStream inputStreamToSave = new ByteArrayInputStream(gexfAsByteArrayResult);
        fileStream = DefaultStreamedContent.builder()
                .name("network_spatialized.gexf")
                .contentType("application/gexf+xml")
                .stream(() -> inputStreamToSave)
                .build();

        return fileStream;

    }

    public void layout() {
        if (uploadedFileAsByteArray == null) {
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            String details = sessionBean.getLocaleBundle().getString("general.message.data_not_found");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, details);
            return;
        }
        HttpRequest request;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(120)).build();
        Set<CompletableFuture> futures = new HashSet();
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(uploadedFileAsByteArray);
        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_api_port")))
                .withHost("localhost")
                .withPath("api/spatialization")
                .addParameter("durationInSeconds", String.valueOf(durationInSeconds))
                .toUri();
        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();
        Runnable progressBarIncrement = () -> {
            while (gexfAsByteArrayResult == null) {
                // 90 not 100 to make sure we count down a bit more slowly than the ideal value, just in case
                float increment = ((float) 90 / (float) durationInSeconds);
                progressFloat += increment;
                progress = Math.round(progressFloat);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ex) {
                    Logger.getLogger(SpatializeBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        };
        Thread t = new Thread(progressBarIncrement);
        t.start();
        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(
                        resp -> {
                            if (resp.statusCode() == 200) {
                                gexfAsByteArrayResult = resp.body();
                                displayDownloadButton = true;
                                progress = 100;
                                progressFloat = 100f;
                            } else {
                                gexfAsByteArrayResult = null;
                            }
                        }
                );
        futures.add(future);
        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();
        if (gexfAsByteArrayResult == null) {
            System.out.println("gexfAsByteArray returned by the API was not a 200 code");
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, error);
        }

    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getDurationInSeconds() {
        return durationInSeconds;
    }

    public void setDurationInSeconds(Integer durationInSeconds) {
        this.durationInSeconds = durationInSeconds;
    }

    public boolean isDisplayDownloadButton() {
        return displayDownloadButton;
    }

    public void setDisplayDownloadButton(boolean displayDownloadButton) {
        this.displayDownloadButton = displayDownloadButton;
    }

}
