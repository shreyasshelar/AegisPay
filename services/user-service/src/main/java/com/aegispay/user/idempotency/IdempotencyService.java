package com.aegispay.user.idempotency;

import com.aegispay.common.domain.exception.DuplicateIdempotencyKeyException;
import com.aegispay.user.config.UserServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:user-service:";

    private final StringRedisTemplate redisTemplate;
    private final UserServiceProperties props;

    /**
     * Attempts to claim the idempotency key.
     * Throws {@link DuplicateIdempotencyKeyException} if the key was already claimed
     * (i.e. the request was already processed or is in flight).
     */
    public void claim(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Duration ttl = Duration.ofSeconds(props.getIdempotency().getTtlSeconds());

        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "processing", ttl);

        if (!Boolean.TRUE.equals(isNew)) {
            log.warn("Duplicate idempotency key detected: {}", idempotencyKey);
            throw new DuplicateIdempotencyKeyException(idempotencyKey);
        }
    }

    public void release(String idempotencyKey) {
        redisTemplate.delete(KEY_PREFIX + idempotencyKey);
    }
}
