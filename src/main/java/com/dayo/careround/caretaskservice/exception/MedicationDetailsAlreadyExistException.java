package com.dayo.careround.caretaskservice.exception;

public class MedicationDetailsAlreadyExistException extends RuntimeException {
    public MedicationDetailsAlreadyExistException(String message) {
        super(message);
    }
}
