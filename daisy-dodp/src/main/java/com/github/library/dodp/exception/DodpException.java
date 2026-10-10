package com.github.library.dodp.exception;

/**
 * Base exception for all DODP client errors. SOAP faults and transport errors
 * are both represented as {@code DodpException} subclasses.
 */
public class DodpException extends Exception {

    public DodpException(String message) {
        super(message);
    }

    public DodpException(String message, Throwable cause) {
        super(message, cause);
    }
}
