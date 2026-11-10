package dev.tylerpac.backend.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import dev.tylerpac.backend.security.LoginRolesClient.LoginUnavailableException;

class AdminAccessTest {

    private static final String LEGACY_ADMIN = "76561199000000001";
    private static final String LOGIN_ADMIN = "76561199000000002";
    private static final String PLAYER = "76561198012345678";

    private final LoginRolesClient loginRoles = mock(LoginRolesClient.class);
    private final AdminAccess access = new AdminAccess(LEGACY_ADMIN, loginRoles);

    @Test
    void accountsWithoutASteamIdAreNeverAdminsAndTheLoginServiceIsNotAsked() {
        assertFalse(access.isAdmin(null));
        assertFalse(access.isAdmin(""));
        assertEquals("forbidden", assertThrows(IllegalArgumentException.class, () -> access.requireAdmin(null)).getMessage());
        verifyNoInteractions(loginRoles);
    }

    @Test
    void theOldListStillWorksWithoutAskingTheLoginService() {
        access.requireAdmin(LEGACY_ADMIN);
        assertTrue(access.isAdmin(LEGACY_ADMIN));
        verifyNoInteractions(loginRoles);
    }

    @Test
    void adminsGrantedInTheLoginServiceAreAdmins() {
        when(loginRoles.isAdmin(LOGIN_ADMIN)).thenReturn(true);
        access.requireAdmin(LOGIN_ADMIN);
        assertTrue(access.isAdmin(LOGIN_ADMIN));
    }

    @Test
    void playersAreRefusedTheWayTheShopExpects() {
        when(loginRoles.isAdmin(PLAYER)).thenReturn(false);
        assertEquals("forbidden", assertThrows(IllegalArgumentException.class, () -> access.requireAdmin(PLAYER)).getMessage());
        assertFalse(access.isAdmin(PLAYER));
    }

    @Test
    void whenTheLoginServiceIsDownAdminRequestsFailClosedAndTheMenuIsHidden() {
        when(loginRoles.isAdmin(LOGIN_ADMIN)).thenThrow(new LoginUnavailableException());
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> access.requireAdmin(LOGIN_ADMIN));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(access.isAdmin(LOGIN_ADMIN));
    }
}
