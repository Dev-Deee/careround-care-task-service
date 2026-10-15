package com.dayo.careround.caretaskservice.data.models.enums;

public enum OutboxStatus {
    PENDING,
    SENT,
    DELIVERED,
    FAILED,
    DEAD_LETTER
}