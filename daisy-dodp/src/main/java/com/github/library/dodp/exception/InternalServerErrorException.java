package com.github.library.dodp.exception;

/**
 * The server reported an internal error.
 */
public class InternalServerErrorException extends DodpFaultException {

    public InternalServerErrorException(String reason) {
        super("internalServerErrorFault", reason);
    }
}
