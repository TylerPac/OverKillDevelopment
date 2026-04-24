package dev.tylerpac.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;

@Service
public class PurchaseEmailService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseEmailService.class);

    public PurchaseEmailService() {
    }

    public void sendOrderPending(User user, ShopOrder order) {
        log.info("purchase_notification status=PENDING user={} orderId={} product={}",
            user.getUsername(),
            order.getId(),
            order.getProductName());
    }

    public void sendOrderPaid(User user, ShopOrder order) {
        log.info("purchase_notification status=PAID user={} orderId={} product={}",
            user.getUsername(),
            order.getId(),
            order.getProductName());
    }

    public void sendOrderFailed(User user, ShopOrder order) {
        log.info("purchase_notification status=FAILED user={} orderId={} product={}",
            user.getUsername(),
            order.getId(),
            order.getProductName());
    }
}
