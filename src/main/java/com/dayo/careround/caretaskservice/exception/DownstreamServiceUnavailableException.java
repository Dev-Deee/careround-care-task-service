package com.dayo.careround.caretaskservice.exception;

// patient-service or ward-service could not answer; the request is refused (fail closed) with 503.
public class DownstreamServiceUnavailableException extends RuntimeException {
    public DownstreamServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
