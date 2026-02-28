package dev.tylerpac.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ShopReconciliationService {

    private final StripeShopService stripeShopService;

    public ShopReconciliationService(StripeShopService stripeShopService) {
        this.stripeShopService = stripeShopService;
    }

    @Scheduled(fixedDelayString = "${app.shop.reconcile-interval-ms:300000}")
    public void reconcilePendingOrders() {
        stripeShopService.reconcilePendingOrders();
    }
}
