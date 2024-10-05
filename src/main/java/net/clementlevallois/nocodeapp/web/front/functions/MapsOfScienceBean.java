package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.Serializable;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig

public class MapsOfScienceBean implements Serializable {

    @Inject
    SessionBean sessionBean;

    public MapsOfScienceBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("mapsofscience");

    }

}
