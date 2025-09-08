package net.clementlevallois.nocodeapp.web.front.flows.base;

public record FlowFailed(String jobId, FlowState lastKnownState, String userMessage, String logMessage) implements FlowState {
    
}