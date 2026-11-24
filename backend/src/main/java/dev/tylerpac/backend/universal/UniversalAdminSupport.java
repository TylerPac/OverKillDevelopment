package dev.tylerpac.backend.universal;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.AdminAccess;
import dev.tylerpac.backend.universal.UniversalApiClient.UniversalApiException;

// What the Universal API admin controllers share: who the signed-in admin is, and the ids admins type or click,
// checked here so a malformed one never reaches the Universal API.
@Component
public class UniversalAdminSupport {

    private final UserRepository users;
    private final AdminAccess adminAccess;

    public UniversalAdminSupport(UserRepository users, AdminAccess adminAccess) {
        this.users = users;
        this.adminAccess = adminAccess;
    }

    /** The admin's Steam id; 401 when signed out, 403 when not an admin, 503 when the login service is down. */
    public String requireAdmin(Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new IllegalArgumentException("unauthorized");
        }
        User user = users.findByUsername(principal.getName()).orElseThrow(() -> new IllegalArgumentException("unauthorized"));
        adminAccess.requireAdmin(user.getSteam64Id());
        return user.getSteam64Id();
    }

    static String requireSteamId(String steamId) {
        if (steamId == null || !steamId.matches("\\d{17}")) {
            throw new UniversalApiException(HttpStatus.BAD_REQUEST, "invalid_steam_id");
        }
        return steamId;
    }

    /** A game server's id: what the DayZ mod accepts in ServerId. */
    static String requireServerId(String serverId) {
        if (serverId == null || !serverId.matches("[A-Za-z0-9._~-]{1,64}")) {
            throw new UniversalApiException(HttpStatus.BAD_REQUEST, "invalid_server_id");
        }
        return serverId;
    }
}
