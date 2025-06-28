/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows;


import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import java.io.Serializable;

@Named
@SessionScoped
public class WorkflowSessionBean implements Serializable {

    private CowoState cowoState;

    public CowoState getCowoState() {
        return cowoState;
    }

    public void setCowoState(CowoState cowoState) {
        this.cowoState = cowoState;
    }
}