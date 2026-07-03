package com.consultorprocessos.auth.entity;

import com.consultorprocessos.plan.entity.Plan;
import com.consultorprocessos.user.entity.UserNotificationPreferences;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "login_failure_count", nullable = false)
    private int loginFailureCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Embedded
    private UserNotificationPreferences notificationPreferences =new UserNotificationPreferences();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "role", length = 50)
    private Set<String> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean isActive() {
        return UserStatus.ACTIVE.equals(status);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public void incrementLoginFailures() {
        this.loginFailureCount++;
        if (this.loginFailureCount >= 5) {
            this.lockedUntil = Instant.now().plusSeconds(900);
            this.loginFailureCount = 0;
        }
    }

    public void resetLoginFailures() {
        this.loginFailureCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void anonymize() {
        this.name         = "Usuário Removido";
        this.email        = "deleted_" + this.id + "@deleted.consultorprocessos.com.br";
        this.passwordHash = "";
        this.status       = UserStatus.DELETED;
        this.emailVerifiedAt   = null;
        this.lastLoginAt       = null;
        this.loginFailureCount = 0;
        this.lockedUntil       = null;
    }
}