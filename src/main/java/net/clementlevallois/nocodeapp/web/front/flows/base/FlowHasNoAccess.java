package net.clementlevallois.nocodeapp.web.front.flows.base;

public record FlowHasNoAccess(String jobId, FlowState lastKnownState, String userMessage, String logMessage) implements FlowState {
    
    public FlowHasNoAccess(String jobId, FlowState lastKnownState, String userMessage) {
        this(jobId, lastKnownState, userMessage, userMessage);
    }
}
