package com.dayo.careround.caretaskservice.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

// Ward supervision aggregate. byStatus/byPriority/completionRate cover tasks whose deadline falls in [from, to)
// (RETRACTED excluded). overdueDerived and oldestOverdueAgeSeconds are the ward's current open-and-late tasks,
// regardless of the window.
public record CareTaskStatsResponse(
        UUID wardId,
        Instant from,
        Instant to,
        long total,
        Map<String, Long> byStatus,
        Map<Integer, Long> byPriority,
        Double completionRate, // COMPLETED / total; null when total is 0
        long overdueDerived,
        Long oldestOverdueAgeSeconds, // null when nothing is overdue
        Instant generatedAt
) {}
