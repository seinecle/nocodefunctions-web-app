package net.clementlevallois.nocodeapp.web.front.flows.cowo;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.io.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGexf;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class CowoResultsBean implements Serializable {

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
            FacesUtils.redirectTo("cowo-import.html");
        }
    }

    public void gotoVV() {
        if (results != null) {
            String linkToVosViewer = exportToVosViewer.exportAndReturnLinkForConversionToVV(results.jobId(), results.shareVVPublicly(), "each node is a term or expression appearing frequently in the corpus", "a link means the two connected term are cooccurring in the corpus", "strength of the connection reflects the frequency of coccurrences between the two connected terms");
            if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
                FacesUtils.redirectTo(linkToVosViewer);
            }
        } else {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "results are null, can't redirect to vosviewer");
        }
    }

    public void gotoGephiLite() {
        if (results != null) {
            String urlToGephiLite = exportToGephiLite.exportAndReturnLinkFromId(results.jobId(), results.shareGephiLitePublicly());
            if (urlToGephiLite != null && !urlToGephiLite.isBlank()) {
                FacesUtils.redirectTo(urlToGephiLite);
            }
        } else {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "results are null, can't redirect to gephi lite");
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return ExportToGexf.exportGexfAsStreamedFile(results.gexf(), "results_cowo");
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
        } else {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "results are null, can't redirect to vosviewer");
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareGephiLitePublicly(flag));
            this.results = (CowoState.ResultsReady) sessionBean.getFlowState();
        } else {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Error", "results are null, can't redirect to gephi lite");
        }
    }

    public String getNodesAsJson() {
        return results != null ? results.nodesAsJson() : "{}";
    }

    public String getEdgesAsJson() {
        return results != null ? results.edgesAsJson() : "{}";
    }
}
