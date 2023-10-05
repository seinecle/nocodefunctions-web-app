package net.clementlevallois.nocodeapp.web.front.backingbeans;

/**
 *
 * @author LEVALLOIS
 */
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;

@Named
@SessionScoped
public class LocaleBean implements Serializable {

    private static final long serialVersionUID = 1L;

    private Locale currentLocale;
    private List<Locale> availableLocales;

    @Inject
    SessionBean sessionBean;

    public LocaleBean() {
    }

    public void setSessionBean(SessionBean sessionBean) {
        this.sessionBean = sessionBean;
    }

    @PostConstruct
    public void init() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        if (currentInstance == null) {
            currentLocale = Locale.ENGLISH;
        } else {
            currentLocale = FacesContext.getCurrentInstance().getExternalContext().getRequestLocale();
        }
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public String getLanguageTag() {
        return currentLocale.toLanguageTag();
    }

    public void setLanguageTag(String languageTag) {
        String correctLangTag;
        if (languageTag == null) {
            System.out.println("language Tag param was null??");
            return;
        }
        if (languageTag.contains("=") & !languageTag.contains("?")) {
            System.out.println("weird url parameters decoded in sessionBean");
            System.out.println("url param for lang is: " + languageTag);
            correctLangTag = languageTag.split("=")[1];
        } else if (languageTag.contains("=") & languageTag.contains("?")) {
            correctLangTag = languageTag.split("\\?")[0];
        } else {
            correctLangTag = languageTag;
        }

        Locale newLocale = Locale.forLanguageTag(correctLangTag);
        if (currentLocale == null) {
            FacesContext currentInstance = FacesContext.getCurrentInstance();
            if (currentInstance == null) {
                currentLocale = Locale.ENGLISH;
            } else {
                currentLocale = FacesContext.getCurrentInstance().getViewRoot().getLocale();

            }
            sessionBean.setCurrentLocale(currentLocale);
        }
        if (!newLocale.equals(currentLocale)) {
            currentLocale = newLocale;
            sessionBean.setCurrentLocale(currentLocale);
            FacesContext currentInstance = FacesContext.getCurrentInstance();
            if (currentInstance != null) {
                currentInstance.getViewRoot().setLocale(newLocale);
            }
            sessionBean.refreshLocaleBundle();
        }
    }

    public List<Locale> getAvailableLocales() {
        List<Locale> locales = new ArrayList();
        String[] availableLocaleAsStringArray = new String[]{"PT-BR", "PT-PT", "AZ", "BE", "BN", "BS", "CA", "CO", "EO", "ET", "EU", "FI", "GL", "GU", "FY", "HA", "HE", "HI", "HMN", "HAW", "HR", "HU", "HT", "ID", "IG", "IS", "KA", "SQ", "AM", "AR", "HY", "CEB", "GA", "JA", "JV", "KN", "KK", "KM", "RW", "KO", "KU", "KY", "LV", "LT", "LB", "MK", "MG", "MS", "ML", "MT", "MI", "MR", "MN", "MY", "NE", "NO", "NY", "OR", "PS", "FA", "PL", "PT", "PA", "RO", "RU", "SM", "GD", "SR", "ST", "SN", "SD", "SI", "SK", "SL", "SO", "SU", "SW", "SV", "TL", "TG", "TA", "TR", "TT", "TE", "TH", "TK", "UK", "UR", "UG", "UZ", "VI", "CY", "XH", "YI", "YO", "ZU", "LO", "ZH-TW", "BG", "CS", "DA", "DE", "EL", "ES", "FR", "IT", "NL", "ZH"};

        for (String tag : availableLocaleAsStringArray) {
            if (tag.contains("-")) {
                String[] tagParts = tag.split("-");
                Locale oneLocaleWithRegion = new Locale.Builder().setLanguage(tagParts[0]).setRegion(tagParts[1]).build();
                if (oneLocaleWithRegion != null) {
                    locales.add(oneLocaleWithRegion);
                }
            } else {
                Locale locale = Locale.forLanguageTag(tag.toLowerCase());
                if (locale != null) {
                    locales.add(locale);
                }
            }
        }

        // this dance is to make sure that in the dropdown menu,
        // 1. the visible item of the selection is the current language (makes intuitive sense to the user)
        // 2. English is placed in second next to the visible item (because it is a very common language)
        availableLocales = new ArrayList();
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        Locale requestLocale;
        if (currentInstance == null) {
            requestLocale = Locale.ENGLISH;
        } else {
            requestLocale = currentInstance.getExternalContext().getRequestLocale();
        }
        for (Locale l : locales) {
            if (!l.getLanguage().equals(requestLocale.getLanguage()) & !l.getLanguage().equals("en")) {
                availableLocales.add(l);
            }
        }
        List<Locale> sortedList = availableLocales.stream()
                .sorted(new LocaleComparator(currentLocale))
                .collect(Collectors.toList());
        availableLocales = new ArrayList();
        availableLocales.addAll(sortedList);
        if (!requestLocale.getLanguage().equals("en")) {
            availableLocales.add(Locale.ENGLISH);
        }
        availableLocales.add(requestLocale);
        return availableLocales;
    }
}
