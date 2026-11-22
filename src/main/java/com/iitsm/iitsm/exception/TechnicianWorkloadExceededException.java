package com.iitsm.iitsm.exception;

/**
 * Every technician who could take the incident already has the maximum number of active
 * incidents. A special case of "technician unavailable", so callers that handle that also
 * handle this.
 */
public class TechnicianWorkloadExceededException extends TechnicianUnavailableException {
    public TechnicianWorkloadExceededException(String message) {
        super(message);
    }
}
