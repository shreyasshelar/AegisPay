package com.aegispay.transaction.domain.mapper;

import com.aegispay.transaction.domain.dto.TransactionResponse;
import com.aegispay.transaction.domain.entity.Transaction;
import com.aegispay.transaction.domain.dto.TransactionStatusResponse;
import com.aegispay.transaction.readmodel.TransactionView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "note",
             expression = "java(transaction.getMetadata() != null ? " +
                          "(String) transaction.getMetadata().get(\"note\") : null)")
    TransactionResponse toResponse(Transaction transaction);

    @Mapping(target = "transactionId", expression = "java(java.util.UUID.fromString(view.getId()))")
    @Mapping(target = "aiExplanation", source = "aiExplanation")
    TransactionStatusResponse toStatusResponse(TransactionView view);
}
