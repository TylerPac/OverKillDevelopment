package dev.tylerpac.backend.service.email;

public interface EmailSender {
    void sendEmail(String to, String subject, String textBody);
}