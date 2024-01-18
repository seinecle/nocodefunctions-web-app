/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front;

import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@Singleton
public class WatchTower {

    public static final long THREE_HOURS_MS = 3 * 60 * 60 * 1000;

    private static final ConcurrentHashMap<String, ConcurrentLinkedDeque<MessageFromApi>> dequeAPIMessages = new ConcurrentHashMap();
    private static final ConcurrentHashMap<String, Long> queueOutcomesProcesses = new ConcurrentHashMap();
    private static final ConcurrentHashMap<String, Long> currentSessions = new ConcurrentHashMap();

    public static ConcurrentHashMap<String, ConcurrentLinkedDeque<MessageFromApi>> getDequeAPIMessages() {
        return dequeAPIMessages;
    }

    public static ConcurrentHashMap<String, Long> getQueueOutcomesProcesses() {

        Long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = queueOutcomesProcesses.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if ((currentTime - entry.getValue()) > THREE_HOURS_MS) {
                iterator.remove();
            }
        }
        return queueOutcomesProcesses;
    }

    public static void removeCurrentSession(String sessionId) {
        currentSessions.remove(sessionId);
    }

    public static void addCurrentSession(String sessionId) {
        Long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = currentSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if ((currentTime - entry.getValue()) > THREE_HOURS_MS) {
                iterator.remove();
            }
        }
        currentSessions.put(sessionId, System.currentTimeMillis());
    }

    public static ConcurrentHashMap<String, Long> getCurrentSessions() {
        return currentSessions;
    }
}
