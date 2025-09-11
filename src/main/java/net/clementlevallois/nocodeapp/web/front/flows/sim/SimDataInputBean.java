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
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class SimDataInputBean implements Serializable {

    private List<SheetModel> dataInSheets;
    private Boolean hasHeaders = false;
    private String selectedColumnIndex;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        SimState.AwaitingParameters awaitingParameters = new SimState.AwaitingParameters(
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

        if (sessionBean.getFlowState() instanceof SimState.AwaitingParameters awaitingParameters) {
            String jobId = UUID.randomUUID().toString().substring(0, 10);
            awaitingParameters = awaitingParameters.withJobId(jobId);
            sessionBean.setFlowState(awaitingParameters);
            sessionBean.sendFunctionPageReport(Globals.Names.SIM.name());

            Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
            try {
                Files.createDirectories(jobDirectory);
            } catch (IOException ex) {
                throw new NocodeApplicationException("Error in getDocumentDimensions method: Failed to read image dimensions for jobId: " + jobId, ex);
            }

            ImportersService.PreparationResult result;
            if (dataSource instanceof SimDataSource.FileUpload fileUpload) {
                result = importersService.handleFileUpload(List.of(fileUpload.file()), jobId, Globals.Names.SIM);
            } else {
                throw new IllegalArgumentException("Unsupported data source type");
            }

            if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
                sessionBean.setFlowState(new FlowFailed(jobId, awaitingParameters, "data prep failed"));
            } else {
                switch (dataSource) {
                    case SimDataSource.FileUpload(UploadedFile file) -> {
                        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", file.getFileName() + " has been added to your dataset.");
                        dataInSheets = populateDataInSheetsVariable();
                    }
                }
            }
        } else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    private List<SheetModel> populateDataInSheetsVariable() {
        if (sessionBean.getFlowState() instanceof SimState.AwaitingParameters awaitingParameters) {
            String jobId = awaitingParameters.jobId();
            Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
            Path sheetsModelFile = globals.getDataSheetPath(jobId);
            if (Files.exists(sheetsModelFile)) {
                try {
                    byte[] fileBytes = Files.readAllBytes(sheetsModelFile);

                    try (ByteArrayInputStream bis = new ByteArrayInputStream(fileBytes); ObjectInputStream ois = new ObjectInputStream(bis)) {
                        return (List<SheetModel>) ois.readObject();
                    } catch (IOException | ClassNotFoundException ex) {
                        System.getLogger(SimDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                        sessionBean.setFlowState(new FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                        return Collections.EMPTY_LIST;
                    }
                } catch (IOException ex) {
                    System.getLogger(SimDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    sessionBean.setFlowState(new FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                    return Collections.EMPTY_LIST;
                }
            } else {
                sessionBean.setFlowState(new FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                return Collections.EMPTY_LIST;
            }
        }else{
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public String proceedToParameters(String sourceColIndex, String sheetName) {
        if (sessionBean.getFlowState() instanceof SimState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(p.jobId()));
            sessionBean.setFlowState(p.withSheetName(sheetName));
            sessionBean.setFlowState(p.withSourceColIndex(sourceColIndex));
            return "similarities-parameters.html?faces-redirect=true";
        } else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
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
