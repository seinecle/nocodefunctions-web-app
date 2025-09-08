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
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.cowo.CowoResultsBean;
import net.clementlevallois.nocodeapp.web.front.io.ExportToGephiLite;
import net.clementlevallois.nocodeapp.web.front.io.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author clevallois
 */
@Named
@ViewScoped
public class SpatializeResultsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CowoResultsBean.class.getName());

    @Inject
    private SessionBean sessionBean;

    @Inject
    private ExportToVosViewer exportToVosViewer;

    @Inject
    private ExportToGephiLite exportToGephiLite;

    private SpatializeState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof SpatializeState.ResultsReady rr) {
            this.results = rr;
        } else {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("spatialize-import.html?faces-redirect=true");
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Redirection failed in results bean init", ex);
            }
        }
    }

    public StreamedContent getFileToSave() {
        if (results != null) {
            return GEXFSaver.exportGexfAsStreamedFile(results.gexf(), "spatializef_graph");
        }
        return new DefaultStreamedContent();
    }

}
