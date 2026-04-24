package dev.tylerpac.backend.security;

import java.io.IOException;
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for JwtFilter - JWT authentication filter
 */
@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtFilter jwtFilter;
    private SecurityContext securityContext;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final String INVALID_TOKEN = "invalid.jwt.token";
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setUp() {
        jwtFilter = new JwtFilter(jwtUtil, userDetailsService);
        
        // Create a fresh SecurityContext for each test
        securityContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        // Clean up SecurityContext after each test
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should set authentication when valid Bearer token is provided")
    void shouldSetAuthenticationWithValidToken() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtUtil.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(VALID_TOKEN)).thenReturn(USERNAME);
        
        UserDetails userDetails = User.builder()
                .username(USERNAME)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Authentication should be set");
        assertEquals(USERNAME, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.isAuthenticated());
        
        verify(filterChain).doFilter(request, response);
        verify(jwtUtil).validateToken(VALID_TOKEN);
        verify(jwtUtil).extractUsername(VALID_TOKEN);
        verify(userDetailsService).loadUserByUsername(USERNAME);
    }

    @Test
    @DisplayName("Should not set authentication when token is invalid")
    void shouldNotSetAuthenticationWithInvalidToken() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + INVALID_TOKEN);
        when(jwtUtil.validateToken(INVALID_TOKEN)).thenReturn(false);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Authentication should not be set for invalid token");
        
        verify(filterChain).doFilter(request, response);
        verify(jwtUtil).validateToken(INVALID_TOKEN);
        verify(jwtUtil, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("Should not set authentication when Authorization header is missing")
    void shouldNotSetAuthenticationWhenHeaderMissing() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Authentication should not be set when header is missing");
        
        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).validateToken(anyString());
        verify(jwtUtil, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("Should not set authentication when Authorization header does not start with Bearer")
    void shouldNotSetAuthenticationWhenNotBearerToken() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Basic sometoken");

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Authentication should not be set when not Bearer token");
        
        verify(filterChain).doFilter(request, response);
        verify(jwtUtil, never()).validateToken(anyString());
        verify(jwtUtil, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("Should not set authentication when Authorization header has only Bearer")
    void shouldNotSetAuthenticationWhenBearerWithoutToken() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Authentication should not be set when token is empty");
        
        verify(filterChain).doFilter(request, response);
        // Empty string token will be validated
        verify(jwtUtil).validateToken("");
        verify(jwtUtil, never()).extractUsername(anyString());
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    @DisplayName("Should continue filter chain even when authentication fails")
    void shouldContinueFilterChainOnAuthenticationFailure() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + INVALID_TOKEN);
        when(jwtUtil.validateToken(INVALID_TOKEN)).thenReturn(false);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should continue filter chain even without Authorization header")
    void shouldContinueFilterChainWithoutHeader() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should handle UserDetailsService exception gracefully")
    void shouldHandleUserDetailsServiceException() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtUtil.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(VALID_TOKEN)).thenReturn(USERNAME);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenThrow(new RuntimeException("User not found"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            jwtFilter.doFilterInternal(request, response, filterChain);
        });
        
        // Authentication should not be set
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNull(auth, "Authentication should not be set when UserDetailsService throws exception");
    }

    @Test
    @DisplayName("Should set authentication with user authorities")
    void shouldSetAuthenticationWithAuthorities() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtUtil.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(VALID_TOKEN)).thenReturn(USERNAME);
        
        UserDetails userDetails = User.builder()
                .username(USERNAME)
                .password("password")
                .authorities("ROLE_USER", "ROLE_ADMIN")
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals(2, auth.getAuthorities().size());
        
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should extract token correctly from Bearer header with extra spaces")
    void shouldHandleBearerHeaderWithSpaces() throws ServletException, IOException {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer  " + VALID_TOKEN);
        when(jwtUtil.validateToken(" " + VALID_TOKEN)).thenReturn(true);
        when(jwtUtil.extractUsername(" " + VALID_TOKEN)).thenReturn(USERNAME);
        
        UserDetails userDetails = User.builder()
                .username(USERNAME)
                .password("password")
                .authorities(Collections.emptyList())
                .build();
        when(userDetailsService.loadUserByUsername(USERNAME)).thenReturn(userDetails);

        // Act
        jwtFilter.doFilterInternal(request, response, filterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        
        verify(filterChain).doFilter(request, response);
    }
}
