/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

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
public class SimResultsBean implements Serializable {

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
             FacesUtils.redirectTo("sim-import.html");
        }
    }

    public void execGraph(int maxNodes) {
    }

    public void gotoVV() {
        if (results != null) {
            String link = exportToVosViewer.exportAndReturnLinkForConversionToVV(results.jobId(), results.shareVVPublicly(), "each node is an item in the column you selected", "a link or connection means the two nodes had an attribute in common in your source files", "strength of the connection reflects how similar are the attributes of the two connected nodes");
            if (link != null && !link.isBlank()) {
                FacesUtils.redirectTo(link);
            }
        }
    }

    public void gotoGephiLite() {
        if (results != null) {
            String link = exportToGephiLite.exportAndReturnLinkFromId(results.jobId(), results.shareGephiLitePublicly());
            if (link != null && !link.isBlank()) {
                FacesUtils.redirectTo(link);
            }
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return ExportToGexf.exportGexfAsStreamedFile(results.gexf(), "results_sim");
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
