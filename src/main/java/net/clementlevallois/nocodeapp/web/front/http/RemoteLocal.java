package net.clementlevallois.nocodeapp.web.front.http;

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
            path = "nocodefunctions-web-app";
            protocol = "http";
            domain = "localhost";
            port = 9080;
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
        StringBuilder uri = new StringBuilder();

        if (isLocal()) {
            uri.append(protocol).append("://").append(domain).append(":").append(port).append("/")
                    .append(path);
        }else {
            uri.append(protocol).append("://").append(domain).append("/")
                    .append(path);
        }
        return uri.toString();
    }

    public static String getInternalMessageApiEndpoint(){
        return "/internalapi/messageFromAPI/";
    }
    

    public static boolean isLocal() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static boolean isTest() {
        return System.getProperty("test") != null && System.getProperty("test").equals("yes");
    }
}
