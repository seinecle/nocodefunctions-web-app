/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans.tests;

import java.io.IOException;
import java.util.Locale;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class SessionBeanTest {

    
    private static SessionBean sessionBean;
    private static ApplicationPropertiesBean applicationProperties; 

    @BeforeAll
    public static void mockEssentialBeans() throws IOException {
        sessionBean = new SessionBean();
        applicationProperties = new ApplicationPropertiesBean();
    }    
    
    
    @Test
    public void refreshLocaleBundle() {
        Locale l = Locale.GERMAN;
        sessionBean.setCurrentLocale(l);
        sessionBean.refreshLocaleBundle();
        assertThat(sessionBean.getLocaleBundle().getLocale()).isEqualTo(Locale.GERMAN);
    }

}
