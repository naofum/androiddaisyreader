package com.github.library.dodp.exception;

/**
 * An operation was invoked before the initialization sequence
 * ({@code logOn}, {@code getServiceAttributes},
 * {@code setReadingSystemAttributes}) had completed.
 */
public class InvalidOperationException extends DodpFaultException {

    public InvalidOperationException(String reason) {
        super("invalidOperationFault", reason);
    }
}
