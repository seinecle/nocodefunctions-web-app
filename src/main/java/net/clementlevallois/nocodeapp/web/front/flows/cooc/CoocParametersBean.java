/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.Optional;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

@Named
@ViewScoped
public class CoocParametersBean implements Serializable {

    private int minSharedTargets = 1;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        FlowState currentState = sessionBean.getFlowState();
        switch (currentState) {
            case CoocState.DataImported importedState -> // First time on the parameters page, initialize the state
                sessionBean.setFlowState(new CoocState.AwaitingParameters(importedState.jobId(), this.minSharedTargets));
            case CoocState.AwaitingParameters params -> // Re-visiting the page, retrieve existing parameters
            {
                this.minSharedTargets = params.minSharedTargets();
            }
            default -> {
                try {
                    FacesContext.getCurrentInstance().getExternalContext().redirect("/cooc/cooc-import.xhtml?faces-redirect=true");
                } catch (IOException ex) {
                    throw new NocodeApplicationException("An IO error occurred", ex);
                }
            }
        }
    }

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
}
