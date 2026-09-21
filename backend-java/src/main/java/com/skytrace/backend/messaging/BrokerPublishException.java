package com.skytrace.backend.messaging;

public class BrokerPublishException extends RuntimeException {

    public BrokerPublishException(String message) {
        super(message);
    }

    public BrokerPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
