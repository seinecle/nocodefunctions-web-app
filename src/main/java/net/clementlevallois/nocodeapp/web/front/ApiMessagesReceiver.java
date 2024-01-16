/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;

/**
 *
 * @author LEVALLOIS
 */
@Path("/messageFromAPI")
public class ApiMessagesReceiver {

    public static ConcurrentHashMap<String, ArrayList<MessageFromApi>> queueResultsArrived = new ConcurrentHashMap();

    @POST
    @Path("/cowo")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response messagesFromCowoAPI(MessageFromApi msg) {
        if (msg.getInfo().equals(Information.RESULT_ARRIVED)) {
            ArrayList<MessageFromApi> messages = queueResultsArrived.getOrDefault(msg.getSessionId(), new ArrayList());
            messages.add(0,msg);
            queueResultsArrived.put(msg.getSessionId(), messages);
        } else {
            SingletonBean.addMessageFromApi(msg);
        }
        return Response.ok().build();
    }

    @Path("/hello")
    @GET
    public String sayHello() {
        return "Hello World";
    }

}
