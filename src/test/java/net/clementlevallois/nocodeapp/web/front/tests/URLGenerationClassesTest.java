/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.tests;

import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author LEVALLOIS
 */
public class URLGenerationClassesTest {

    @Test
    public void remoteLocalClassTest() {
        String domain = RemoteLocal.getDomain();
        Assert.assertEquals("http://localhost:8080/jsf-app", domain);
    }

    @Test
    public void urlToAPI() {
        SingletonBean sb = new SingletonBean();
        String apiUrl = RemoteLocal.getHostFunctionsAPI();
        System.out.println("api url: "+ apiUrl);
    }

}
