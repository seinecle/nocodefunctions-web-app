package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import net.clementlevallois.nocodeapp.web.front.utils.ApplicationProperties;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class SingletonBean {


    public SingletonBean() {
        ApplicationProperties.load();
    }

}
