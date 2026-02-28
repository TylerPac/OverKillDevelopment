package dev.tylerpac.backend.service.email;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

public class SesEmailSender implements EmailSender {

    private final SesClient sesClient;
    private final String fromEmail;

    public SesEmailSender(SesClient sesClient, String fromEmail) {
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendEmail(String to, String subject, String textBody) {
        SendEmailRequest request = SendEmailRequest.builder()
            .source(fromEmail)
            .destination(Destination.builder().toAddresses(to).build())
            .message(Message.builder()
                .subject(Content.builder().data(subject).charset("UTF-8").build())
                .body(Body.builder().text(Content.builder().data(textBody).charset("UTF-8").build()).build())
                .build())
            .build();

        sesClient.sendEmail(request);
    }
}