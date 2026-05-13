package com.aegispay.common.domain.event;

import com.aegispay.common.domain.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@SuperBuilder
public class KycStatusChangedEvent extends BaseEvent {

    private UUID userId;
    private KycStatus previousStatus;
    private KycStatus newStatus;
    private String rejectionReason;
    private String documentType;
}
