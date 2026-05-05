package com.aegispay.transaction.idempotency;

import com.aegispay.common.domain.exception.DuplicateIdempotencyKeyException;
import com.aegispay.transaction.config.TransactionServiceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:txn-service:";

    private final StringRedisTemplate redisTemplate;
    private final TransactionServiceProperties props;

    public void claim(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        Duration ttl = Duration.ofSeconds(props.getIdempotency().getTtlSeconds());

        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "processing", ttl);

        if (!Boolean.TRUE.equals(isNew)) {
            log.warn("Duplicate idempotency key: {}", idempotencyKey);
            throw new DuplicateIdempotencyKeyException(idempotencyKey);
        }
    }
}
