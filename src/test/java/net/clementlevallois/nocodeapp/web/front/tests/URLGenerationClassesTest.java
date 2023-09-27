/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.tests;

import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;


/**
 *
 * @author LEVALLOIS
 */
public class URLGenerationClassesTest {

    @Test
    public void remoteLocalClassTest() {
        String domain = RemoteLocal.getDomain();
        assertThat(domain).isEqualTo("http://localhost:8080/jsf-app");
    }

    @Test
    public void urlToAPI() {
        SingletonBean sb = new SingletonBean();
        String apiUrl = RemoteLocal.getHostFunctionsAPI();
        System.out.println("api url: "+ apiUrl);
    }

}
