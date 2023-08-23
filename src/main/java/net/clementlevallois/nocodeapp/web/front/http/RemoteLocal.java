package net.clementlevallois.nocodeapp.web.front.http;

/**
 *
 * @author LEVALLOIS
 */
public class RemoteLocal {

    public static String getDomain() {
        String post;
        String protocol;
        String domain;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            post = "jsf-app/";
            protocol = "http://";
            domain = "localhost:8080";
        } else {
            post = "";
            protocol = "https://";
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
        }
        String result = protocol + domain + "/" + post;
        return result;
    }

    public static String getHostFunctionsAPI() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return "http://localhost:7002/";
        } else {
            String protocol = "https://";
            String domain;
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
            return protocol + domain + "/";

        }
    }

    public static boolean isLocal() {
        return System.getProperty("test") == null || System.getProperty("test").isBlank();
    }

    public static boolean isTest() {
        return System.getProperty("test") != null && System.getProperty("test").equals("yes");
    }
}
