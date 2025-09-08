/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

@Named
@ViewScoped
public class CoocAnalysisBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CoocAnalysisBean.class.getName());

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private CoocService coocService;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() == null) {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("/cooc/cooc-import.xhtml?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirect failed in cooc analysis bean init", ex);
            }
        }
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof CoocState.AwaitingParameters params) {
            sessionBean.setFlowState(new CoocState.Processing(params.jobId(), params, 0));
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            CoocState processingState = coocService.callCoocMicroService(params);
            if (processingState != null) {
                sessionBean.setFlowState(processingState);
            } else {
                sessionBean.setFlowState(new CoocState.FlowFailed(params.jobId(), params, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof CoocState.Processing processingState) {
            sessionBean.setFlowState(coocService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof CoocState.ResultsReady) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/cooc/cooc-results.html");
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Redirect to cooc-results.html failed", ex);
                }
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getFlowState() instanceof CoocState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getFlowState() instanceof CoocState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getFlowState()) {
            case CoocState.AwaitingParameters ap ->
                0;
            case CoocState.Processing p ->
                p.progress();
            case CoocState.ResultsReady rr ->
                100;
            case CoocState.FlowFailed ff ->
                0;
            default ->
                0;
        };
    }
}