package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class SingletonBean {

    private static Properties privateProperties;
    private final static String PATHLOCALDEV = "C:\\Users\\levallois\\open\\nocode-app-web-front\\";
    private final static String PATHREMOTEDEV = "/home/waouh/nocodeapp-web/";
    private static String rootProject;

    public SingletonBean() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                rootProject = PATHLOCALDEV;
            } else {
                rootProject = PATHREMOTEDEV;
            }
            InputStream is = new FileInputStream(rootProject + "private/private.properties");
            privateProperties = new Properties();
            privateProperties.load(is);
            is.close();
        } catch (UnknownHostException ex) {
            System.out.println("ex:" + ex.getMessage());
        } catch (FileNotFoundException ex) {
            System.out.println("ex:" + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("ex:" + ex.getMessage());
        }

    }

    public static Properties getPrivateProperties() {
        return privateProperties;
    }

    public static String getExternalFolderForInternationalizationFiles() {
        return rootProject + "i18n" + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator;
    }

    public static String getRootOfProject() {
        return rootProject;
    }

}
