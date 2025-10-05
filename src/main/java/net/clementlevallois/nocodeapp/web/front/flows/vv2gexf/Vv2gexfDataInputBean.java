package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import org.primefaces.event.FileUploadEvent;

@Named
@RequestScoped
public class Vv2gexfDataInputBean implements Serializable {

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public void handleFileUpload(FileUploadEvent event) {
        try {
            var file = event.getFile();
            if (file == null || file.getFileName() == null || file.getFileName().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Upload", "No file selected");
                return;
            }
            if (!file.getFileName().toLowerCase().endsWith(".json")) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Format", "Expected file format is a .json VosViewer file");
                return;
            }

            var jobId = UUID.randomUUID().toString().substring(0, 10);

            Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
            try {
                Files.createDirectories(jobDirectory);
            } catch (IOException ex) {
                throw new NocodeApplicationException("Error in getDocumentDimensions method: Failed to read image dimensions for jobId: " + jobId, ex);
            }

            Files.write(jobDirectory.resolve(jobId), file.getInputStream().readAllBytes());

            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "success", file.getFileName() + " uploaded");
            sessionBean.setFlowState(new Vv2gexfState.AwaitingFile(jobId, file.getFileName()));

            FacesUtils.redirectTo("vv2gexf-analyze.html");

        } catch (IOException ex) {
            throw new NocodeApplicationException("Error during JSON VOSviewer file upload", ex);
        }
    }
}
