package com.github.library.dodp.exception;

/**
 * Thrown when the DODP service cannot be reached, returns a non-SOAP response,
 * or when a response cannot be parsed.
 */
public class DodpTransportException extends DodpException {

    public DodpTransportException(String message) {
        super(message);
    }

    public DodpTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
