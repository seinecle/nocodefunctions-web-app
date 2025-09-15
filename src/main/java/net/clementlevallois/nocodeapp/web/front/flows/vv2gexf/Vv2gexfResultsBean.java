package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class Vv2gexfResultsBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    private Vv2gexfState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof Vv2gexfState.ResultsReady rr) {
            this.results = rr;
        } else {
            FacesUtils.redirectTo("vv2gexf-import.html");
        }
    }

    public StreamedContent getFileToSave() {
        if (results == null || results.gexfBytes() == null) {
            return new DefaultStreamedContent();
        }
        var in = new ByteArrayInputStream(results.gexfBytes());
        return DefaultStreamedContent.builder()
                .name("results.gexf")
                .contentType("application/gexf+xml")
                .stream(() -> in)
                .build();
    }
}
