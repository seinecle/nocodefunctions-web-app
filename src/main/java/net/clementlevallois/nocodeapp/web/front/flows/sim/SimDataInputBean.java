/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class SimDataInputBean implements Serializable {

    private String jobId;
    private List<SheetModel> dataInSheets;
    private Boolean hasHeaders = false;
    private String selectedColumnIndex;
    private SimState.AwaitingParameters awaitingParameters;

    private static final Logger LOG = Logger.getLogger(SimDataInputBean.class.getName());

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        this.jobId = null;
        this.awaitingParameters = new SimState.AwaitingParameters(
                null, // jobId to be set later
                1, // minSharedTargets default
                "Sheet1", // placeholder for sheet name
                null // sourceColIndex to be provided later
        );
        sessionBean.setFlowState(awaitingParameters);
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        SimDataSource dataSource = new SimDataSource.FileUpload(event.getFile());
        processSimDataSource(dataSource);
    }

    private void processSimDataSource(SimDataSource dataSource) {
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID().toString().substring(0, 10);
        }

        SimState currentState = sessionBean.getFlowState();

        if (!(currentState instanceof SimState.AwaitingParameters)) {
            // This case should ideally not happen if the flow is followed correctly,
            // but it's good practice to handle it.
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Invalid State", "The application is in an invalid state.");
            sessionBean.setFlowState(new SimState.FlowFailed(jobId, currentState, "invalid state"));
            return;
        }

        awaitingParameters = (SimState.AwaitingParameters) currentState;
        awaitingParameters = awaitingParameters.withJobId(this.jobId);
        sessionBean.setFlowState(awaitingParameters);

        sessionBean.sendFunctionPageReport(Globals.Names.SIM.name());

        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "unable to create directories for job " + jobId, ex);
            sessionBean.setFlowState(new SimState.FlowFailed(jobId, awaitingParameters, "unable to create directories"));
        }

        ImportersService.PreparationResult result = switch (dataSource) {
            case SimDataSource.FileUpload(UploadedFile file) ->
                importersService.handleFileUpload(List.of(file), jobId, Globals.Names.SIM);
        };

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
            sessionBean.setFlowState(new SimState.FlowFailed(jobId, awaitingParameters, "data prep failed"));
        } else {
            switch (dataSource) {
                case SimDataSource.FileUpload(UploadedFile file) -> {
                    sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", file.getFileName() + " has been added to your dataset.");
                    dataInSheets = populateDataInSheetsVariable();
                }
            }
        }
    }

    private List<SheetModel> populateDataInSheetsVariable() {

        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path sheetsModelFile = globals.getDataSheetPath(jobId);
        if (Files.exists(sheetsModelFile)) {
            try {
                byte[] fileBytes = Files.readAllBytes(sheetsModelFile);

                try (ByteArrayInputStream bis = new ByteArrayInputStream(fileBytes); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    return (List<SheetModel>) ois.readObject();
                } catch (IOException | ClassNotFoundException ex) {
                    System.getLogger(SimDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    sessionBean.setFlowState(new SimState.FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                    return Collections.EMPTY_LIST;
                }
            } catch (IOException ex) {
                System.getLogger(SimDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                sessionBean.setFlowState(new SimState.FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                return Collections.EMPTY_LIST;
            }
        } else {
            sessionBean.setFlowState(new SimState.FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
            return Collections.EMPTY_LIST;
        }
    }

    public String proceedToParameters(String sourceColIndex, String sheetName) {
        if (jobId == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first upload a file.");
            sessionBean.setFlowState(new SimState.FlowFailed(jobId, awaitingParameters, "job Id was null"));
            return null;
        }

        if (sessionBean.getFlowState() instanceof SimState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(this.jobId));
            sessionBean.setFlowState(p.withSheetName(sheetName));
            sessionBean.setFlowState(p.withSourceColIndex(sourceColIndex));
            return "similarities-parameters.html?faces-redirect=true";
        } else {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first upload a file.");
            sessionBean.setFlowState(new SimState.FlowFailed(jobId, sessionBean.getFlowState(), "sim state was not awaiting params"));
            return null;
        }
    }

    public List<SheetModel> getDataInSheets() {
        if (dataInSheets == null) {
            return Collections.EMPTY_LIST;
        }
        for (SheetModel sheet : dataInSheets) {
            sheet.setHasHeaders(hasHeaders);
        }
        return dataInSheets;
    }

    public void setDataInSheets(List<SheetModel> dataInSheets) {
        this.dataInSheets = dataInSheets;
    }

    public Boolean getHasHeaders() {
        return hasHeaders;
    }

    public void setHasHeaders(Boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
    }

    public String getSelectedColumnIndex() {
        return selectedColumnIndex;
    }

    public void setSelectedColumnIndex(String selectedColumnIndex) {
        this.selectedColumnIndex = selectedColumnIndex;
    }

}
