package com.dayo.careround.caretaskservice.exception;

public class OutboxEventNotFoundException extends RuntimeException {
    public OutboxEventNotFoundException(String message) {
        super(message);
    }
}
