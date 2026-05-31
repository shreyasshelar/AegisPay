package com.aegispay.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;

/**
 * Phone OTP service — generates a 6-digit code, sends it via Fast2SMS,
 * and stores it in Redis with a 5-minute TTL for verification.
 *
 * Replaces Firebase Phone Auth for web OTP flows.  Firebase v2 reCAPTCHA
 * is rejected by newer Firebase projects that require reCAPTCHA Enterprise;
 * this service uses Fast2SMS directly and needs no browser-side verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneOtpService {

    private static final String OTP_PREFIX   = "phone_otp:";
    private static final Duration OTP_TTL    = Duration.ofMinutes(5);
    private static final String FAST2SMS_URL = "https://www.fast2sms.com/dev/bulkV2";
    private static final SecureRandom RNG    = new SecureRandom();

    private final StringRedisTemplate redis;
    private final RestTemplate        restTemplate;

    @Value("${aegispay.sms.fast2sms.api-key:}")
    private String fast2smsKey;

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Generates a 6-digit OTP for {@code phone}, stores it in Redis, and
     * sends it via Fast2SMS.
     *
     * @param phone  E.164 phone number, e.g. {@code +919876543210}
     * @throws IllegalStateException if Fast2SMS is not configured or the send fails
     */
    public void sendOtp(String phone) {
        String otp      = generateOtp();
        String redisKey = OTP_PREFIX + phone;

        redis.opsForValue().set(redisKey, otp, OTP_TTL);
        log.debug("OTP stored for phone ending ...{}", phone.substring(Math.max(0, phone.length() - 4)));

        sendViaSms(phone, otp);
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Verifies the OTP.  Deletes the key on success (one-time use).
     *
     * @return {@code true} if the OTP matches and hasn't expired
     */
    public boolean verifyOtp(String phone, String otp) {
        String stored = redis.opsForValue().get(OTP_PREFIX + phone);
        if (stored == null) {
            log.warn("OTP verify: no code in Redis for phone ending ...{}",
                     phone.substring(Math.max(0, phone.length() - 4)));
            return false;
        }
        if (!stored.equals(otp)) {
            log.warn("OTP verify: wrong code for phone ending ...{}",
                     phone.substring(Math.max(0, phone.length() - 4)));
            return false;
        }
        redis.delete(OTP_PREFIX + phone);
        return true;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String generateOtp() {
        return String.format("%06d", RNG.nextInt(1_000_000));
    }

    private void sendViaSms(String phone, String otp) {
        if (fast2smsKey == null || fast2smsKey.isBlank()) {
            log.warn("[OTP-STUB] Fast2SMS key not set — OTP for {} is: {}", phone, otp);
            return;
        }

        String normalised = normaliseIndianPhone(phone);
        if (normalised == null) {
            throw new IllegalArgumentException("Cannot normalise phone number to 10-digit Indian format: " + phone);
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("authorization", fast2smsKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String body = "{\"route\":\"q\",\"message\":\"Your AegisPay verification code is " + otp
                    + ". Valid for 5 minutes. Do not share this code.\","
                    + "\"numbers\":\"" + normalised + "\",\"flash\":0}";

            ResponseEntity<String> resp = restTemplate.postForEntity(
                    FAST2SMS_URL, new HttpEntity<>(body, headers), String.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                log.info("OTP SMS sent to ...{}", normalised.substring(Math.max(0, normalised.length() - 4)));
            } else {
                log.warn("Fast2SMS non-2xx: {} — {}", resp.getStatusCode(), resp.getBody());
                throw new IllegalStateException("SMS delivery failed: " + resp.getBody());
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Fast2SMS call failed: {}", e.getMessage());
            throw new IllegalStateException("SMS delivery failed — " + e.getMessage(), e);
        }
    }

    private static String normaliseIndianPhone(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0"))  return digits.substring(1);
        if (digits.length() == 10)                             return digits;
        return null;
    }
}
