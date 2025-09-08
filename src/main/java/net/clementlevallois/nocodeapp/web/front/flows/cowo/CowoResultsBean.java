package net.clementlevallois.nocodeapp.web.front.flows.cowo;

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
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.io.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class CowoResultsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CowoResultsBean.class.getName());

    @Inject
    private SessionBean sessionBean;

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    private CowoState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof CowoState.ResultsReady rr) {
            this.results = rr;
        } else {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("cowo-import.html?faces-redirect=true");
            } catch (IOException ex) {
                throw new NocodeApplicationException("An IO error occurred", ex);

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
                    throw new NocodeApplicationException("An IO error occurred", ex);

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
                    throw new NocodeApplicationException("An IO error occurred", ex);
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
            sessionBean.setFlowState(results.withShareVVPublicly(flag));
            this.results = (CowoState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareGephiLitePublicly(flag));
            this.results = (CowoState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public String getNodesAsJson() {
        return results != null ? results.nodesAsJson() : "{}";
    }

    public String getEdgesAsJson() {
        return results != null ? results.edgesAsJson() : "{}";
    }
}
