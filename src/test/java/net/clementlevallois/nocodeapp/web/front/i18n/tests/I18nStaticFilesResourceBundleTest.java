/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.i18n.tests;

import java.util.Locale;
import java.util.ResourceBundle;
import net.clementlevallois.nocodeapp.web.front.i18n.I18nStaticFilesResourceBundle;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class I18nStaticFilesResourceBundleTest {

    @Test
    public void remoteLocalClass() {
        I18nStaticFilesResourceBundle bundleManager = new I18nStaticFilesResourceBundle();
        ResourceBundle bundle = bundleManager.simpleMethodToGetResourceBundle(Locale.forLanguageTag("fr"));
        boolean containsKey = bundle.containsKey("cowo.tool.argument1.details");
        assertThat(containsKey).isTrue();
    }
}
