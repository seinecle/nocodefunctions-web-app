package net.clementlevallois.nocodeapp.web.front.logview;

import java.io.Serializable;
import java.util.List;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.omnifaces.cdi.Push;
import org.omnifaces.cdi.PushContext;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class LogBean implements Serializable {

    private List<Notification> notifications;

    @Inject
    private NotificationService service;

    @Inject
    @Push
    private PushContext push;

    @PostConstruct
    public void load() {
        notifications = service.list();
    }

    public void onNewNotification(@Observes Notification newNotification) {
        notifications.add(0, newNotification);
        push.send("updateNotifications");
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

}
