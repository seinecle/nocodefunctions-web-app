package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

@Named
@ViewScoped
public class UmigonAnalysisBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private UmigonService umigonService;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() == null) {
            FacesUtils.redirectTo("umigon-import.xhtml");
        }
    }

    public String getSelectedLanguage() {
        if (sessionBean.getFlowState() instanceof UmigonState.AwaitingParameters p) {
            return p.selectedLanguage();
        }
        return "en";
    }

    public void setSelectedLanguage(String language) {
        if (sessionBean.getFlowState() instanceof UmigonState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withSelectedLanguage(language));
        }
    }

    public int getMaxCapacity() {
        if (sessionBean.getFlowState() instanceof UmigonState.AwaitingParameters p) {
            return p.maxCapacity();
        }
        return 0;
    }

    public void runAnalysis() {
        if (sessionBean.getFlowState() instanceof UmigonState.AwaitingParameters params) {
            if (params.jobId() == null || params.jobId().isBlank()) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "No data has been imported for this analysis.");
                return;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            FlowState processingState = umigonService.startAnalysis(params);
            if (processingState instanceof FlowFailed) {
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            } else {
                sessionBean.setFlowState(processingState);
            }
        }
    }

    public String pollingListener() {
        if (sessionBean.getFlowState() instanceof UmigonState.Processing processingState) {
            sessionBean.setFlowState(umigonService.checkCompletion(processingState));
            if (sessionBean.getFlowState() instanceof UmigonState.ResultsReady) {
                FacesUtils.redirectTo("results.html");
            }
        }
        return null;
    }

    public String getRunButtonText() {
        return (sessionBean.getFlowState() instanceof UmigonState.Processing)
                ? sessionBean.getLocaleBundle().getString("general.message.wait_long_operation")
                : sessionBean.getLocaleBundle().getString("general.verbs.compute");
    }

    public boolean isRunButtonDisabled() {
        return sessionBean.getFlowState() instanceof UmigonState.Processing;
    }

    public int getProgress() {
        return switch (sessionBean.getFlowState()) {
            case UmigonState.AwaitingParameters ap ->
                0;
            case UmigonState.Processing p ->
                p.progress();
            case UmigonState.ResultsReady rr ->
                100;
            default ->
                0;
        };
    }
}
