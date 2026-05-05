package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.KycStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@SuperBuilder
public class KycStatusChangedEvent extends BaseEvent {

    private final UUID userId;
    private final KycStatus previousStatus;
    private final KycStatus newStatus;
    private final String rejectionReason;
    private final String documentType;
}
