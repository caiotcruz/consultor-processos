package com.consultorprocessos.notification.repository;

import com.consultorprocessos.notification.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

    List<UserDeviceToken> findByUserId(UUID userId);

    Optional<UserDeviceToken> findByToken(String token);

    boolean existsByUserIdAndToken(UUID userId, String token);

    void deleteByToken(String token);

    int countByUserId(UUID userId);
}