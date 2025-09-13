package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.UUID;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import org.primefaces.event.FileUploadEvent;

@Named
@RequestScoped
public class Gexf2VvDataInputBean implements Serializable {

    @Inject
    SessionBean sessionBean;
    @Inject
    ApplicationPropertiesBean applicationProperties;

    public void handleFileUpload(FileUploadEvent event) {
        try {
            var file = event.getFile();
            var jobId = UUID.randomUUID().toString().substring(0, 10);
            var tempRoot = applicationProperties.getTempFolderFullPath();
            Files.write(tempRoot.resolve(file.getFileName()), file.getInputStream().readAllBytes());
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", file.getFileName() + " uploaded.");
            sessionBean.setFlowState(new Gexf2VvState.AwaitingParameters(
                    jobId, "item_name", "link_name", "link strength name", false
            ));
        } catch (IOException ex) {
            throw new NocodeApplicationException("Handling of uploaded gexf file failed", ex);
        }
    }

    public boolean isDataReady() {
        return sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters;
    }

    public void proceedToParameters() {
        if (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(p.jobId()));
            FacesUtils.redirectTo("gexf2vv-parameters.html");
        } else {
            throw new IllegalStateException("Wrong state: " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

}
