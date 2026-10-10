package com.github.library.dodp.exception;

/**
 * The service does not support the requested optional operation.
 */
public class OperationNotSupportedException extends DodpFaultException {

    public OperationNotSupportedException(String reason) {
        super("operationNotSupportedFault", reason);
    }
}
