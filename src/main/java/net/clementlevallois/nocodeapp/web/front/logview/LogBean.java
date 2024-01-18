package net.clementlevallois.nocodeapp.web.front.logview;

import java.io.Serializable;
import java.util.List;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.push.Push;
import jakarta.faces.push.PushContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information;
import net.clementlevallois.nocodeapp.web.front.WatchTower;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class LogBean implements Serializable {

    private final List<Notification> notifications;

    @Inject
    @Push
    private PushContext push;
    private String sessionId = "";

    public LogBean() {
        notifications = new ArrayList();
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        watchIncomingMessages();
    }

    private void watchIncomingMessages() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true && WatchTower.getCurrentSessions().containsKey(sessionId)) {
                ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
                if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
                    Iterator<MessageFromApi> it = messagesFromApi.iterator();
                    while (it.hasNext()) {
                        MessageFromApi msg = it.next();
                        if (msg.getInfo().equals(Information.INTERMEDIARY)) {
                            addOneNotificationFromString(msg.getMessage());
                            it.remove();
                        }
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(LogBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
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
