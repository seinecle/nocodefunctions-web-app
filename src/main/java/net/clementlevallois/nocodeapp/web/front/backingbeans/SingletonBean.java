package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class SingletonBean {

    public static final String SERVICE_NAME = "NOCODE";
    public static final String SERVICE_NAME_ALL_CREDITS_USED = "nocode-all-credits-used";

    static {
        Logger grizzlyLogger = Logger.getLogger("org.glassfish.grizzly.http2");
        grizzlyLogger.setLevel(Level.SEVERE);
    }

    public SingletonBean() {
        setStage();
    }

    private void setStage() {
        if (RemoteLocal.isTest() || RemoteLocal.isLocal()) {
            System.setProperty("projectStage", "Development");
            System.out.println("project stage set to DEVELOPMENT");
        } else {
            System.setProperty("projectStage", "Production");
            System.out.println("project stage set to PRODUCTION");
        }
    }
}
