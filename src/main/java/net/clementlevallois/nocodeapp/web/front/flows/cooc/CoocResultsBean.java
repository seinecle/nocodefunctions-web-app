/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

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
public class CoocResultsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CoocResultsBean.class.getName());

    @Inject
    private SessionBean sessionBean;

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    private CoocState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getCoocState() instanceof CoocState.ResultsReady rr) {
            this.results = rr;
        } else {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("/cooc/cooc-import.xhtml?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirect failed in CoocResultsBean init", ex);
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
            return GEXFSaver.exportGexfAsStreamedFile(results.gexf(), "results_cooc");
        }
        return new DefaultStreamedContent();
    }

    public Boolean getShareVVPublicly() {
        return results != null && results.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setCoocState(results.withShareVVPublicly(flag));
            this.results = (CoocState.ResultsReady) sessionBean.getCoocState();
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setCoocState(results.withShareGephiLitePublicly(flag));
            this.results = (CoocState.ResultsReady) sessionBean.getCoocState();
        }
    }

    public String getNodesAsJson() {
        return results != null ? results.nodesAsJson() : "{}";
    }

    public String getEdgesAsJson() {
        return results != null ? results.edgesAsJson() : "{}";
    }
}