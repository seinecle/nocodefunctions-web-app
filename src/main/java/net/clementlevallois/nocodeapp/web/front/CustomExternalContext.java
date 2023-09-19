package net.clementlevallois.nocodeapp.web.front;

import jakarta.faces.FacesException;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.ExternalContextFactory;
import jakarta.faces.context.ExternalContextWrapper;

/**
 *
 * @author LEVALLOIS
 */
public class CustomExternalContext extends ExternalContextWrapper {

    public CustomExternalContext(ExternalContext wrapped) {
        super(wrapped);
    }

    @Override
    public String encodeWebsocketURL(String url) {
        // this adds an s to ws when we are in production on linux, where https applie
        //but if we are locally on windows, with http, nothing to add and just return.
        String property = System.getProperty("os.name");
        System.out.println("OS: " + property);
        if (!property.toLowerCase().contains("inux")) {
            return super.encodeWebsocketURL(url);
        } else {
            return super.encodeWebsocketURL(url).replaceFirst("ws://", "wss://");
        }
    }

    public static class Factory extends ExternalContextFactory {

        public Factory(ExternalContextFactory wrapped) {
            super(wrapped);
        }

        @Override
        public ExternalContext getExternalContext(Object context, Object request, Object response) throws FacesException {
            return new CustomExternalContext(getWrapped().getExternalContext(context, request, response));
        }
    }
}
