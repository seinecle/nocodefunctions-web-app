/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.i18n;

import jakarta.faces.context.FacesContext;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;

/**
 *
 * @author LEVALLOIS
 */
public class I18nStaticFilesResourceBundle extends ResourceBundle {

    ResourceBundle rb;
    Locale current;

    @Override
    public Object handleGetObject(String key) {
        return getCurrentInstance().getObject(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return getCurrentInstance().getKeys();
    }

    public ResourceBundle getCurrentInstance() {
        Locale locale = FacesContext.getCurrentInstance().getViewRoot().getLocale();
        if (rb == null || !locale.equals(current)) {
            rb = simpleMethodToGetResourceBundle(locale);
            current = locale;
        }
        return rb;
    }

    public ResourceBundle simpleMethodToGetResourceBundle(Locale locale) {
        try {
            File i8nFolderAsFile = new File(SingletonBean.getExternalFolderForInternationalizationFiles());
            URL[] urls = {i8nFolderAsFile.toURI().toURL()};
            try (URLClassLoader loader = new URLClassLoader(urls)) {
                rb = ResourceBundle.getBundle("text", locale, loader);
            }
            return rb;
        } catch (MalformedURLException ex) {
            Logger.getLogger(I18nStaticFilesResourceBundle.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(I18nStaticFilesResourceBundle.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}
