/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans.tests;

import java.io.IOException;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.CardTestBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class CardTestBeanTest {

    private static SessionBean sessionBean;
    private static CardTestBean cardTest;
    private static ApplicationPropertiesBean applicationProperties;

    @BeforeAll
    public static void mockEssentialBeans() throws IOException {
        sessionBean = new SessionBean();
        applicationProperties = new ApplicationPropertiesBean();
        sessionBean.setApplicationProperties(applicationProperties);
        sessionBean.init();
        cardTest = new CardTestBean();
        cardTest.setSessionBean(sessionBean);
        cardTest.setApplicationPropertiesBean(applicationProperties);
        cardTest.setUsePublicDomainName(Boolean.FALSE);
        cardTest.setPrivateProperties(applicationProperties.getPrivateProperties());
    }

    @Test
    public void runUmigonTestFR() {
        cardTest.runUmigonTestFR();
        assertThat(cardTest.getUmigonResultFR()).startsWith("🤗 positive");
    }

    @Test
    public void runUmigonTestEN() {
        cardTest.runUmigonTestEN();
        assertThat(cardTest.getUmigonResultEN()).startsWith("🤗 positive");
    }

    @Test
    public void runUmigonTestES() {
        cardTest.runUmigonTestES();
        assertThat(cardTest.getUmigonResultES()).startsWith("🤗 positive");
    }

    @Test
    public void runOrganicTestEN() {
        cardTest.runOrganicTestEN();
        assertThat(cardTest.getOrganicResultEN()).startsWith("📢 commercial");
    }

    @Test
    public void runOrganicTestFR() {
        cardTest.runOrganicTestFR();
        assertThat(cardTest.getOrganicResultFR()).startsWith("📢 commercial");
    }

}
