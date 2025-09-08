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
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

@Named
@ViewScoped
public class CoocParametersBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CoocParametersBean.class.getName());

    private int minSharedTargets = 1;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        CoocState currentState = sessionBean.getFlowState();
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
                    LOG.log(Level.SEVERE, "Redirect failed in CoocParametersBean init", ex);
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
