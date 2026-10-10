package com.github.library.dodp.exception;

/**
 * Represents a SOAP fault returned by the DODP service. The fault carries the
 * name of the fault element (e.g. {@code noActiveSessionFault}) and an optional
 * {@code reason} string provided by the server.
 */
public class DodpFaultException extends DodpException {

    private final String faultName;
    private final String reason;

    public DodpFaultException(String faultName, String reason) {
        super(buildMessage(faultName, reason));
        this.faultName = faultName;
        this.reason = reason;
    }

    private static String buildMessage(String faultName, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return faultName == null ? "Unknown DODP SOAP fault" : faultName;
        }
        return faultName + ": " + reason;
    }

    public String getFaultName() {
        return faultName;
    }

    public String getReason() {
        return reason;
    }

    /**
     * Maps a fault element local name to the matching concrete exception.
     */
    public static DodpFaultException fromFault(String faultName, String reason) {
        if (faultName == null) {
            return new DodpFaultException(null, reason);
        }
        switch (faultName) {
            case "noActiveSessionFault":
                return new NoActiveSessionException(reason);
            case "invalidParameterFault":
                return new InvalidParameterException(reason);
            case "invalidOperationFault":
                return new InvalidOperationException(reason);
            case "operationNotSupportedFault":
                return new OperationNotSupportedException(reason);
            case "internalServerErrorFault":
                return new InternalServerErrorException(reason);
            default:
                return new DodpFaultException(faultName, reason);
        }
    }
}
