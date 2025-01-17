/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.i18n;

import jakarta.ejb.Singleton;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;

/**
 *
 * @author LEVALLOIS
 */

public class I18nStaticFilesResourceBundle extends ResourceBundle {

    ApplicationPropertiesBean appBean;
    
    private ResourceBundle rb;
    private Locale current;

    public I18nStaticFilesResourceBundle() {
    }

    public void setApplicationPropertiesBean(ApplicationPropertiesBean applicationPropertiesBean) {
        this.appBean = applicationPropertiesBean;
    }
    
    
    @Override
    public Object handleGetObject(String key) {
        return getCurrentInstance().getObject(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return getCurrentInstance().getKeys();
    }

    public ResourceBundle getCurrentInstance() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        Locale locale;
        if (currentInstance != null) {
            locale = currentInstance.getViewRoot().getLocale();
        }else{
            locale = Locale.ENGLISH;
        }
        if (rb == null || !locale.equals(current)) {
            rb = simpleMethodToGetResourceBundle(locale);
            current = locale;
        }
        return rb;
    }

    public ResourceBundle simpleMethodToGetResourceBundle(Locale locale) {
        try {
            File i8nFolderAsFile = ApplicationPropertiesBean.getExternalFolderForInternationalizationFiles().toFile();
            URL[] urls = {i8nFolderAsFile.toURI().toURL()};
            try (URLClassLoader loader = new URLClassLoader(urls)) {
                rb = ResourceBundle.getBundle("text", locale, loader);
            }
            return rb;
        } catch (MalformedURLException ex) {
            System.out.println("wrong file path or name when getting i18n bundle");
            System.out.println("ex: "+ ex);
        } catch (IOException ex) {
            System.out.println("io issue when getting i18n bundle");
            System.out.println("ex: "+ ex);
        }
        return null;
    }
}
