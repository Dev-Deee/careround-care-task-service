package com.dayo.careround.caretaskservice.exception;

public class TaskAlreadyClaimedException extends RuntimeException {
    public TaskAlreadyClaimedException(String message) {
        super(message);
    }
}
