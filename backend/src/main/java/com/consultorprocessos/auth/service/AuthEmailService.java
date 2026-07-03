package com.consultorprocessos.auth.service;

public interface AuthEmailService {

    void sendVerificationEmail(String to, String rawToken);

    void sendPasswordResetEmail(String to, String rawToken);

    void sendPasswordResetConfirmationEmail(String to);
}