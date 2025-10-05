package net.clementlevallois.nocodeapp.web.front.utils;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import java.io.IOException;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;

public final class FacesUtils {

    public static void redirectTo(String url) {
        try {
            FacesContext facesContext = FacesContext.getCurrentInstance();
            ExternalContext externalContext = facesContext.getExternalContext();

            // Get the application's context path (e.g., "/nocodefunctions-web-app")
            String contextPath = externalContext.getRequestContextPath();

            // Prepend the context path to the URL
            String fullUrl = contextPath + url;

            // Perform the redirect with the complete, correct URL
            externalContext.redirect(fullUrl);
        } catch (IOException ex) {
            throw new NocodeApplicationException("Redirect to " + url + " failed due to an I/O error.", ex);
        }
    }
}
