/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

/**
 *
 * @author LEVALLOIS
 */
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import java.io.IOException;

@Named
@SessionScoped
public class ActiveLocale implements Serializable {

    private static final long serialVersionUID = 1L;

    private Locale current;
    private List<Locale> available;

    @Inject
    SessionBean sessionBean;

    @PostConstruct
    public void init() {
        current = FacesContext.getCurrentInstance().getExternalContext().getRequestLocale();
    }

    public Locale getCurrent() {
        return current;
    }

    public String getLanguageTag() {
        return current.toLanguageTag();
    }

    public void setLanguageTag(String languageTag) throws IOException {

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
        if (current == null) {
            current = FacesContext.getCurrentInstance().getViewRoot().getLocale();
        }
        if (newLocale.equals(current)) {
            return;
        } else {
            current = newLocale;
            FacesContext.getCurrentInstance().getViewRoot().setLocale(newLocale);
            sessionBean.refreshLocaleBundle();
        }
    }

    public List<Locale> getAvailable() {
        List<Locale> locales = new ArrayList();
        String[] availableLocale = new String[]{"PT-BR", "PT-PT", "AZ", "BE", "BN", "BS", "CA", "CO", "EO", "ET", "EU", "FI", "GL", "GU", "FY", "HA", "HE", "HI", "HMN", "HAW", "HR", "HU", "HT", "ID", "IG", "IS", "KA", "SQ", "AM", "AR", "HY", "CEB", "GA", "JA", "JV", "KN", "KK", "KM", "RW", "KO", "KU", "KY", "LV", "LT", "LB", "MK", "MG", "MS", "ML", "MT", "MI", "MR", "MN", "MY", "NE", "NO", "NY", "OR", "PS", "FA", "PL", "PT", "PA", "RO", "RU", "SM", "GD", "SR", "ST", "SN", "SD", "SI", "SK", "SL", "SO", "SU", "SW", "SV", "TL", "TG", "TA", "TR", "TT", "TE", "TH", "TK", "UK", "UR", "UG", "UZ", "VI", "CY", "XH", "YI", "YO", "ZU", "LO", "ZH-TW", "BG", "CS", "DA", "DE", "EL", "ES", "FR", "IT", "NL", "ZH"};

        for (String tag : availableLocale) {
            if (tag.contains("-")) {
                String[] tagParts = tag.split("-");
                Locale oneLocaleWithRegion = new Locale.Builder().setLanguage(tagParts[0]).setRegion(tagParts[1]).build();
                locales.add(oneLocaleWithRegion);
            } else {
                locales.add(Locale.forLanguageTag(tag.toLowerCase()));
            }
        }

        // this dance is to make sure that in the dropdown menu,
        // 1. the visible item of the selection is the current language (makes intuitive sense to the user)
        // 2. English is placed in second next to the visible item (because it is a very common language)
        available = new ArrayList();
        Locale requestLocale = FacesContext.getCurrentInstance().getExternalContext().getRequestLocale();
        for (Locale l : locales) {
            if (!l.getLanguage().equals(requestLocale.getLanguage()) & !l.getLanguage().equals("en")) {
                available.add(l);
            }
        }
        List<Locale> sortedList = available.stream()
                .sorted(new ActiveLocale.LocaleComparator())
                .collect(Collectors.toList());
        available = new ArrayList();
        available.addAll(sortedList);
        if (!requestLocale.getLanguage().equals("en")) {
            available.add(Locale.ENGLISH);
        }
        available.add(requestLocale);
        return available;
    }

    public class LocaleComparator implements Comparator<Locale> {

        @Override
        public int compare(Locale firstLocale, Locale secondLocale) {
            if (firstLocale == null || secondLocale == null || current == null) {
                return -1;
            }
            return firstLocale.getDisplayName(current).compareTo(secondLocale.getDisplayName(current));
        }

    }
}
