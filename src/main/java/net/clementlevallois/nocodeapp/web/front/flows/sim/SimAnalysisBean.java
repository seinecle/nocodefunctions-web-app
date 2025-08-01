/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

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
public class SimAnalysisBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(SimAnalysisBean.class.getName());

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private SimService simService;

    @PostConstruct
    public void init() {
        if (sessionBean.getSimState() == null) {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("sim-import.html?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirect failed in sim analysis bean init", ex);
            }
        }
    }

    public void runAnalysis() {
        if (sessionBean.getSimState() instanceof SimState.AwaitingParameters params) {
            if (params.jobId() == null || params.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                sessionBean.setSimState(new SimState.FlowFailed(params.jobId(), sessionBean.getSimState(), "jobId not set"));
                return;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            SimState processingState = simService.startAnalysis(params);
            if (processingState != null) {
                sessionBean.setSimState(processingState);
            } else {
                sessionBean.setSimState(new SimState.FlowFailed(params.jobId(), params, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getSimState() instanceof SimState.Processing processingState) {
            sessionBean.setSimState(simService.checkCompletion(processingState));
            if (sessionBean.getSimState() instanceof SimState.ResultsReady) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/sim/similarities-results.html");
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Redirect to results.xhtml failed", ex);
                }
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getSimState() instanceof SimState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getSimState() instanceof SimState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getSimState()) {
            case SimState.AwaitingParameters ap ->
                0;
            case SimState.Processing p ->
                p.progress();
            case SimState.ResultsReady rr ->
                100;
            case SimState.FlowFailed ff ->
                0;
            default ->
                0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<SimState.AwaitingParameters, SimState.AwaitingParameters> updater) {
        if (sessionBean.getSimState() instanceof SimState.AwaitingParameters params) {
            sessionBean.setSimState(updater.apply(params));
        }
    }

    public int getMinSharedTargets() {
        return (sessionBean.getSimState() instanceof SimState.AwaitingParameters p) ? p.minSharedTargets() : 1;
    }

    public void setMinSharedTargets(int value) {
        updateAwaitingParameters(p -> p.withMinSharedTargets(value));
    }

    public String getSourceColIndex() {
        return (sessionBean.getSimState() instanceof SimState.AwaitingParameters p) ? p.sourceColIndex() : "";
    }

    public void setSourceColIndex(String value) {
        updateAwaitingParameters(p -> p.withSourceColIndex(value));
    }
}
