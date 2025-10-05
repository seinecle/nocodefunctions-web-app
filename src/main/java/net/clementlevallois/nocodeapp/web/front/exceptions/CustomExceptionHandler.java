package net.clementlevallois.nocodeapp.web.front.exceptions;

import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
            Map<String, Object> flashMap = fc.getExternalContext().getFlash();
            
            if (flashMap == null){
                flashMap = new HashMap();
            }

            if (t instanceof NocodeApplicationException) {
                flashMap.put("errorMsg", t.getMessage());
            } else {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                flashMap.put("errorMsg", t.getMessage());
                flashMap.put("stackTrace", sw.toString());

            }
            if (t instanceof ViewExpiredException) {
                fc.getApplication().getNavigationHandler().handleNavigation(fc, null, "/index.xhtml?faces-redirect=true");
            } else {
                fc.getApplication().getNavigationHandler().handleNavigation(fc, null, "/error.xhtml?faces-redirect=true");
            }
            i.remove();
        }
        getWrapped().handle();
    }
}
