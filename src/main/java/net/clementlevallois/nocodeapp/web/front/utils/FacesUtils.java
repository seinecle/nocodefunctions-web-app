package net.clementlevallois.nocodeapp.web.front.utils;

import jakarta.faces.context.FacesContext;
import java.io.IOException;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;

public final class FacesUtils {

    public static void redirectTo(String url) {
        try {
            FacesContext.getCurrentInstance().getExternalContext().redirect(url);
        } catch (IOException ex) {
            throw new NocodeApplicationException("Redirect to " + url + " failed due to an I/O error.", ex);
        }
    }
}