package com.github.library.dodp.exception;

/**
 * A request referenced a content id or other parameter that does not exist or
 * is otherwise invalid.
 */
public class InvalidParameterException extends DodpFaultException {

    public InvalidParameterException(String reason) {
        super("invalidParameterFault", reason);
    }
}
