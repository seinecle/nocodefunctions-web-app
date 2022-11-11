/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.redisops;

import java.util.Iterator;
import java.util.Objects;
import redis.clients.jedis.Jedis;
import static redis.clients.jedis.ScanParams.SCAN_POINTER_START;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author LEVALLOIS
 */
public class Annotator {

    private String taskId;
    private String email;
    private String password;

    public Annotator(String taskId, String email, String pass) {
        this.taskId = taskId;
        this.email = email;
        this.password = pass;
    }

    public Annotator(String taskId, String emailAnnotator, Jedis jedis) {
        this.email = emailAnnotator;
        this.taskId = taskId;
//        ScanParams scanParams = new ScanParams().count(100).match(keyCode + ":data:.*");
            String cur = SCAN_POINTER_START;
            Iterator<String> iteratorScanResults;
            boolean found = false;
            do {
                ScanResult<String> scanResult = jedis.scan(cur);
                // work with result
                iteratorScanResults = scanResult.getResult().iterator();
                while (iteratorScanResults.hasNext()) {
                    String next = iteratorScanResults.next();
                    if (!next.startsWith("annotator")) {
                        continue;
                    }
                    String[] keyFields = next.split(":");
                    if (keyFields.length != 6) {
                        continue;
                    }
                    String typeOfKey = keyFields[0];
                    String taskIdInKey = keyFields[1];
                    String emailAnnotatorInKey = keyFields[3];
                    String passInKey = keyFields[5];
                    if (typeOfKey.equals("annotator") && taskIdInKey.equals(taskId) && emailAnnotatorInKey.equals(emailAnnotator)) {
                        this.email = emailAnnotator;
                        this.taskId = taskId;
                        this.password = passInKey;
                        found = true;
                        break;
                    }
                }
                cur = scanResult.getCursor();
            } while (!cur.equals(SCAN_POINTER_START) & !found);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String produceAnnotatorKey() {
        return "annotator:email:" + email + ":password:" + password;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 17 * hash + Objects.hashCode(this.taskId);
        hash = 17 * hash + Objects.hashCode(this.email);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Annotator other = (Annotator) obj;
        if (!Objects.equals(this.taskId, other.taskId)) {
            return false;
        }
        if (!Objects.equals(this.email, other.email)) {
            return false;
        }
        return true;
    }

}
