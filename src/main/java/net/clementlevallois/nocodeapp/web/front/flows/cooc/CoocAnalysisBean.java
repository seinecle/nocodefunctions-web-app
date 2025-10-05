package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowHasNoAccess;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

@Named
@ViewScoped
public class CoocAnalysisBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private CoocService coocService;

    public CoocAnalysisBean() {
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof CoocState.AwaitingParameters params) {
            sessionBean.setFlowState(new CoocState.Processing(params.jobId(), params, 0));
            FlowState flowState = coocService.sheetModelToCooccurrences(params);
            // if flow failed, return a message to the user saying that no data was found.
            if (flowState instanceof FlowFailed) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                return;
            }
            FlowState accessCheckPassed = coocService.accessCheckPassed(params);
            if (accessCheckPassed instanceof FlowHasNoAccess){
                FacesUtils.redirectTo("/plans/pricing_table.html");
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            FlowState processingState = coocService.callCoocMicroService(params);
            if (processingState instanceof FlowFailed flowFailed) {
                throw new IllegalStateException("Flow has Failed" + sessionBean.getFlowState().getClass().getSimpleName() + flowFailed.userMessage());
            } else {
                sessionBean.setFlowState(processingState);
            }
        } else {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() != null && sessionBean.getFlowState() instanceof CoocState.Processing processingState) {
            sessionBean.setFlowState(coocService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof CoocState.ResultsReady) {
                FacesUtils.redirectTo("/cooc/cooc-results.html");
                return null;
            } else {
                return null;
            }
        } else {
            return null;
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
        FlowState flowState = sessionBean.getFlowState();
        if (flowState == null) {
            return 0;
        }
        return switch (flowState) {
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

    public void setProgress(int i) {

    }

}
