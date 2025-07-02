package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class CowoResultsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CowoResultsBean.class.getName());

    @Inject
    private WorkflowSessionBean workflowSessionBean;

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    private CowoState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (workflowSessionBean.getCowoState() instanceof CowoState.ResultsReady rr) {
            this.results = rr;
        } else {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("cowo-import.html?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirection failed in results bean init", ex);
            }
        }
    }

    public void gotoVV() {
        if (results != null) {
            String linkToVosViewer = exportToVosViewer.exportAndReturnLinkFromGexfWithGet(results.jobId(), results.shareVVPublicly());
            if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(linkToVosViewer);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error redirecting to VOSviewer", ex);
                }
            }
        }
    }

    public void gotoGephiLite() {
        if (results != null) {
            String urlToGephiLite = exportToGephiLite.exportAndReturnLinkFromId(results.jobId(), results.shareGephiLitePublicly());
            if (urlToGephiLite != null && !urlToGephiLite.isBlank()) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(urlToGephiLite);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error redirecting to Gephi Lite", ex);
                }
            }
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return GEXFSaver.exportGexfAsStreamedFile(results.gexf(), "results_cowo");
        }
        return new DefaultStreamedContent();
    }

    public Boolean getShareVVPublicly() {
        return results != null && results.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (results != null) {
            workflowSessionBean.setCowoState(results.withShareVVPublicly(flag));
            this.results = (CowoState.ResultsReady) workflowSessionBean.getCowoState();
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            workflowSessionBean.setCowoState(results.withShareGephiLitePublicly(flag));
            this.results = (CowoState.ResultsReady) workflowSessionBean.getCowoState();
        }
    }

    public String getNodesAsJson() {
        return results != null ? results.nodesAsJson() : "{}";
    }

    public String getEdgesAsJson() {
        return results != null ? results.edgesAsJson() : "{}";
    }
}
