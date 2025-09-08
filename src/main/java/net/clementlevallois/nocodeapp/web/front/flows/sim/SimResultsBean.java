/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.io.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class SimResultsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(SimResultsBean.class.getName());

    @Inject
    private SessionBean sessionBean;

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    private SimState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof SimState.ResultsReady rr) {
            this.results = rr;
        } else {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("sim-import.html?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirect failed in SimResultsBean init", ex);
            }
        }
    }

    public void execGraph(int maxNodes) {
    }

    public void gotoVV() {
        if (results != null) {
            String link = exportToVosViewer.exportAndReturnLinkFromGexfWithGet(results.jobId(), results.shareVVPublicly());
            if (link != null && !link.isBlank()) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(link);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error redirecting to VOSviewer", ex);
                }
            }
        }
    }

    public void gotoGephiLite() {
        if (results != null) {
            String link = exportToGephiLite.exportAndReturnLinkFromId(results.jobId(), results.shareGephiLitePublicly());
            if (link != null && !link.isBlank()) {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect(link);
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, "Error redirecting to Gephi Lite", ex);
                }
            }
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return GEXFSaver.exportGexfAsStreamedFile(results.gexf(), "results_sim");
        }
        return new DefaultStreamedContent();
    }

    public Boolean getShareVVPublicly() {
        return results != null && results.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareVVPublicly(flag));
            this.results = (SimState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareGephiLitePublicly(flag));
            this.results = (SimState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public String getNodesAsJson() {
        return results != null ? results.nodesAsJson() : "{}";
    }

    public String getEdgesAsJson() {
        return results != null ? results.edgesAsJson() : "{}";
    }
}
