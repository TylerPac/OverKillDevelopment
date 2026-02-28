package dev.tylerpac.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.tylerpac.backend.service.email.EmailSender;
import dev.tylerpac.backend.service.email.LoggingEmailSender;
import dev.tylerpac.backend.service.email.SesEmailSender;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration
public class EmailConfig {

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "ses")
    public SesClient sesClient(@Value("${app.aws.region}") String awsRegion) {
        return SesClient.builder()
            .region(Region.of(awsRegion))
            .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.email.provider", havingValue = "ses")
    public EmailSender sesEmailSender(SesClient sesClient, @Value("${app.email.from}") String fromEmail) {
        return new SesEmailSender(sesClient, fromEmail);
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    public EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}