/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Optional;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

@Named
@ViewScoped
public class CoocParametersBean implements Serializable {

    private int minSharedTargets = 1;
    private boolean hasHeaders = false;

    @Inject
    private SessionBean sessionBean;

    public int getMinSharedTargets() {
        return Optional.ofNullable(sessionBean.getFlowState())
                .filter(CoocState.AwaitingParameters.class::isInstance)
                .map(CoocState.AwaitingParameters.class::cast)
                .map(CoocState.AwaitingParameters::minSharedTargets)
                .orElse(this.minSharedTargets);
    }

    public void setMinSharedTargets(int minSharedTargets) {
        this.minSharedTargets = minSharedTargets;
        if (sessionBean.getFlowState() instanceof CoocState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withMinSharedTargets(minSharedTargets));
        }
    }

    public boolean isHasHeaders() {
        return Optional.ofNullable(sessionBean.getFlowState())
                .filter(CoocState.AwaitingParameters.class::isInstance)
                .map(CoocState.AwaitingParameters.class::cast)
                .map(CoocState.AwaitingParameters::hasHeaders)
                .orElse(this.hasHeaders);
    }

    public void setHasHeaders(boolean hashHeaders) {
        if (sessionBean.getFlowState() instanceof CoocState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withHasHeaders(hashHeaders));
        }
    }
}
