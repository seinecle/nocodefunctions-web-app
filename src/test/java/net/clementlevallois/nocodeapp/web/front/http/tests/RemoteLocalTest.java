/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.http.tests;

import java.io.IOException;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 *
 * @author LEVALLOIS
 */
public class RemoteLocalTest {

    private static ApplicationPropertiesBean applicationProperties;
        
    @BeforeAll
    public static void loadProperties() throws IOException {
        applicationProperties = new ApplicationPropertiesBean();
    }
    
    @AfterEach
    public void resetEnvToWindows() throws IOException {
        System.setProperty("os.name", "windows");
        System.getProperty("test","");
    }

    @Test
    public void remoteLocalClass() {
        String domain = RemoteLocal.getDomain();
        assertThat(domain).isEqualTo("http://localhost:8080/jsf-app");

        System.setProperty("os.name", "linux");
        domain = RemoteLocal.getDomain();
        assertThat(domain).isEqualTo("https://nocodefunctions.com");

        System.setProperty("os.name", "linux");
        System.setProperty("test","yes");
        domain = RemoteLocal.getDomain();
        assertThat(domain).isEqualTo("https://test.nocodefunctions.com");

    }

    @Test
    public void getHostFunctionsAPI() {
        String apiUrl = applicationProperties.getHostFunctionsAPI();
        assertThat(apiUrl).isEqualTo("http://localhost:7002");
    }

    @Test
    public void isTest() {
        boolean isTest = RemoteLocal.isTest();
        assertThat(isTest).isFalse();
    }

    @Test
    public void isLocal() {
        boolean isLocal = RemoteLocal.isLocal();
        assertThat(isLocal).isTrue();
    }

}
