package net.clementlevallois.nocodeapp.web.front.flows.base;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

@Named
@RequestScoped
public class FlowService {

    @Inject
    private SessionBean sessionBean;

    public void logAndSetState(String jobId, String stateName, String userMessage, String logMessage, Throwable exception) {
        Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, logMessage, exception);

        FacesContext facesContext = FacesContext.getCurrentInstance();
//        facesContext.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", failedState.errorMessage()));
        facesContext.getExternalContext().getFlash().setKeepMessages(true);

//        sessionBean.setCurrentFlowState(failedState);
        try {
            String redirectUrl = facesContext.getExternalContext().getApplicationContextPath() + "/index.xhtml?faces-redirect=true";
            facesContext.getExternalContext().redirect(redirectUrl);
        } catch (IOException e) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Failed to redirect.", e);
        }
    }
}
