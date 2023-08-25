package net.clementlevallois.nocodeapp.web.front.logview;

import java.util.ArrayList;
import java.util.List;
import jakarta.ejb.Stateless;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

/**
 *
 * @author LEVALLOIS
 */
@Stateless
public class NotificationService {

    @Inject
    private BeanManager beanManager;

    public void create(String message) {
        Notification newNotification = new Notification();
        newNotification.setMessage(message);
        beanManager.getEvent().fire(newNotification);
    }
}
