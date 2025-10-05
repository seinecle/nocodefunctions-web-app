package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

@Named
@ViewScoped
public class Gexf2VvAnalysisBean implements Serializable {

    @Inject
    SessionBean sessionBean;
    @Inject
    Gexf2VvService service;

    public String convert() {
        if (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) {
            String url = service.buildVosviewerUrl(p.jobId(), p.shareVVPublicly(), p.item(), p.link(), p.linkStrength());
            sessionBean.setFlowState(new Gexf2VvState.ResultsReady(p.jobId(), url, p.shareVVPublicly()));
            return "results.xhtml?faces-redirect=true";
        } else {
            throw new IllegalStateException("Wrong state for conversion");
        }
    }
}
