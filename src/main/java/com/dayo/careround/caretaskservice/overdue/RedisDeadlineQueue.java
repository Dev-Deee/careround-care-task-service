package com.dayo.careround.caretaskservice.overdue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

// Redis ZSET: member = task publicId, score = deadline epoch-ms. ZREM is the claim, so several instances can poll
// the same key and each member is processed once.
@Component
public class RedisDeadlineQueue implements DeadlineQueue {

    private final StringRedisTemplate redis;
    private final String key;

    public RedisDeadlineQueue(StringRedisTemplate redis, @Value("${app.overdue.queue-key:careround:task-deadlines}") String key) {
        this.redis = redis;
        this.key = key;
    }

    @Override
    public void schedule(UUID taskPublicId, Instant at) {
        redis.opsForZSet().add(key, taskPublicId.toString(), at.toEpochMilli());
    }

    @Override
    public void remove(UUID taskPublicId) {
        redis.opsForZSet().remove(key, taskPublicId.toString());
    }

    @Override
    public List<UUID> due(Instant now, int limit) {
        Set<String> members = redis.opsForZSet().rangeByScore(key, Double.NEGATIVE_INFINITY, now.toEpochMilli(), 0, limit);
        return members == null ? List.of() : members.stream().map(UUID::fromString).toList();
    }

    @Override
    public boolean claim(UUID taskPublicId) {
        Long removed = redis.opsForZSet().remove(key, taskPublicId.toString());
        return removed != null && removed == 1L;
    }
}
