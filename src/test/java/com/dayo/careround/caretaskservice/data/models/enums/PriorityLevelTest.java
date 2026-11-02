package com.dayo.careround.caretaskservice.data.models.enums;

import com.dayo.careround.caretaskservice.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriorityLevelTest {

    @Test
    void intMapsToLevel() {
        assertEquals(PriorityLevel.LOW, PriorityLevel.fromInt(0));
        assertEquals(PriorityLevel.LOW, PriorityLevel.fromInt(1));
        assertEquals(PriorityLevel.MEDIUM, PriorityLevel.fromInt(2));
        assertEquals(PriorityLevel.HIGH, PriorityLevel.fromInt(3));
        assertEquals(PriorityLevel.HIGH, PriorityLevel.fromInt(7));
    }

    @Test
    void levelIsStoredAsItsCanonicalInt() {
        assertEquals(1, PriorityLevel.resolve(null, PriorityLevel.LOW, "priority"));
        assertEquals(2, PriorityLevel.resolve(null, PriorityLevel.MEDIUM, "priority"));
        assertEquals(3, PriorityLevel.resolve(null, PriorityLevel.HIGH, "priority"));
    }

    @Test
    void legacyIntOnlyIsKeptAsSent() {
        assertEquals(5, PriorityLevel.resolve(5, null, "priority"));
    }

    @Test
    void agreeingPairIsAccepted() {
        assertEquals(4, PriorityLevel.resolve(4, PriorityLevel.HIGH, "priority"));
        assertEquals(2, PriorityLevel.resolve(2, PriorityLevel.MEDIUM, "priority"));
    }

    @Test
    void disagreeingPairIs400() {
        assertThrows(BadRequestException.class, () -> PriorityLevel.resolve(1, PriorityLevel.HIGH, "priority"));
        assertThrows(BadRequestException.class, () -> PriorityLevel.resolve(3, PriorityLevel.MEDIUM, "priority"));
    }

    @Test
    void neitherIs400() {
        assertThrows(BadRequestException.class, () -> PriorityLevel.resolve(null, null, "priority"));
    }
}
