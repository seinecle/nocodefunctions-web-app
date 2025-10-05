/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.spatialize;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.io.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGexf;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author clevallois
 */
@Named
@ViewScoped
public class SpatializeResultsBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    private SpatializeState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof SpatializeState.ResultsReady rr) {
            this.results = rr;
        } else {
            FacesUtils.redirectTo("spatialize-import.html");
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return ExportToGexf.exportGexfAsStreamedFile(results.gexf(), "spatializef_graph");
        }
        return new DefaultStreamedContent();
    }

}
