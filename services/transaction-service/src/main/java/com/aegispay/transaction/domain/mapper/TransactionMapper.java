package com.aegispay.transaction.domain.mapper;

import com.aegispay.common.domain.enums.TransactionStatus;
import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.domain.dto.TransactionStatusResponse;
import com.aegispay.transaction.domain.entity.Transaction;
import com.aegispay.transaction.readmodel.TransactionView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "note",
             expression = "java(transaction.getMetadata() != null ? " +
                          "(String) transaction.getMetadata().get(\"note\") : null)")
    TransactionResponse toResponse(Transaction transaction);

    @Mapping(target = "transactionId", expression = "java(java.util.UUID.fromString(view.getId()))")
    @Mapping(target = "aiExplanation",   source = "aiExplanation")
    @Mapping(target = "failureReason",   source = "failureReason")
    @Mapping(target = "failureCode",     source = "failureCode")
    TransactionStatusResponse toStatusResponse(TransactionView view);

    /** Maps a MongoDB read-model view to the full response shape used by the list endpoint. */
    default TransactionResponse toListItemResponse(TransactionView view) {
        if (view == null) return null;
        return TransactionResponse.builder()
                .id(UUID.fromString(view.getId()))
                .userId(view.getUserId() != null ? UUID.fromString(view.getUserId()) : null)
                .payerId(view.getPayerId() != null ? UUID.fromString(view.getPayerId()) : null)
                .payeeId(view.getPayeeId() != null ? UUID.fromString(view.getPayeeId()) : null)
                .amount(view.getAmount())
                .currency(view.getCurrency())
                .status(view.getStatus() != null ? TransactionStatus.valueOf(view.getStatus()) : null)
                .idempotencyKey(null)
                .sagaId(null)
                .note(null)
                .initiatedAt(view.getInitiatedAt())
                .completedAt(view.getCompletedAt())
                .failureReason(view.getFailureReason())
                .failureCode(view.getFailureCode())
                .externalReference(view.getExternalReference())
                .build();
    }
}
