package com.aegispay.ledger.repository;

import com.aegispay.ledger.domain.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.userId = :userId AND a.currency = :currency")
    Optional<Account> findByUserIdAndCurrencyForUpdate(@Param("userId") UUID userId,
                                                        @Param("currency") String currency);

    List<Account> findByUserId(UUID userId);

    Optional<Account> findByUserIdAndCurrency(UUID userId, String currency);
}
