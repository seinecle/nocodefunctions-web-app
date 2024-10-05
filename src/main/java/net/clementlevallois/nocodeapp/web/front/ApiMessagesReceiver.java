/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 *
 * @author LEVALLOIS
 */
@Path("/messageFromAPI")
public class ApiMessagesReceiver {

    @POST
    @Path("/cowo")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromCowoAPI(MessageFromApi msg) {
        ConcurrentLinkedDeque <MessageFromApi> messages = WatchTower.getDequeAPIMessages().getOrDefault(msg.getSessionId(), new ConcurrentLinkedDeque ());
        messages.addLast(msg);
        WatchTower.getDequeAPIMessages().put(msg.getSessionId(), messages);
        return Response.ok().build();
    }

    @POST
    @Path("/topics")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromTopicsAPI(MessageFromApi msg) {
        ConcurrentLinkedDeque <MessageFromApi> messages = WatchTower.getDequeAPIMessages().getOrDefault(msg.getSessionId(), new ConcurrentLinkedDeque ());
        messages.addLast(msg);
        WatchTower.getDequeAPIMessages().put(msg.getSessionId(), messages);
        return Response.ok().build();
    }

    @POST
    @Path("/gaze")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromGazeAPI(MessageFromApi msg) {
        ConcurrentLinkedDeque <MessageFromApi> messages = WatchTower.getDequeAPIMessages().getOrDefault(msg.getSessionId(), new ConcurrentLinkedDeque ());
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
