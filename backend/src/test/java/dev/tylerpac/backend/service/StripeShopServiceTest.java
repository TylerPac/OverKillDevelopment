package dev.tylerpac.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.tylerpac.backend.dto.CreateFullAccessCodeResponse;
import dev.tylerpac.backend.dto.RedeemFullAccessCodeResponse;
import dev.tylerpac.backend.model.ShopAccessCode;
import dev.tylerpac.backend.model.ShopOrder;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.ProcessedStripeEventRepository;
import dev.tylerpac.backend.repo.ShopAccessCodeRepository;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import dev.tylerpac.backend.repo.UserRepository;

@ExtendWith(MockitoExtension.class)
class StripeShopServiceTest {

    @Mock
    private ShopAccessCodeRepository shopAccessCodeRepository;

    @Mock
    private ShopOrderRepository shopOrderRepository;

    @Mock
    private ProcessedStripeEventRepository processedStripeEventRepository;

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
            shopAccessCodeRepository,
            shopOrderRepository,
            processedStripeEventRepository,
            purchaseEmailService,
            userRepository,
            gitHubRepoService,
            "usd",
            "http://localhost:5173",
            "http://localhost:5173",
            "admin-secret",
            "sk_test_dummy",
            "whsec_dummy",
            "price_dummy"
        );
    }

    @Test
    @DisplayName("Creates a full access code when admin token is valid")
    void createsFullAccessCode() {
        when(shopAccessCodeRepository.existsByCode(any())).thenReturn(false);

        CreateFullAccessCodeResponse response = stripeShopService.createFullAccessCode("vip-all-access", "admin-secret");

        assertEquals("VIP-ALL-ACCESS", response.getCode());
        assertEquals(true, response.isFullAccess());
        assertEquals(0, response.getProductIds().size());
        assertEquals(3, response.getProductCount());
        verify(shopAccessCodeRepository).save(any());
    }

    @Test
    @DisplayName("Creates a scoped access code for selected products")
    void createsScopedAccessCode() {
        when(shopAccessCodeRepository.existsByCode(any())).thenReturn(false);

        CreateFullAccessCodeResponse response = stripeShopService.createAccessCode(
            "battle-only",
            java.util.List.of("battle-pass"),
            "admin-secret"
        );

        assertEquals("BATTLE-ONLY", response.getCode());
        assertEquals(false, response.isFullAccess());
        assertEquals(1, response.getProductIds().size());
        assertEquals("battle-pass", response.getProductIds().get(0));
        assertEquals(1, response.getProductCount());
    }

    @Test
    @DisplayName("Redeems a one-time code and grants every missing product")
    void redeemsCodeAndCreatesPaidOrders() {
        User user = new User();
        user.setId(42L);
        user.setUsername("tester");
        user.setGithubUsername("tester-gh");

        ShopAccessCode accessCode = new ShopAccessCode();
        accessCode.setId(7L);
        accessCode.setCode("OKD-TEST-CODE");

        when(shopAccessCodeRepository.findByCode("OKD-TEST-CODE")).thenReturn(Optional.of(accessCode));
        when(shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(eq(user), any(), eq("PAID"))).thenReturn(false);

        RedeemFullAccessCodeResponse response = stripeShopService.redeemFullAccessCode(user, "okd-test-code");

        assertEquals(3, response.getGrantedCount());
        assertEquals(3, response.getGrantedProductIds().size());
        assertNotNull(accessCode.getRedeemedAt());
        assertEquals(user, accessCode.getRedeemedByUser());
        verify(shopOrderRepository, times(3)).save(any(ShopOrder.class));
        verify(shopAccessCodeRepository).save(accessCode);
        verify(purchaseEmailService, times(3)).sendOrderPaid(eq(user), any(ShopOrder.class));
        verify(gitHubRepoService, times(3)).grantRepoAccess(eq("tester-gh"), anyString());
    }

    @Test
    @DisplayName("Rejects an already redeemed code")
    void rejectsAlreadyRedeemedCode() {
        User user = new User();
        ShopAccessCode accessCode = new ShopAccessCode();
        accessCode.setCode("OKD-USED-CODE");
        accessCode.setRedeemedAt(Instant.now());

        when(shopAccessCodeRepository.findByCode("OKD-USED-CODE")).thenReturn(Optional.of(accessCode));

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> stripeShopService.redeemFullAccessCode(user, "okd-used-code")
        );

        assertEquals("access_code_already_redeemed", ex.getMessage());
    }

    @Test
    @DisplayName("Redeems scoped codes for only assigned products")
    void redeemsScopedCodeProductsOnly() {
        User user = new User();
        user.setId(12L);
        user.setUsername("scoped-user");
        user.setGithubUsername("scoped-gh");

        ShopAccessCode accessCode = new ShopAccessCode();
        accessCode.setId(99L);
        accessCode.setCode("OKD-SCOPED-1");
        accessCode.setFullAccess(false);
        accessCode.setProductIdsCsv("weapon-system,battle-pass");

        when(shopAccessCodeRepository.findByCode("OKD-SCOPED-1")).thenReturn(Optional.of(accessCode));
        when(shopOrderRepository.existsByUserAndProductIdAndStatusIgnoreCase(eq(user), any(), eq("PAID"))).thenReturn(false);

        RedeemFullAccessCodeResponse response = stripeShopService.redeemFullAccessCode(user, "okd-scoped-1");

        assertEquals(2, response.getGrantedCount());
        assertEquals(2, response.getGrantedProductIds().size());
        verify(shopOrderRepository, times(2)).save(any(ShopOrder.class));
    }
}