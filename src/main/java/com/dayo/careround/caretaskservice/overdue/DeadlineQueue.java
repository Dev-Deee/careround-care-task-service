package com.dayo.careround.caretaskservice.overdue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Timer index of open-task deadlines. Implementations may lose data (Redis without persistence); reconciliation
// rebuilds it from Postgres, and firing always re-checks the task's state, so stale or missing entries are harmless.
public interface DeadlineQueue {

    void schedule(UUID taskPublicId, Instant at);

    void remove(UUID taskPublicId);

    // Members whose time has come, oldest first, at most limit.
    List<UUID> due(Instant now, int limit);

    // Atomic claim: true for exactly one caller per scheduled member.
    boolean claim(UUID taskPublicId);
}
