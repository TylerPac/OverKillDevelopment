package dev.tylerpac.backend.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.service.email.EmailSender;

@Service
public class AuthEmailService {

    private final EmailSender emailSender;
    private final String frontendBaseUrl;

    public AuthEmailService(EmailSender emailSender, @Value("${app.auth.frontend-base-url}") String frontendBaseUrl) {
        this.emailSender = emailSender;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void sendVerificationEmail(String toEmail, String username, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String verifyLink = frontendBaseUrl + "/verify-email?token=" + encodedToken;
        String subject = "Verify your OverKill Development account";
        String body = "Hi " + username + ",\n\n"
            + "Please verify your email by opening this link:\n"
            + verifyLink + "\n\n"
            + "If you did not sign up, you can ignore this email.";
        emailSender.sendEmail(toEmail, subject, body);
    }

    public void sendPasswordResetEmail(String toEmail, String username, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String resetLink = frontendBaseUrl + "/reset-password?token=" + encodedToken;
        String subject = "Reset your OverKill Development password";
        String body = "Hi " + username + ",\n\n"
            + "We received a request to reset your password. Open this link:\n"
            + resetLink + "\n\n"
            + "If you did not request this, you can ignore this email.";
        emailSender.sendEmail(toEmail, subject, body);
    }
}