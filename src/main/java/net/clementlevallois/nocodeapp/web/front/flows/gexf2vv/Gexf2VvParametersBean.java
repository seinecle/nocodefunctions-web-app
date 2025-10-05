package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.function.UnaryOperator;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

@Named
@ViewScoped
public class Gexf2VvParametersBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        if (!(sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters)) {
            FacesUtils.redirectTo("/gexf2vv/gexf2vv-import.html");
        }
    }

    private void update(UnaryOperator<Gexf2VvState.AwaitingParameters> fn) {
        if (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) {
            sessionBean.setFlowState(fn.apply(p));
        } else {
            throw new IllegalStateException("Wrong state: " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    // item
    public String getItem() {
        return (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) ? p.item() : "";
    }
    public void setItem(String v) { update(p -> p.withItem(v)); }

    // link
    public String getLink() {
        return (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) ? p.link() : "";
    }
    public void setLink(String v) { update(p -> p.withLink(v)); }

    // link strength
    public String getLinkStrength() {
        return (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) ? p.linkStrength() : "";
    }
    public void setLinkStrength(String v) { update(p -> p.withLinkStrength(v)); }

    // share publicly
    public boolean isShareVVPublicly() {
        return (sessionBean.getFlowState() instanceof Gexf2VvState.AwaitingParameters p) && p.shareVVPublicly();
    }
    public void setShareVVPublicly(boolean v) { update(p -> p.withShareVVPublicly(v)); }
}
