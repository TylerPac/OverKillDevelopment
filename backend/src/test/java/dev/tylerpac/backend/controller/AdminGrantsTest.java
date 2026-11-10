package dev.tylerpac.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.tylerpac.backend.dto.ProductGrantResponse;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.ProductReleaseRepository;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.AdminAccess;
import dev.tylerpac.backend.security.SecurityConfig;
import dev.tylerpac.backend.security.SharedLoginUserService;
import dev.tylerpac.backend.service.ShopDownloadService;
import dev.tylerpac.backend.service.StripeShopService;

// Giving products to a Steam account from the admin page, behind the real security chain and admin check.
@WebMvcTest(controllers = ShopController.class)
@Import({SecurityConfig.class, SharedLoginUserService.class})
@TestPropertySource(properties = {
    "app.login.issuer=https://auth.test",
    "app.login.internal-url=http://login.internal",
    "app.login.client-secret=test-value",
    "app.auth.frontend-base-url=https://site.test"
})
class AdminGrantsTest {

    private static final String ADMIN = "76561199000000001";
    private static final String PLAYER = "76561198012345678";

    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private AdminAccess adminAccess;
    @MockitoBean private StripeShopService shop;
    @MockitoBean private ShopDownloadService shopDownloads;
    @MockitoBean private ProductReleaseRepository releases;

    @BeforeEach
    void accounts() {
        when(users.findByUsername(ADMIN)).thenReturn(Optional.of(User.fromSteamAccount(ADMIN)));
        when(users.findByUsername(PLAYER)).thenReturn(Optional.of(User.fromSteamAccount(PLAYER)));
        doThrow(new IllegalArgumentException("forbidden")).when(adminAccess).requireAdmin(PLAYER);
    }

    @Test
    void anAdminGrantsProductsInTheirOwnName() throws Exception {
        when(shop.grantProducts(PLAYER, List.of("battle-pass"), ADMIN))
            .thenReturn(new ProductGrantResponse.Result(PLAYER, List.of("battle-pass"), List.of()));

        mvc.perform(post("/shop/admin/grants").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"steamId\":\"" + PLAYER + "\",\"productIds\":[\"battle-pass\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.granted[0]").value("battle-pass"));
    }

    @Test
    void playersCannotGrantListOrRevoke() throws Exception {
        mvc.perform(post("/shop/admin/grants").with(user(PLAYER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"steamId\":\"" + PLAYER + "\",\"productIds\":[\"battle-pass\"]}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/shop/admin/grants").with(user(PLAYER))).andExpect(status().isForbidden());
        mvc.perform(post("/shop/admin/grants/1/revoke").with(user(PLAYER)).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(shop);
    }

    @Test
    void mistakesComeBackAsErrorCodes() throws Exception {
        when(shop.grantProducts(any(), any(), any())).thenThrow(new IllegalArgumentException("invalid_steam_id"));
        mvc.perform(post("/shop/admin/grants").with(user(ADMIN)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"steamId\":\"123\",\"productIds\":[\"battle-pass\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("invalid_steam_id"));

        doThrow(new IllegalArgumentException("grant_not_found")).when(shop).revokeGrant(9L, ADMIN);
        mvc.perform(post("/shop/admin/grants/9/revoke").with(user(ADMIN)).with(csrf()))
            .andExpect(status().isNotFound());
        verify(shop).revokeGrant(9L, ADMIN);
    }
}
