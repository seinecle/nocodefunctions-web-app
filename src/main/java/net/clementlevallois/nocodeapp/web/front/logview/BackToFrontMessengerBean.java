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
public class BackToFrontMessengerBean implements Serializable {

    private final List<Notification> notifications;

    @Inject
    @Push
    private PushContext logChannel;

    @Inject
    @Push
    private PushContext navigationChannel;
    private String sessionId = "";

    public BackToFrontMessengerBean() {
        notifications = new ArrayList();
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        watchIncomingMessages();
    }

    private void watchIncomingMessages() {
        Executors.newSingleThreadExecutor().execute(() -> {
            while (WatchTower.getCurrentSessions().containsKey(sessionId)) {
                ConcurrentLinkedDeque<MessageFromApi> messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
                if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
                    Iterator<MessageFromApi> it = messagesFromApi.iterator();
                    while (it.hasNext()) {
                        MessageFromApi msg = it.next();
                        if (msg.getInfo().equals(Information.INTERMEDIARY)) {
                            addOneNotificationFromString(msg.getMessage());
                            it.remove();
                        } else if (msg.getInfo().equals(Information.GOTORESULTS)) {
                            navigateToResultsPage(msg.getFunction());
                            it.remove();
                        }
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    Logger.getLogger(BackToFrontMessengerBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    public void addOneNotificationFromString(String notificationAsString) {
        Notification notification = new Notification();
        notification.setMessage(notificationAsString);
        notifications.add(0, notification);
        // because what is sent to the frontend is not the incoming message!
        // it is just the arbitrary name of an event, here "updateNotifications"
        // this will cause the ajax to trigger the rendering of the log
        // and the newest messages will appear in the log...
        logChannel.send("updateNotifications");
    }

    public void navigateToResultsPage(String functionName) {
        navigationChannel.send("navigateToResults" + functionName);
    }

    public List<Notification> getNotifications() {
        return notifications;
    }
}
