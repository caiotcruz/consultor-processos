package com.consultorprocessos.auth.repository;

import com.consultorprocessos.auth.entity.User;
import com.consultorprocessos.auth.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") UserStatus status);

    Page<User> findByStatusOrderByCreatedAtDesc(UserStatus status, Pageable pageable);

    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);
}