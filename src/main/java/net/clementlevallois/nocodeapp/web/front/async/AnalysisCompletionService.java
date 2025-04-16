package net.clementlevallois.nocodeapp.web.front.async; // Choose an appropriate package

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@ApplicationScoped
public class AnalysisCompletionService {

    private static final Logger LOG = Logger.getLogger(AnalysisCompletionService.class.getName());

    // Map: uniqueTaskId -> Future that will complete with the result identifier (e.g., path)
    private final Map<String, CompletableFuture<String>> pendingFutures = new ConcurrentHashMap<>();

    /**
     * Creates and registers a CompletableFuture associated with a unique task ID.
     * The Session Bean calls this before initiating the async task.
     *
     * @param taskId A unique ID for the analysis task.
     * @return The CompletableFuture that will eventually hold the result identifier.
     */
    public CompletableFuture<String> registerFuture(String taskId) {
        LOG.log(Level.INFO, "Registering future for task ID: {0}", taskId);
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingFutures.put(taskId, future);
        // Optional: Add timeout logic here using future.completeOnTimeout() if needed
        return future;
    }

    /**
     * Called by the callback receiver (e.g., ApiMessagesReceiver) when the result is ready.
     *
     * @param taskId The unique ID of the completed task.
     * @param resultIdentifier The identifier for the result (e.g., file path).
     */
    public void completeFuture(String taskId, String resultIdentifier) {
        CompletableFuture<String> future = pendingFutures.remove(taskId); // Remove when completing
        if (future != null) {
            LOG.log(Level.INFO, "Completing future for task ID: {0} with result: {1}", new Object[]{taskId, resultIdentifier});
            future.complete(resultIdentifier);
        } else {
            LOG.log(Level.WARNING, "Attempted to complete future for unknown or already completed task ID: {0}", taskId);
        }
    }

    /**
     * Called by the callback receiver if the async task reported an error.
     *
     * @param taskId The unique ID of the failed task.
     * @param throwable The exception that occurred.
     */
    public void completeFutureExceptionally(String taskId, Throwable throwable) {
        CompletableFuture<String> future = pendingFutures.remove(taskId); // Remove when completing
        if (future != null) {
            LOG.log(Level.WARNING, "Completing future exceptionally for task ID: {0}", taskId);
            future.completeExceptionally(throwable);
        } else {
            LOG.log(Level.WARNING, "Attempted to complete future exceptionally for unknown or already completed task ID: {0}", taskId);
        }
    }

    /**
    * Optional: Called if a task needs explicit cleanup (e.g., session expired).
    *
    * @param taskId The unique ID of the task to clean up.
    */
    public void cancelAndCleanupFuture(String taskId) {
        CompletableFuture<String> future = pendingFutures.remove(taskId);
        if (future != null && !future.isDone()) {
             LOG.log(Level.INFO, "Cancelling and cleaning up future for task ID: {0}", taskId);
             future.cancel(true); // Attempt cancellation
        }
    }
}