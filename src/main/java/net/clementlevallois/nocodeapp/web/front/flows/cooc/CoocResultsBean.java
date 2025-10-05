/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.annotation.PostConstruct;
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
public class CoocResultsBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    private CoocState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof CoocState.ResultsReady rr) {
            this.results = rr;
        }
    }

    public void checkStateAndRedirect() {
        if (!(sessionBean.getFlowState() instanceof CoocState.ResultsReady)) {
            FacesUtils.redirectTo("cooc_dev.html");
        }
    }

    public void gotoVV() {
        if (results != null) {
            String linkToVosViewer = exportToVosViewer.exportAndReturnLinkForConversionToVV(results.jobId(), results.shareVVPublicly(), "item", "cooccurring items", "link strength is the number of cooccurrences betwee 2 items");
            if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
                FacesUtils.redirectTo(linkToVosViewer);
            }
        }
    }

    public void gotoGephiLite() {
        if (results != null) {
            String urlToGephiLite = exportToGephiLite.exportAndReturnLinkFromId(results.jobId(), results.shareGephiLitePublicly());
            if (urlToGephiLite != null && !urlToGephiLite.isBlank()) {
                FacesUtils.redirectTo(urlToGephiLite);
            }
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return ExportToGexf.exportGexfAsStreamedFile(results.gexf(), "results_cooc");
        }
        return new DefaultStreamedContent();
    }

    public Boolean getShareVVPublicly() {
        return results != null && results.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareVVPublicly(flag));
            this.results = (CoocState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareGephiLitePublicly(flag));
            this.results = (CoocState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public String getNodesAsJson() {
        return results != null ? results.nodesAsJson() : null;
    }

    public String getEdgesAsJson() {
        return results != null ? results.edgesAsJson() : null;
    }
}
