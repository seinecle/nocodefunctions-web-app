/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

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
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class CoocDataInputBean implements Serializable {

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    public CoocDataInputBean() {
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        sessionBean.setFlowState(new CoocState.AwaitingData(null // jobId to be set later
        ));
        CoocDataSource dataSource = new CoocDataSource.FileUpload(List.of(event.getFile()));
        processCoocDataSource(dataSource);
    }

    private boolean processCoocDataSource(CoocDataSource dataSource) {
        if (!(sessionBean.getFlowState() instanceof CoocState.AwaitingData)) {
            logBean.addOneNotificationFromString("a problem occured in method processCoocDataSource");
            return false;
        }

        String jobId = UUID.randomUUID().toString().substring(0, 10);
        sessionBean.setFlowState(new CoocState.AwaitingData(jobId));
        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            throw new NocodeApplicationException("Error in cooc  method: Failed to create a directory for jobId: " + jobId, ex);
        }

        sessionBean.sendFunctionPageReport(Globals.Names.COOC.name());

        ImportersService.PreparationResult result;
        if (dataSource instanceof CoocDataSource.FileUpload fileUpload) {
            result = importersService.handleFileUpload(fileUpload.files(), jobId, Globals.Names.COOC);
        } else {
            logBean.addOneNotificationFromString("invalid type of data source");
            return false;
        }

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            logBean.addOneNotificationFromString("a problem occured when dealing with the data provided");
        } else {
            switch (dataSource) {
                case CoocDataSource.FileUpload(List<UploadedFile> files) -> {
                    for (var file : files) {
                        logBean.addOneNotificationFromString(file.getFileName() + " has been processed.");
                    }
                    sessionBean.setFlowState(new CoocState.AwaitingParameters(jobId, files.get(0).getFileName(), 1, false));
                }
            }
        }
        return true;
    }

    public boolean isDataReady() {
        return sessionBean.getFlowState() instanceof CoocState.AwaitingParameters;
    }

    public String getFileName() {
        if ((sessionBean.getFlowState() instanceof CoocState.AwaitingParameters(String jobId, String fileName, int minSharedTargets, boolean hasHeaders))) {
            return fileName;
        } else {
            return "";
        }
    }
}
