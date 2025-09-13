package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

@Named
@RequestScoped
public class CoocAnalysisBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private CoocService coocService;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() == null) {
            FacesUtils.redirectTo("/cooc/cooc-import.html");
        }
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof CoocState.AwaitingParameters params) {
            sessionBean.setFlowState(new CoocState.Processing(params.jobId(), params, 0));
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            FlowState processingState = coocService.callCoocMicroService(params);
            if (processingState instanceof FlowFailed flowFailed) {
                throw new IllegalStateException("Flow has Failed" + sessionBean.getFlowState().getClass().getSimpleName() + flowFailed.userMessage());
            } else {
                sessionBean.setFlowState(processingState);
            }
        } else {
            throw new IllegalStateException("State is not CoocState.AwaitingParameters " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof CoocState.Processing processingState) {
            sessionBean.setFlowState(coocService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof CoocState.ResultsReady) {
                FacesUtils.redirectTo("/cooc/cooc-results.html");
                return null;
            } else {
                // results have not arrived yet, this is a nominal state.
                return null;
            }
        } else {
            throw new IllegalStateException("State is not CoocState.Processing "
                    + sessionBean.getFlowState().getClass().getSimpleName());
        }
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
            default ->
                0;
        };
    }
}
