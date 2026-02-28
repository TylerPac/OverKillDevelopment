package dev.tylerpac.backend.service;

import org.springframework.stereotype.Service;

import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.service.email.EmailSender;

@Service
public class PurchaseEmailService {

    private final EmailSender emailSender;

    public PurchaseEmailService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    public void sendOrderPending(User user, ShopOrder order) {
        String subject = "OverKill Development purchase received: pending";
        String body = "Hi " + user.getUsername() + ",\n\n"
            + "We received your order for " + order.getProductName() + ".\n"
            + "Order status: PENDING\n"
            + "Order id: " + order.getId() + "\n\n"
            + "We will email you again when payment is confirmed.";
        emailSender.sendEmail(user.getEmail(), subject, body);
    }

    public void sendOrderPaid(User user, ShopOrder order) {
        String subject = "OverKill Development purchase confirmed";
        String body = "Hi " + user.getUsername() + ",\n\n"
            + "Payment was confirmed for your order.\n"
            + "Product: " + order.getProductName() + "\n"
            + "Order id: " + order.getId() + "\n"
            + "Status: PAID\n\n"
            + "Thank you for your purchase!";
        emailSender.sendEmail(user.getEmail(), subject, body);
    }

    public void sendOrderFailed(User user, ShopOrder order) {
        String subject = "OverKill Development purchase failed";
        String body = "Hi " + user.getUsername() + ",\n\n"
            + "Your payment for " + order.getProductName() + " did not complete.\n"
            + "Order id: " + order.getId() + "\n"
            + "Status: FAILED\n\n"
            + "You can try checkout again from the shop.";
        emailSender.sendEmail(user.getEmail(), subject, body);
    }
}
