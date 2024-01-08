package net.clementlevallois.nocodeapp.web.front.http;

import io.mikael.urlbuilder.UrlBuilder;
import java.net.URI;

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

        if (isLocal()) {
            path = "jsf-app";
            protocol = "http";
            domain = "localhost";
            port = 8080;
        } else {
            path = "";
            protocol = "https";
            port = 443;
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
        }
        URI uri;

        if (isLocal()) {
            uri = UrlBuilder
                    .empty()
                    .withScheme(protocol)
                    .withHost(domain)
                    .withPort(port)
                    .withPath(path).toUri();
        }else {
            uri = UrlBuilder
                    .empty()
                    .withScheme(protocol)
                    .withHost(domain)
                    .withPath(path).toUri();
            
        }
        return uri.toString();
    }


    public static boolean isLocal() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isTest() {
        return System.getProperty("test") != null && System.getProperty("test").equals("yes");
    }
}
