/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.logview;

import java.io.Serializable;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
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
