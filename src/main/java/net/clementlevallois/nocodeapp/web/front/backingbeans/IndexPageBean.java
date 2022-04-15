/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import java.io.IOException;
import java.io.Serializable;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import javax.faces.view.ViewScoped;
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
        ec.redirect(RemoteLocal.getDomain() + "index.html#Pricing");
    }

}
