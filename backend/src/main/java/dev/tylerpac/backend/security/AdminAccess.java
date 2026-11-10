package dev.tylerpac.backend.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import dev.tylerpac.backend.security.LoginRolesClient.LoginUnavailableException;

// Decides who is an Overkill admin by asking the shared login service on every call, so adding or removing an
// admin at auth.tylerpac.dev/admin applies straight away.
// APP_ADMIN_STEAM64_IDS is the old list, still accepted while admins move to the login service. Once it is empty,
// admin pages depend on the login service being up.
@Service
public class AdminAccess {

    private static final Logger log = LoggerFactory.getLogger(AdminAccess.class);

    private final Set<String> legacyAdminSteamIds;
    private final LoginRolesClient loginRoles;

    public AdminAccess(@Value("${app.admin.steam64-ids:}") String adminSteamIds, LoginRolesClient loginRoles) {
        this.legacyAdminSteamIds = Arrays.stream(adminSteamIds.split(","))
            .map(String::trim)
            .filter(id -> id.matches("\\d{17}"))
            .collect(Collectors.toUnmodifiableSet());
        this.loginRoles = loginRoles;
    }

    /** For showing the admin menu: false when not an admin or when the login service cannot answer. */
    public boolean isAdmin(String steam64Id) {
        if (steam64Id == null || steam64Id.isBlank()) {
            return false;
        }
        if (legacyAdminSteamIds.contains(steam64Id)) {
            return true;
        }
        try {
            return loginRoles.isAdmin(steam64Id);
        } catch (LoginUnavailableException e) {
            log.warn("Login service could not confirm admin status; showing the site without the admin menu");
            return false;
        }
    }

    /**
     * Throws IllegalArgumentException("forbidden") for non-admins, matching the shop's existing error handling,
     * and 503 login_unavailable when the login service cannot answer.
     */
    public void requireAdmin(String steam64Id) {
        if (steam64Id == null || steam64Id.isBlank()) {
            throw new IllegalArgumentException("forbidden");
        }
        if (legacyAdminSteamIds.contains(steam64Id)) {
            return;
        }
        boolean admin;
        try {
            admin = loginRoles.isAdmin(steam64Id);
        } catch (LoginUnavailableException e) {
            log.warn("Login service could not confirm admin status; refusing the admin request");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "login_unavailable");
        }
        if (!admin) {
            throw new IllegalArgumentException("forbidden");
        }
    }
}
