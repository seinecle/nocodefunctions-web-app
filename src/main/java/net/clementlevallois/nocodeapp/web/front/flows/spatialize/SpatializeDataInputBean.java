/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.spatialize;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class SpatializeDataInputBean implements Serializable {

    private String jobId;
    private String uploadedFileName;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        this.jobId = null;
        this.uploadedFileName = null;
        SpatializeState.AwaitingParameters awaitingParameters = new SpatializeState.AwaitingParameters(
                null, // jobId to be set later
                10 // durationInSecond default
        );
        sessionBean.setFlowState(awaitingParameters);
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        SpatializeDataSource dataSource = new SpatializeDataSource.FileUpload(event.getFile());
        uploadedFileName = event.getFile().getFileName();
        processSpatializeDataSource(dataSource);
    }

    private void processSpatializeDataSource(SpatializeDataSource dataSource) {

        if (this.jobId == null) {
            this.jobId = UUID.randomUUID().toString().substring(0, 10);
        }

        sessionBean.sendFunctionPageReport(Globals.Names.SPATIALIZE_FORCE_ATLAS.name());

        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            throw new NocodeApplicationException("An IO error occurred", ex);
        }

        ImportersService.PreparationResult result;
        if (dataSource instanceof SpatializeDataSource.FileUpload fileUpload) {
            result = importersService.handleFileUpload(List.of(fileUpload.file()), jobId, Globals.Names.SPATIALIZE_FORCE_ATLAS);
        } else {
            throw new IllegalArgumentException("Unsupported data source type");
        }

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
        } else {
            switch (dataSource) {
                case SpatializeDataSource.FileUpload(UploadedFile file) ->
                    sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", file.getFileName() + " has been added to your dataset.");
            }
        }
    }

    public String proceedToParameters() {
        if (jobId == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first upload a file.");
            return null;
        }

        if (sessionBean.getFlowState() instanceof SpatializeState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(this.jobId));
        }

        return "spatialize-analyze.xhtml?faces-redirect=true";
    }

    public boolean isDataReady() {
        return this.jobId != null;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getUploadedFileName() {
        return uploadedFileName;
    }
}
