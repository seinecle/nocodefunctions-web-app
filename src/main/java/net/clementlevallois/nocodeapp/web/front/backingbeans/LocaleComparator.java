/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import java.util.Comparator;
import java.util.Locale;

/**
 *
 * @author LEVALLOIS
 */
public class LocaleComparator implements Comparator<Locale> {

    private final Locale currentLocale;

    public LocaleComparator(Locale currentLocale) {
        this.currentLocale = currentLocale;
    }

    @Override
    public int compare(Locale firstLocale, Locale secondLocale) {
        if (firstLocale == null || secondLocale == null || currentLocale == null) {
            return -1;
        }
        return firstLocale.getDisplayName(currentLocale).compareTo(secondLocale.getDisplayName(currentLocale));
    }

}
