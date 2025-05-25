package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import java.nio.charset.StandardCharsets;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;


@Named
@SessionScoped
@MultipartConfig
public class SpatializeBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(SpatializeBean.class.getName());

    private UploadedFile uploadedFile;
    private byte[] uploadedFileAsByteArray;
    private boolean displayDownloadButton = false;

    private byte[] gexfAsByteArrayResult;

    private StreamedContent fileToSave;

    private Integer progress = 0;
    private Integer durationInSeconds = 20;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    public SpatializeBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("spatialize");
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
        displayDownloadButton = false; // Reset download button state
        gexfAsByteArrayResult = null; // Clear previous results

        sessionBean.sendFunctionPageReport();
        String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
        String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, event.getFile().getFileName() + " " + is_uploaded + ".");
        uploadedFile = event.getFile();
        try {
            uploadedFileAsByteArray = uploadedFile.getInputStream().readAllBytes();
             LOG.log(Level.INFO, "Uploaded file {0} read into byte array.", uploadedFile.getFileName());
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading uploaded file into byte array", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Upload Error", "Could not read uploaded file: " + ex.getMessage());
            uploadedFileAsByteArray = null; // Ensure byte array is null on error
        }
        return "";
    }

    public StreamedContent getFileToSave() {
        if (gexfAsByteArrayResult == null) {
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            String details = sessionBean.getLocaleBundle().getString("general.message.data_not_found");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, details);
            return new DefaultStreamedContent();
        }

        InputStream inputStreamToSave = new ByteArrayInputStream(gexfAsByteArrayResult);
        return DefaultStreamedContent.builder()
                .name("network_spatialized.gexf")
                .contentType("application/gexf+xml")
                .stream(() -> inputStreamToSave)
                .build();
    }

    public void layout() {
        if (uploadedFileAsByteArray == null) {
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            String details = sessionBean.getLocaleBundle().getString("general.message.data_not_found");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, details);
            return;
        }

        progress = 0; // Reset progress
        displayDownloadButton = false; // Hide download button
        gexfAsByteArrayResult = null; // Clear previous results

        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Starting", "Starting spatialization layout...");

        // Use MicroserviceHttpClient to call the spatialization endpoint
        microserviceClient.api().post("/api/spatialization")
            .withByteArrayPayload(uploadedFileAsByteArray) // Send the GEXF file bytes as payload
            .addQueryParameter("durationInSeconds", String.valueOf(durationInSeconds)) // Add duration as query parameter
            .sendAsync(HttpResponse.BodyHandlers.ofByteArray()) // Send async, expect byte array (GEXF result)
            .thenAccept(resp -> {
                // This block runs on HttpClient's thread pool
                if (resp.statusCode() == 200) {
                    gexfAsByteArrayResult = resp.body();
                    displayDownloadButton = true;
                    progress = 100; // Set progress to 100 on success
                    sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", "Spatialization complete. Download ready.");
                    LOG.info("Spatialization successful.");
                } else {
                    gexfAsByteArrayResult = null; // Ensure null on error
                    displayDownloadButton = false;
                    progress = 0; // Reset progress on error
                    String errorBody = new String(resp.body(), StandardCharsets.UTF_8);
                    LOG.log(Level.SEVERE, "Spatialization microservice call failed. Status: {0}, Body: {1}", new Object[]{resp.statusCode(), errorBody});
                    sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Spatialization Failed", "Microservice error: Status " + resp.statusCode() + ", " + errorBody);
                }
                 // Update UI components (button, progress) - Needs to happen on JSF thread
                 // Using PrimeFaces RequestContext or similar if not relying on polling
                 // For simplicity here, assuming sessionBean.addMessage triggers UI update or using p:poll
            })
            .exceptionally(exception -> {
                // This block runs on HttpClient's thread pool if an exception occurs
                gexfAsByteArrayResult = null; // Ensure null on error
                displayDownloadButton = false;
                progress = 0; // Reset progress on error
                LOG.log(Level.SEVERE, "Exception during async spatialization call", exception);
                String errorMessage = "Communication error with spatialization service: " + exception.getMessage();
                 if (exception.getCause() instanceof MicroserviceCallException) {
                     MicroserviceCallException msce = (MicroserviceCallException) exception.getCause();
                     errorMessage = "Communication error: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
                 }
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Spatialization Failed", errorMessage);
                return null;
            });

        // The method returns immediately after launching the async call.
        // Progress bar update and button state changes need to be handled by the UI
        // polling the bean properties (progress, displayDownloadButton).
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public Integer getProgress() {
        // This method is polled by the UI to update the progress bar
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
