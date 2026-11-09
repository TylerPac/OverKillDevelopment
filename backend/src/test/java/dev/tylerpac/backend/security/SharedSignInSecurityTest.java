package dev.tylerpac.backend.security;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import dev.tylerpac.backend.controller.AuthController;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.ShopOrderRepository;
import dev.tylerpac.backend.repo.UserGoogleCredentialRepository;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.service.CryptoUtil;
import dev.tylerpac.backend.service.DiscordOAuthService;
import dev.tylerpac.backend.service.GitHubOAuthService;
import dev.tylerpac.backend.service.GoogleOAuthService;
import dev.tylerpac.backend.service.SheetsParsingService;
import dev.tylerpac.backend.service.UserTokenService;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, SharedLoginUserService.class})
@TestPropertySource(properties = {
    "app.login.issuer=https://auth.test",
    "app.login.internal-url=http://login.internal",
    "app.login.client-secret=test-value",
    "app.auth.frontend-base-url=https://site.test"
})
class SharedSignInSecurityTest {

    private static final String STEAM_ID = "76561199000000001";

    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private ShopOrderRepository orders;
    @MockitoBean private UserTokenService tokens;
    @MockitoBean private DiscordOAuthService discord;
    @MockitoBean private GitHubOAuthService github;
    @MockitoBean private UserGoogleCredentialRepository googleCredentials;
    @MockitoBean private CryptoUtil crypto;
    @MockitoBean private GoogleOAuthService google;
    @MockitoBean private SheetsParsingService sheets;

    @Test
    void signInStartsAtTheSharedServiceWithPkceAndGoesStraightToSteam() throws Exception {
        mvc.perform(get("/oauth2/authorization/shared"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", allOf(
                containsString("https://auth.test/oauth2/authorize"),
                containsString("client_id=overkill"),
                containsString("code_challenge="),
                containsString("provider_hint=steam"),
                containsString("https://site.test/login/oauth2/code/shared"))));
    }

    @Test
    void aSignedOutVisitorGets401AndTheCsrfCookie() throws Exception {
        mvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void aSignedInUserIsLookedUpByUsername() throws Exception {
        User account = User.fromSteamAccount(STEAM_ID);
        ReflectionTestUtils.setField(account, "id", 1L);
        when(users.findByUsername(STEAM_ID)).thenReturn(Optional.of(account));

        mvc.perform(get("/auth/me").with(user(STEAM_ID)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steam64Id").value(STEAM_ID));
    }

    @Test
    void protectedEndpointsAnswer401NotARedirect() throws Exception {
        mvc.perform(get("/shop/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void aBearerTokenNoLongerSignsAnyoneIn() throws Exception {
        mvc.perform(get("/shop/orders").header("Authorization", "Bearer anything"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionRequestsWithoutACsrfTokenAreRefused() throws Exception {
        mvc.perform(post("/shop/access-codes/redeem").with(user(STEAM_ID))).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").with(user(STEAM_ID))).andExpect(status().isForbidden());
    }

    @Test
    void logoutReturnsTheUrlThatEndsTheSharedSignIn() throws Exception {
        // A real cookie and header exchange, as the browser does it
        String csrf = mvc.perform(get("/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();

        mvc.perform(post("/auth/logout").with(user(STEAM_ID))
                .cookie(new Cookie("XSRF-TOKEN", csrf)).header("X-XSRF-TOKEN", csrf))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logoutUrl", containsString("https://auth.test/connect/logout")));
    }

    // 404 here means the request passed security and simply has no controller in this test slice
    @Test
    void serverToServerPostsAreNotBlockedByCsrf() throws Exception {
        mvc.perform(post("/shop/webhook")).andExpect(status().isNotFound());
        mvc.perform(post("/api/dayz/players")).andExpect(status().isNotFound());
        mvc.perform(post("/shop/access-codes/create")).andExpect(status().isNotFound());
    }

    @Test
    void theOldSteamCallbackIsGone() throws Exception {
        mvc.perform(get("/auth/steam/callback")).andExpect(status().isNotFound());
        mvc.perform(get("/auth/steam/login-url")).andExpect(status().isNotFound());
    }
}
