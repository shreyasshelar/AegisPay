package com.aegispay.user.repository;

import com.aegispay.user.domain.entity.User;
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
}
