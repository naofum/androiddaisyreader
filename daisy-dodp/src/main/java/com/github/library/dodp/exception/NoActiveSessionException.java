package com.github.library.dodp.exception;

/**
 * The session has expired or the operation was called before {@code logOn}.
 */
public class NoActiveSessionException extends DodpFaultException {

    public NoActiveSessionException(String reason) {
        super("noActiveSessionFault", reason);
    }
}
