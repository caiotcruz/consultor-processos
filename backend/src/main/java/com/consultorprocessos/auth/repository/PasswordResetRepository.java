package com.consultorprocessos.auth.repository;

import com.consultorprocessos.auth.entity.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {

    Optional<PasswordReset> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordReset pr SET pr.usedAt = CURRENT_TIMESTAMP " +
           "WHERE pr.user.id = :userId AND pr.usedAt IS NULL")
    void invalidateAllByUserId(@Param("userId") UUID userId);
}