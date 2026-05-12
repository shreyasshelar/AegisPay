package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.KycStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor
@SuperBuilder
public class KycStatusChangedEvent extends BaseEvent {

    private UUID userId;
    private KycStatus previousStatus;
    private KycStatus newStatus;
    private String rejectionReason;
    private String documentType;
}
