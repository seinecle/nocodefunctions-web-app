package net.clementlevallois.nocodeapp.web.front.logview;

import java.io.Serializable;
import java.util.List;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class LogBean implements Serializable {

    private final List<Notification> notifications;

    @Inject
    @jakarta.faces.push.Push
    private jakarta.faces.push.PushContext push;

    public LogBean() {
        notifications = new ArrayList();
    }

    public void addOneNotificationFromString(String notificationAsString) {
        Notification notification = new Notification();
        notification.setMessage(notificationAsString);
        notifications.add(0, notification);
        push.send(notificationAsString);
    }

    public List<Notification> getNotifications() {
        return notifications;
    }
}
