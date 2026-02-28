package dev.tylerpac.backend.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void sendEmail(String to, String subject, String textBody) {
        log.info("Email sender=log to={} subject={} body={}", to, subject, textBody);
    }
}