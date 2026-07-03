package com.consultorprocessos.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class UserNotificationPreferences {

    @Column(name = "notif_email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "notif_push_enabled", nullable = false)
    private boolean pushEnabled = false;

    public UserNotificationPreferences(boolean emailEnabled, boolean pushEnabled) {
        this.emailEnabled = emailEnabled;
        this.pushEnabled  = pushEnabled;
    }
}