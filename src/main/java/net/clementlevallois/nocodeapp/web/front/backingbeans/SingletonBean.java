package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class SingletonBean {

    private static final ConcurrentHashMap<String, ArrayList<MessageFromApi>> messagesFromApi = new ConcurrentHashMap();
    private static final ConcurrentHashMap<String, Integer> currentSessions = new ConcurrentHashMap();

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

    public static void addMessageFromApi(MessageFromApi msg) {
        String sessionId = msg.getSessionId();
        ArrayList<MessageFromApi> msgs;
        if (messagesFromApi.containsKey(sessionId)) {
            msgs = messagesFromApi.get(sessionId);
        } else {
            msgs = new ArrayList();
        }
        msgs.add(0,msg);
        messagesFromApi.put(sessionId, msgs);
    }

    public static ArrayList<MessageFromApi> takeMessagesFromApi(String sessionId) {
        return messagesFromApi.remove(sessionId);
    }

    public static void removeCurrentSession(String sessionId) {
        currentSessions.remove(sessionId);
    }

    public static void addCurrentSession(String sessionId) {
        currentSessions.put(sessionId, 1);
    }

    public static ConcurrentHashMap<String, Integer> getCurrentSessions() {
        return currentSessions;
    }
    
    

}
