package com.dayo.careround.caretaskservice.data.models.enums;

import com.dayo.careround.caretaskservice.exception.BadRequestException;

// Named priority. The int priority column stays the stored value; the level is derived from it
// (1 or less -> LOW, 2 -> MEDIUM, 3 or more -> HIGH), and a level sent by a client is stored as 1 / 2 / 3.
public enum PriorityLevel {
    LOW(1), MEDIUM(2), HIGH(3);

    private final int value;

    PriorityLevel(int value) {
        this.value = value;
    }

    public int toInt() {
        return value;
    }

    public static PriorityLevel fromInt(int priority) {
        if (priority <= 1) return LOW;
        if (priority == 2) return MEDIUM;
        return HIGH;
    }

    // The int to store for a request that may carry either form. Neither -> 400; both but disagreeing -> 400.
    public static int resolve(Integer priority, PriorityLevel level, String field) {
        if (priority == null && level == null) {
            throw new BadRequestException(field + "Level (LOW, MEDIUM, HIGH) is required");
        }
        if (level == null) return priority;
        if (priority != null && fromInt(priority) != level) {
            throw new BadRequestException(field + " " + priority + " (" + fromInt(priority) + ") disagrees with "
                    + field + "Level " + level);
        }
        return priority != null ? priority : level.toInt();
    }
}
