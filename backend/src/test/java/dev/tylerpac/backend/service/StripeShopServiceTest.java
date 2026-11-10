package dev.tylerpac.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stripe.model.checkout.Session;

import dev.tylerpac.backend.dto.ProductGrantResponse;
import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.DownloadTokenRepository;
import dev.tylerpac.backend.repo.ProcessedStripeEventRepository;
import dev.tylerpac.backend.repo.ProductReleaseRepository;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import dev.tylerpac.backend.repo.UserRepository;

@ExtendWith(MockitoExtension.class)
class StripeShopServiceTest {

    private static final String ADMIN = "76561199155324762";
    private static final String PLAYER = "76561198012345678";

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Mock
    private DownloadTokenRepository downloadTokenRepository;

    @Mock
    private ProductReleaseRepository productReleaseRepository;

    @Mock
    private PurchaseEmailService purchaseEmailService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitHubRepoService gitHubRepoService;

    private StripeShopService stripeShopService;

    @BeforeEach
    void setUp() {
        stripeShopService = new StripeShopService(
            shopOrderRepository,
            processedStripeEventRepository,
            downloadTokenRepository,
            productReleaseRepository,
            purchaseEmailService,
            userRepository,
            gitHubRepoService,
            "usd",
            "http://localhost:5173",
            "http://localhost:5173",
            "sk_test_dummy",
            "whsec_dummy",
            "price_dummy"
        );
    }

    private static User player() {
        User user = User.fromSteamAccount(PLAYER);
        user.setId(42L);
        user.setGithubUsername("player-gh");
        return user;
    }

    @Test
    @DisplayName("Grants chosen products to a Steam account as free paid orders")
    void grantsProductsAsFreeOrders() {
        User user = player();
        when(userRepository.findBySteam64Id(PLAYER)).thenReturn(Optional.of(user));
        when(shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(eq(user), any(), eq("PAID"))).thenReturn(false);

        ProductGrantResponse.Result result = stripeShopService.grantProducts(" " + PLAYER + " ", List.of("keycard-crates", "battle-pass"), ADMIN);

        assertEquals(PLAYER, result.steamId());
        assertEquals(List.of("keycard-crates", "battle-pass"), result.granted());
        ArgumentCaptor<ShopOrder> orders = ArgumentCaptor.forClass(ShopOrder.class);
        verify(shopOrderRepository, times(2)).save(orders.capture());
        ShopOrder order = orders.getAllValues().get(0);
        assertEquals("PAID", order.getStatus());
        assertEquals(0L, order.getAmountCents());
        assertTrue(order.getStripeCheckoutSessionId().startsWith("grant:"));
        assertEquals("grant:" + ADMIN, order.getStripePaymentIntentId());
        verify(purchaseEmailService, times(2)).sendOrderPaid(eq(user), any(ShopOrder.class));
        verify(gitHubRepoService, times(2)).grantRepoAccess(eq("player-gh"), anyString());
    }

    @Test
    @DisplayName("Skips products the player already owns")
    void skipsOwnedProducts() {
        User user = player();
        when(userRepository.findBySteam64Id(PLAYER)).thenReturn(Optional.of(user));
        when(shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(user, "keycard-crates", "PAID")).thenReturn(true);
        when(shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(user, "battle-pass", "PAID")).thenReturn(false);

        ProductGrantResponse.Result result = stripeShopService.grantProducts(PLAYER, List.of("keycard-crates", "battle-pass"), ADMIN);

        assertEquals(List.of("battle-pass"), result.granted());
        assertEquals(List.of("keycard-crates"), result.alreadyOwned());
        verify(shopOrderRepository, times(1)).save(any(ShopOrder.class));
    }

    @Test
    @DisplayName("Creates the account of a player who has never signed in, as their first sign-in would")
    void createsTheAccountOfANewPlayer() {
        when(userRepository.findBySteam64Id(PLAYER)).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        ProductGrantResponse.Result result = stripeShopService.grantProducts(PLAYER, List.of("weapon-system"), ADMIN);

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(created.capture());
        assertEquals(PLAYER, created.getValue().getSteam64Id());
        assertEquals(PLAYER, created.getValue().getUsername());
        assertEquals(List.of("weapon-system"), result.granted());
    }

