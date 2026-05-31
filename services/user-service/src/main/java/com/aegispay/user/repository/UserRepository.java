package com.aegispay.user.repository;

import com.aegispay.common.domain.enums.KycStatus;
import com.aegispay.user.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByExternalId(String externalId);

    Optional<User> findByEmail(String email);

    boolean existsByExternalId(String externalId);

    boolean existsByEmail(String email);

    /** Back-office: list all users filtered by KYC status, newest first. */
    Page<User> findAllByKycStatusOrderByCreatedAtDesc(KycStatus kycStatus, Pageable pageable);

    /** Back-office: list all users regardless of KYC status, newest first. */
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
