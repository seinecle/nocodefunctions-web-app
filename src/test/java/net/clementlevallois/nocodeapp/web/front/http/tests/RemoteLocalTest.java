/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.http.tests;

import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;


/**
 *
 * @author LEVALLOIS
 */
public class RemoteLocalTest {

    @Test
    public void remoteLocalClass() {
        String domain = RemoteLocal.getDomain();
        assertThat(domain).isEqualTo("http://localhost:8080/jsf-app");
    }

    @Test
    public void getHostFunctionsAPI() {
        SingletonBean sb = new SingletonBean();
        String apiUrl = RemoteLocal.getHostFunctionsAPI();
        assertThat(apiUrl).isEqualTo("http://localhost:7002");
    }

    @Test
    public void isTest() {
        SingletonBean sb = new SingletonBean();
        boolean isTest = RemoteLocal.isTest();
        assertThat(isTest).isFalse();
    }

    @Test
    public void isLocal() {
        SingletonBean sb = new SingletonBean();
        boolean isLocal = RemoteLocal.isLocal();
        assertThat(isLocal).isTrue();
    }

}