    @Test
    @DisplayName("Refuses a bad Steam id, an unknown product, or no products")
    void refusesBadRequests() {
        assertEquals("invalid_steam_id", assertThrows(IllegalArgumentException.class,
            () -> stripeShopService.grantProducts("12345", List.of("battle-pass"), ADMIN)).getMessage());
        assertEquals("invalid_steam_id", assertThrows(IllegalArgumentException.class,
            () -> stripeShopService.grantProducts(null, List.of("battle-pass"), ADMIN)).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> stripeShopService.grantProducts(PLAYER, List.of("nope"), ADMIN)).getMessage().startsWith("invalid_product"));
        assertEquals("product_ids_required", assertThrows(IllegalArgumentException.class,
            () -> stripeShopService.grantProducts(PLAYER, List.of(), ADMIN)).getMessage());
        verify(userRepository, never()).findBySteam64Id(any());
        verify(shopOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lists grants and old code redemptions, newest first")
    void listsGrantsAndOldCodeRedemptions() {
        ShopOrder grant = order(1L, "grant:abc", "grant:" + ADMIN, Instant.parse("2026-11-10T12:00:00Z"));
        ShopOrder fromCode = order(2L, "promo:7:battle-pass", "promo:7", Instant.parse("2026-11-01T12:00:00Z"));
        when(shopOrderRepository.findByStripeCheckoutSessionIdStartingWith("grant:")).thenReturn(List.of(grant));
        when(shopOrderRepository.findByStripeCheckoutSessionIdStartingWith("promo:")).thenReturn(List.of(fromCode));

        List<ProductGrantResponse> grants = stripeShopService.listGrants();

        assertEquals(2, grants.size());
        assertEquals("grant", grants.get(0).source());
        assertEquals(ADMIN, grants.get(0).grantedBy());
        assertEquals(PLAYER, grants.get(0).steamId());
        assertEquals("code", grants.get(1).source());
        assertEquals(null, grants.get(1).grantedBy());
    }

    @Test
    @DisplayName("Revokes free orders but never a paid Stripe order")
    void revokesOnlyFreeOrders() {
        ShopOrder grant = order(1L, "grant:abc", "grant:" + ADMIN, Instant.now());
        ShopOrder paid = order(2L, "cs_test_123", "pi_123", Instant.now());
        when(shopOrderRepository.findById(1L)).thenReturn(Optional.of(grant));
        when(shopOrderRepository.findById(2L)).thenReturn(Optional.of(paid));

        stripeShopService.revokeGrant(1L, ADMIN);
        assertEquals("REVOKED", grant.getStatus());
        verify(shopOrderRepository).save(grant);

        assertEquals("grant_not_found", assertThrows(IllegalArgumentException.class,
            () -> stripeShopService.revokeGrant(2L, ADMIN)).getMessage());
        assertEquals("PAID", paid.getStatus());
    }

    @Test
    @DisplayName("A checkout fully covered by a promotion code counts as paid; an unpaid one does not")
    void promotionCodeCheckoutsCountAsSettled() {
        assertTrue(StripeShopService.isSettled(session("paid")));
        assertTrue(StripeShopService.isSettled(session("no_payment_required")));
        assertFalse(StripeShopService.isSettled(session("unpaid")));
        assertFalse(StripeShopService.isSettled(session(null)));
    }

    private static Session session(String paymentStatus) {
        Session session = new Session();
        session.setPaymentStatus(paymentStatus);
        return session;
    }

    private static ShopOrder order(Long id, String sessionId, String paymentIntentId, Instant createdAt) {
        ShopOrder order = new ShopOrder();
        order.setId(id);
        order.setUser(player());
        order.setProductId("battle-pass");
        order.setProductName("Battle Pass");
        order.setStatus("PAID");
        order.setStripeCheckoutSessionId(sessionId);
        order.setStripePaymentIntentId(paymentIntentId);
        order.setCreatedAt(createdAt);
        return order;
    }
}
