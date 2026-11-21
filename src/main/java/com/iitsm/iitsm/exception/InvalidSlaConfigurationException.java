package com.iitsm.iitsm.exception;

/** An admin tried to save an SLA policy that makes no sense (e.g. zero minutes). */
public class InvalidSlaConfigurationException extends RuntimeException {
    public InvalidSlaConfigurationException(String message) {
        super(message);
    }
}
