package net.clementlevallois.nocodeapp.web.front.flows.sim;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

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
        if (sessionBean.getFlowState() == null) {
            FacesUtils.redirectTo("sim-import.html");
        }
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof SimState.AwaitingParameters params) {
            if (params.jobId() == null || params.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                sessionBean.setFlowState(new FlowFailed(params.jobId(), sessionBean.getFlowState(), "jobId not set"));
                return;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            FlowState processingState = simService.callSimMicroService(params);
            if (processingState != null) {
                sessionBean.setFlowState(processingState);
            } else {
                sessionBean.setFlowState(new FlowFailed(params.jobId(), params, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        }else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof SimState.Processing processingState) {
            sessionBean.setFlowState(simService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof SimState.ResultsReady) {
                FacesUtils.redirectTo("/sim/similarities-results.html");
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getFlowState() instanceof SimState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getFlowState() instanceof SimState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getFlowState()) {
            case SimState.AwaitingParameters ap ->
                0;
            case SimState.Processing p ->
                p.progress();
            case SimState.ResultsReady rr ->
                100;
            default ->
                0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<SimState.AwaitingParameters, SimState.AwaitingParameters> updater) {
        if (sessionBean.getFlowState() instanceof SimState.AwaitingParameters params) {
            sessionBean.setFlowState(updater.apply(params));
        }else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public int getMinSharedTargets() {
        return (sessionBean.getFlowState() instanceof SimState.AwaitingParameters p) ? p.minSharedTargets() : 1;
    }

    public void setMinSharedTargets(int value) {
        updateAwaitingParameters(p -> p.withMinSharedTargets(value));
    }

    public String getSourceColIndex() {
        return (sessionBean.getFlowState() instanceof SimState.AwaitingParameters p) ? p.sourceColIndex() : "";
    }

    public void setSourceColIndex(String value) {
        updateAwaitingParameters(p -> p.withSourceColIndex(value));
    }
}
