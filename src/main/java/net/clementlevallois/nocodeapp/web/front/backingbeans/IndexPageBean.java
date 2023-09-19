package net.clementlevallois.nocodeapp.web.front.backingbeans;

import java.io.IOException;
import java.io.Serializable;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.faces.view.ViewScoped;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
@Named
@ViewScoped
public class IndexPageBean implements Serializable {

    public IndexPageBean() {
    }

    public void navigateToPricing() throws IOException {
        ExternalContext ec = FacesContext.getCurrentInstance().getExternalContext();
        ec.redirect(RemoteLocal.getDomain() + "/index.html#Pricing");
    }

}
