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
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.cooc.CoocState;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

/**
 * Main orchestrator bean for the Region Extractor workflow, refactored for a
 * file-system-based, asynchronous architecture.
 */
@Named
@RequestScoped
public class RegionExtractorAnalysisBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    RegionExtractorService regionExtractorService;

    /**
     * Triggers the final text extraction process on the backend.
     */
    public void startProcessingTargets() {
        if (sessionBean.getRegionExtractorState() instanceof RegionExtractorState.TargetPdfsUploaded current) {
            RegionExtractorState processingState = regionExtractorService.callMicroService(current);
            if (processingState != null) {
                sessionBean.setRegionExtractorState(processingState);
            } else {
                sessionBean.setRegionExtractorState(new RegionExtractorState.FlowFailed(current.jobId(), current, "Failed to start analysis."));
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "Could not start analysis.");
            }
        } else {
            System.out.println("incorrect state in startProcessingTargets : " + sessionBean.getRegionExtractorState().typeName());
        }
    }

    /**
     * Called by a poller to check if the text extraction is complete.
     */
    public void checkExtractionStatus() {
        if (sessionBean.getRegionExtractorState() instanceof RegionExtractorState.Processing processingState) {
            RegionExtractorState stateAfterCompletionCheck = regionExtractorService.checkCompletion(processingState);
            sessionBean.setRegionExtractorState(stateAfterCompletionCheck);
            if (sessionBean.getRegionExtractorState() instanceof RegionExtractorState.ResultsReady) {
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
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.AwaitingExemplarPdf;
    }

    public boolean isStateRegionDefinition() {
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.RegionDefinition;
    }

    public boolean isStateAwaitingTargetPdfs() {
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.AwaitingTargetPdfs;
    }

    public boolean isStateProcessing() {
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.Processing;
    }

    public boolean isStateResultsReady() {
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.ResultsReady;
    }

    public boolean isStateFlowFailed() {
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.FlowFailed;
    }

}
