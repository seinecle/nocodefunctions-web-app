/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans.tests;

import java.util.List;
import java.util.Locale;
import net.clementlevallois.nocodeapp.web.front.backingbeans.LocaleBean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class LocaleBeanTest {
    
    @Test
    public void getAvailableReturnsAList(){
        LocaleBean activeLocale = new LocaleBean();
        List<Locale> available = activeLocale.getAvailableLocales();
        assertThat(available).size().isGreaterThan(100);
    }
    
}
