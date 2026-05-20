package com.aegispay.notification.controller;

import com.aegispay.common.domain.dto.ApiResponse;
import com.aegispay.common.domain.dto.PagedResponse;
import com.aegispay.notification.domain.dto.NotificationResponse;
import com.aegispay.notification.repository.NotificationRepository;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = jwt.getClaimAsString("aegispay_user_id") != null
                ? jwt.getClaimAsString("aegispay_user_id")
                : jwt.getSubject();

        Page<NotificationResponse> results = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .userId(n.getUserId())
                        .type(n.getType().name())
                        .channel(n.getChannel())
                        .status(n.getStatus())
                        .title(n.getTitle())
                        .body(n.getBody())
                        .createdAt(n.getCreatedAt())
                        .sentAt(n.getSentAt())
                        .build());

        PagedResponse<NotificationResponse> response = PagedResponse.<NotificationResponse>builder()
                .content(results.getContent())
                .page(results.getNumber())
                .size(results.getSize())
                .totalElements(results.getTotalElements())
                .totalPages(results.getTotalPages())
                .first(results.isFirst())
                .last(results.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
