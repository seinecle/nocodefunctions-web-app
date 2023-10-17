/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans.tests;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 *
 * @author LEVALLOIS
 */
public class LocaleBeanTest {

    SessionBean sessionBean;

    @BeforeEach
    public void mockEssentialBeans() throws IOException {
        sessionBean = Mockito.mock(SessionBean.class);
    }    
    
    @Test
    public void setLanguageTag(){
        LocaleBean activeLocale = new LocaleBean();
        activeLocale.setSessionBean(sessionBean);
        activeLocale.setLanguageTag("it");
        assertThat(activeLocale.getCurrentLocale()).isEqualTo(Locale.ITALIAN);
    }
    
    @Test
    public void getAvailableReturnsAList(){
        LocaleBean activeLocale = new LocaleBean();
        List<Locale> available = activeLocale.getAvailableLocales();
        assertThat(available).size().isGreaterThan(100);
    }
    
}
