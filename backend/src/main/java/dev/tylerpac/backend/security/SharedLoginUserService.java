package dev.tylerpac.backend.security;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;

// Turns a shared sign-in into this site's own account. Accounts here are keyed by Steam id, so a shared account with
// no Steam login is refused. The signed-in principal is named after the local username, which is how the rest of the
// application looks people up.
@Service
public class SharedLoginUserService extends OidcUserService {

    private final UserRepository users;

    public SharedLoginUserService(UserRepository users) {
        this.users = users;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        return toLocalUser(super.loadUser(request));
    }

    OidcUser toLocalUser(OidcUser shared) {
        String steamId = shared.getClaimAsString("steam_id");
        if (steamId == null || !steamId.matches("7656119\\d{10}")) {
            throw new OAuth2AuthenticationException(new OAuth2Error("steam_account_required"));
        }
        return new LocalUser(shared, findOrCreate(steamId).getUsername());
    }

    private User findOrCreate(String steamId) {
        return users.findBySteam64Id(steamId).orElseGet(() -> {
            try {
                return users.saveAndFlush(User.fromSteamAccount(steamId));
            } catch (DataIntegrityViolationException e) {
                // A simultaneous first sign-in won the insert
                return users.findBySteam64Id(steamId).orElseThrow(() -> e);
            }
        });
    }

    private static final class LocalUser extends DefaultOidcUser {

        private final String username;

        LocalUser(OidcUser shared, String username) {
            super(shared.getAuthorities(), shared.getIdToken(), shared.getUserInfo(), "sub");
            this.username = username;
        }

        @Override
        public String getName() {
            return username;
        }
    }
}
