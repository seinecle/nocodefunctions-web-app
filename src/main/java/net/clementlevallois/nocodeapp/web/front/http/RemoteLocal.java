package net.clementlevallois.nocodeapp.web.front.http;

import io.mikael.urlbuilder.UrlBuilder;
import java.net.URI;
import java.util.Properties;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;

/**
 *
 * @author LEVALLOIS
 */
public class RemoteLocal {

    public static String getDomain() {
        String path;
        String protocol;
        String domain;
        int port;

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            path = "jsf-app";
            protocol = "http";
            domain = "localhost";
            port = 8080;
        } else {
            path = "";
            protocol = "https://";
            port = 443;
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
        }
        URI uri = UrlBuilder
                .empty()
                .withScheme(protocol)
                .withHost(domain)
                .withPort(port)
                .withPath(path)
                .toUri();
        return uri.toString();
    }

    public static String getHostFunctionsAPI() {
        Properties privateProperties = SingletonBean.getPrivateProperties();
        URI uri;

        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withHost("localhost")
                    .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port")))).toUri();
            return uri.toString();
        } else {
            UrlBuilder urlBuilder = UrlBuilder
                    .empty()
                    .withScheme("https");
            String domain;
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
            urlBuilder.withHost(domain);
            return urlBuilder.toUrl().toString();
        }
    }

    public static boolean isLocal() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isTest() {
        return System.getProperty("test") != null && System.getProperty("test").equals("yes");
    }
}
