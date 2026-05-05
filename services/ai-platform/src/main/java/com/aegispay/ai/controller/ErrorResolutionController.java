package com.aegispay.ai.controller;

import com.aegispay.ai.error.ErrorResolutionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/errors")
@RequiredArgsConstructor
public class ErrorResolutionController {

    private final ErrorResolutionService errorResolutionService;

    @PostMapping("/resolve")
    public ResponseEntity<ErrorResolutionService.ErrorResolutionResponse> resolve(
            @Valid @RequestBody ResolveRequest request) {
        ErrorResolutionService.ErrorResolutionResponse response =
                errorResolutionService.resolve(request.errorCode(), request.errorMessage());
        return ResponseEntity.ok(response);
    }

    public record ResolveRequest(
            @NotBlank String errorCode,
            String errorMessage
    ) {}
}
