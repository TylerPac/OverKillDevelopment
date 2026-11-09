package dev.tylerpac.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;

class SharedLoginUserServiceTest {

    private static final String STEAM_ID = "76561199000000001";

    private final UserRepository users = mock(UserRepository.class);
    private final SharedLoginUserService service = new SharedLoginUserService(users);

    private static OidcUser shared(String steamId) {
        Map<String, Object> claims = new HashMap<>(Map.of("sub", "account-1"));
        if (steamId != null) {
            claims.put("steam_id", steamId);
        }
        OidcIdToken token = new OidcIdToken("value", Instant.now(), Instant.now().plusSeconds(60), claims);
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), token, "sub");
    }

    @Test
    void anExistingSteamAccountKeepsItsLocalUsername() {
        User existing = User.fromSteamAccount(STEAM_ID);
        existing.setUsername("older-name");
        when(users.findBySteam64Id(STEAM_ID)).thenReturn(Optional.of(existing));

        assertEquals("older-name", service.toLocalUser(shared(STEAM_ID)).getName());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aNewSteamAccountIsCreated() {
        when(users.findBySteam64Id(STEAM_ID)).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(STEAM_ID, service.toLocalUser(shared(STEAM_ID)).getName());
    }

    @Test
    void anAccountWithoutSteamIsRefused() {
        assertThrows(OAuth2AuthenticationException.class, () -> service.toLocalUser(shared(null)));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void aMalformedSteamIdIsRefused() {
        assertThrows(OAuth2AuthenticationException.class, () -> service.toLocalUser(shared("not-a-steam-id")));
        verify(users, never()).saveAndFlush(any());
    }
}
