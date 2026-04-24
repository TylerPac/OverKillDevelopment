package dev.tylerpac.backend.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    List<ShopOrder> findByUserOrderByCreatedAtDesc(User user);
    Optional<ShopOrder> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    List<ShopOrder> findAllByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    Optional<ShopOrder> findByUserAndIdempotencyKey(User user, String idempotencyKey);
    Optional<ShopOrder> findByStripePaymentIntentId(String stripePaymentIntentId);
    List<ShopOrder> findTop100ByStatusOrderByUpdatedAtAsc(String status);
    boolean existsByUserAndProductIdAndStatusIgnoreCase(User user, String productId, String status);
}
