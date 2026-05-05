package com.aegispay.common.domain.exception;

import com.aegispay.common.domain.enums.KycStatus;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class KycNotApprovedException extends AegisPayException {

    public KycNotApprovedException(UUID userId, KycStatus currentStatus) {
        super("KYC_NOT_APPROVED",
              String.format("User %s cannot initiate transactions. KYC status: %s",
                            userId, currentStatus),
              HttpStatus.FORBIDDEN);
    }
}
