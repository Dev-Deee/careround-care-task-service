package com.dayo.careround.caretaskservice.exception;

public class InvalidTaskStateException extends RuntimeException {
    public InvalidTaskStateException(String message) {
        super(message);
    }
}
