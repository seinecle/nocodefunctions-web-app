package net.clementlevallois.nocodeapp.web.front.exceptions;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class NocodeApplicationException extends RuntimeException {

    public NocodeApplicationException() {
        super();
    }

    public NocodeApplicationException(String message) {
        super(message);
    }

    public NocodeApplicationException(String message, Throwable cause) {
        super(message, cause);
    }

    public NocodeApplicationException(Throwable cause) {
        super(cause);
    }
}