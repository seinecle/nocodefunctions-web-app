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

    private String sessionId;
    private String message;
    private String function;
    private String dataPersistenceId;
    private Information info;
    private boolean success;

    public enum Information {
        INTERMEDIARY, RESULT_ARRIVED, WORKFLOW_COMPLETED, ERROR, PROGRESS, FAILED, GOTORESULTS
    }

    public MessageFromApi() {
    }

    public MessageFromApi(String dataPersistenceId, boolean success, String message) {
        this.dataPersistenceId = dataPersistenceId;
        this.success = success;
        this.message = message;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public String getDataPersistenceId() {
        return dataPersistenceId;
    }

    public void setDataPersistenceId(String dataPersistenceId) {
        this.dataPersistenceId = dataPersistenceId;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
