package net.clementlevallois.nocodeapp.web.front.flows.spatialize;

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
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

/**
 *
 * @author clevallois
 */
@Named
@ViewScoped
public class SpatializeAnalysisBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(SpatializeAnalysisBean.class.getName());

    @Inject
    private SessionBean sessionBean;
    @Inject
    private BackToFrontMessengerBean logBean;
    @Inject
    private SpatializeService spatializeService;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() == null) {
            FacesUtils.redirectTo("spatialize-import.html");
        }
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof SpatializeState.AwaitingParameters params) {
            if (params.jobId() == null || params.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            FlowState processingState = spatializeService.startAnalysis(params);
            if (processingState instanceof FlowFailed) {
                sessionBean.setFlowState(processingState);
            } else {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");

            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof SpatializeState.Processing processingState) {
            sessionBean.setFlowState(spatializeService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof SpatializeState.ResultsReady) {
                FacesUtils.redirectTo("results.html");
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getFlowState() instanceof SpatializeState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getFlowState() instanceof SpatializeState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getFlowState()) {
            case SpatializeState.AwaitingParameters ap ->
                0;
            case SpatializeState.Processing p ->
                p.progress();
            case SpatializeState.ResultsReady rr ->
                100;
            case FlowFailed ff ->
                0;
            default ->
                0;
        };
    }

    private void updateAwaitingParameters(java.util.function.Function<SpatializeState.AwaitingParameters, SpatializeState.AwaitingParameters> updater) {
        if (sessionBean.getFlowState() instanceof SpatializeState.AwaitingParameters params) {
            sessionBean.setFlowState(updater.apply(params));
        }
    }

    public int getDurationInSeconds() {
        if (sessionBean.getFlowState() instanceof SpatializeState.AwaitingParameters p) {
            return p.durationInSecond();
        } else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setDurationInSeconds(int duration) {
        updateAwaitingParameters(p -> p.withDurationInSecond(duration));
    }
}
