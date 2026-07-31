package com.dayo.careround.caretaskservice.exception;

public class UndoWindowExpiredException extends RuntimeException {
    public UndoWindowExpiredException(String message) {
        super(message);
    }
}
