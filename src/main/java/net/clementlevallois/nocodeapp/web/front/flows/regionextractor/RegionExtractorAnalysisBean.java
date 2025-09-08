/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 International (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;


@Named
@RequestScoped
public class RegionExtractorAnalysisBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private SessionBean sessionBean;

    @Inject
    RegionExtractorService regionExtractorService;

    public void startProcessingTargets() {
        if (sessionBean.getFlowState() instanceof RegionExtractorState.TargetPdfsUploaded current) {
            FlowState processingState = regionExtractorService.callMicroService(current);
            if (processingState != null) {
                sessionBean.setFlowState(processingState);
            } else {
                sessionBean.setFlowState(new FlowFailed(current.jobId(), current, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        } else {
                sessionBean.setFlowState(new FlowFailed(sessionBean.getFlowState().jobId(), sessionBean.getFlowState(), "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
        }
    }

    /**
     * Called by a poller to check if the text extraction is complete.
     */
    public void checkExtractionStatus() {
        if (sessionBean.getFlowState() instanceof RegionExtractorState.Processing processingState) {
            FlowState stateAfterCompletionCheck = regionExtractorService.checkCompletion(processingState);
            sessionBean.setFlowState(stateAfterCompletionCheck);
            if (sessionBean.getFlowState() instanceof RegionExtractorState.ResultsReady) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(FacesContext.getCurrentInstance().getExternalContext().getRequestContextPath() + "/regionextractor/results.html");
                } catch (IOException ex) {
                    System.out.println("Redirect to results.html failed");
                }
            }
        }
    }

    // --- Getters for state checking (used by XHTML) ---
    public boolean isStateAwaitingExemplar() {
        return sessionBean.getFlowState() instanceof RegionExtractorState.AwaitingExemplarPdf;
    }

    public boolean isStateRegionDefinition() {
        return sessionBean.getFlowState() instanceof RegionExtractorState.RegionDefinition;
    }

    public boolean isStateAwaitingTargetPdfs() {
        return sessionBean.getFlowState() instanceof RegionExtractorState.AwaitingTargetPdfs;
    }

    public boolean isStateProcessing() {
        return sessionBean.getFlowState() instanceof RegionExtractorState.Processing;
    }

    public boolean isStateResultsReady() {
        return sessionBean.getFlowState() instanceof RegionExtractorState.ResultsReady;
    }

    public boolean isStateFlowFailed() {
        return sessionBean.getFlowState() instanceof FlowFailed;
    }

}
