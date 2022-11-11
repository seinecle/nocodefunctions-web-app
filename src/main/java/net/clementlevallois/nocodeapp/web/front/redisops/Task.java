/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.redisops;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author LEVALLOIS
 */
public class Task {

    String emailDesigner;
    String taskId;
    Set<Annotator> annotators = new HashSet();
    TaskMetadata metadata;
    List<String> keysSeriesBlocks = new ArrayList();

    public Task(String taskId) {
        this.taskId = taskId;
    }

    public String getEmailDesigner() {
        return emailDesigner;
    }

    public void setEmailDesigner(String emailDesigner) {
        this.emailDesigner = emailDesigner;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Set<Annotator> getAnnotators() {
        return annotators;
    }

    public void setAnnotators(Set<Annotator> annotators) {
        this.annotators = annotators;
    }

    public TaskMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(TaskMetadata metadata) {
        this.metadata = metadata;
    }

    public List<String> getKeysSeriesBlocks() {
        return keysSeriesBlocks;
    }

    public void setKeysSeriesBlocks(List<String> keysSeriesBlocks) {
        this.keysSeriesBlocks = keysSeriesBlocks;
    }

    public String addAKeyForNewBlockSeries() {
        Integer nextIndex = keysSeriesBlocks.size() + 1;
        String newKey = taskId + ":blocks:series:" + nextIndex;
        keysSeriesBlocks.add(newKey);
        return newKey;
    }

}
