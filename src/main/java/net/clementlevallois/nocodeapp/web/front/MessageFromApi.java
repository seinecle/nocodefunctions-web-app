/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front;

import java.io.Serializable;

/**
 *
 * @author LEVALLOIS
 */
public class MessageFromApi implements Serializable {

    private String message;
    private Integer progress;
    private String function;
    private String jobId;
    private Information info;
    private boolean success;

    public enum Information {
        INTERMEDIARY, RESULT_ARRIVED, WORKFLOW_COMPLETED, ERROR, PROGRESS, FAILED, GOTORESULTS
    }

    public MessageFromApi() {
    }

    public MessageFromApi(String jobId, boolean success, String message) {
        this.jobId = jobId;
        this.success = success;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public Information getInfo() {
        return info;
    }

    public void setInfo(Information info) {
        this.info = info;
    }

    public String getjobId() {
        return jobId;
    }

    public void setjobId(String jobId) {
        this.jobId = jobId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }
    
    
}
