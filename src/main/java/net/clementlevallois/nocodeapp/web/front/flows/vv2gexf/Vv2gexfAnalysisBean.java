package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

@Named
@ViewScoped
public class Vv2gexfAnalysisBean implements Serializable {

    @Inject
    SessionBean sessionBean;

    @Inject
    Vv2gexfService service;

    public String convert() {
        if (sessionBean.getFlowState() instanceof Vv2gexfState.AwaitingFile p) {
            byte[] gexf = service.convert(p.jobId());
            sessionBean.setFlowState(new Vv2gexfState.ResultsReady(p.jobId(), gexf));
            return "results.xhtml?faces-redirect=true";
        } else {
            throw new IllegalStateException("Wrong state for conversion");
        }
    }
}
