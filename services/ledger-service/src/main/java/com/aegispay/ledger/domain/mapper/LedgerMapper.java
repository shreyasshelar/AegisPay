package com.aegispay.ledger.domain.mapper;

import com.aegispay.ledger.domain.dto.AccountResponse;
import com.aegispay.ledger.domain.dto.LedgerEntryResponse;
import com.aegispay.ledger.domain.entity.Account;
import com.aegispay.ledger.domain.entity.LedgerEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LedgerMapper {

    @Mapping(target = "totalBalance",
             expression = "java(account.getAvailableBalance().add(account.getReservedBalance()))")
    AccountResponse toAccountResponse(Account account);

    @Mapping(target = "entryType", expression = "java(entry.getEntryType().name())")
    LedgerEntryResponse toLedgerEntryResponse(LedgerEntry entry);
}
