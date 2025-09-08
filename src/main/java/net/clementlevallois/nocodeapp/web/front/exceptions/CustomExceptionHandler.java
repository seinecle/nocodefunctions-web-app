package net.clementlevallois.nocodeapp.web.front.exceptions;

import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;

public class CustomExceptionHandler extends ExceptionHandlerWrapper {

    private final ExceptionHandler wrapped;

    public CustomExceptionHandler(ExceptionHandler wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public ExceptionHandler getWrapped() {
        return wrapped;
    }

    @Override
    public void handle() {
        final Iterator<ExceptionQueuedEvent> i = getUnhandledExceptionQueuedEvents().iterator();
        while (i.hasNext()) {
            ExceptionQueuedEvent event = i.next();
            ExceptionQueuedEventContext context = (ExceptionQueuedEventContext) event.getSource();
            Throwable t = context.getException();
            FacesContext fc = FacesContext.getCurrentInstance();
            Map<String, Object> requestMap = fc.getExternalContext().getRequestMap();
            try {
                if (t instanceof NocodeApplicationException) {
                    requestMap.put("errorMsg", t.getMessage());
                    fc.getExternalContext().dispatch("/error.xhtml");
                } else {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    t.printStackTrace(pw);
                    requestMap.put("errorMsg", "An unexpected error has occurred.");
                    requestMap.put("stackTrace", sw.toString());
                    fc.getExternalContext().dispatch("/error.xhtml");
                }
            } catch (IOException ex) {
                System.getLogger(CustomExceptionHandler.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            } finally {
                i.remove();
            }
        }
        getWrapped().handle();
    }
}