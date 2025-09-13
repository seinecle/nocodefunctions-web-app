package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;

@Named
@ViewScoped
public class Gexf2VvResultsBean implements Serializable {

    @Inject
    SessionBean sessionBean;

    public String getVosviewerUrl() {
        return (sessionBean.getFlowState() instanceof Gexf2VvState.ResultsReady r) ? r.vosviewerUrl() : "";
    }

    public void open() {
        var url = getVosviewerUrl();
        if (url != null && !url.isBlank()) {
            FacesUtils.redirectTo(url);
        } else {
            throw new NocodeApplicationException("gexf2vv conversion : vosviewer url was null or empty", new Throwable());
        }
    }
}
