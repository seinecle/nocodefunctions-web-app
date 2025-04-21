/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author LEVALLOIS
 */
@ApplicationScoped
@Path("/messageFromAPI")
public class ApiMessagesReceiver {

    private static final Logger LOG = Logger.getLogger(ApiMessagesReceiver.class.getName());
    private static final long serialVersionUID = 1L; // Good practice for Serializable

    @Inject
    private Event<MessageFromApi> messageFromApiEvent;

    @POST
    @Path("/cowo")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromCowoAPI(MessageFromApi msg) {
        ConcurrentLinkedDeque<MessageFromApi> messages = WatchTower.getDequeAPIMessages().getOrDefault(msg.getSessionId(), new ConcurrentLinkedDeque());
        messages.addLast(msg);
        WatchTower.getDequeAPIMessages().put(msg.getSessionId(), messages);
        return Response.ok().build();
    }

    @POST
    @Path("/workflow/topics") // Assuming this is the callback endpoint for topics
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromTopicsAPI(MessageFromApi msg) {
        LOG.log(Level.INFO, "Received callback for session {0}, task {1}, info {2}",
                new Object[]{msg.getSessionId(), msg.getDataPersistenceId(), msg.getInfo()});

        ConcurrentLinkedDeque<MessageFromApi> messages = WatchTower.getDequeAPIMessages().getOrDefault(msg.getSessionId(), new ConcurrentLinkedDeque());
        messages.addLast(msg);
        WatchTower.getDequeAPIMessages().put(msg.getSessionId(), messages);

        boolean success;
        if (msg.getInfo() == MessageFromApi.Information.WORKFLOW_COMPLETED && msg.getDataPersistenceId() != null && msg.getSessionId() != null) {
            success = true;
            messageFromApiEvent.fire(new MessageFromApi(msg.getDataPersistenceId(), success, msg.getMessage()));
        } else if (msg.getInfo() == MessageFromApi.Information.ERROR && msg.getDataPersistenceId() != null) {
            success = false;
        } else {
            success = false;
            // Log other message types if needed
            LOG.log(Level.FINE, "Received other message type: {0}", msg.getInfo());
        }

        return Response.ok().build();
    }

    @POST
    @Path("/gaze")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromGazeAPI(MessageFromApi msg) {
        ConcurrentLinkedDeque<MessageFromApi> messages = WatchTower.getDequeAPIMessages().getOrDefault(msg.getSessionId(), new ConcurrentLinkedDeque());
        messages.addLast(msg);
        WatchTower.getDequeAPIMessages().put(msg.getSessionId(), messages);
        return Response.ok().build();
    }

    @Path("/hello")
    @GET
    public String sayHello() {
        return "Hello World";
    }

}
