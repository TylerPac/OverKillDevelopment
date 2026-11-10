package dev.tylerpac.backend.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletResponse;

// Sign-in is delegated to the shared login service. The backend completes the OpenID Connect flow itself and keeps
// the browser signed in with an httpOnly session cookie, so tokens never reach JavaScript.
@Configuration
public class SecurityConfig {

    private static final String REGISTRATION_ID = "shared";

    // These do not use the browser session: Stripe and game servers authenticate
    // each request themselves, so a CSRF token would protect nothing and they could never send one.
    private static final String[] NO_SESSION_POSTS = {
        "/api/dayz/**", "/shop/webhook"
    };

    @Value("${app.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    private final String issuer;
    private final String internalUrl;
    private final String clientSecret;
    private final String publicUrl;

    public SecurityConfig(
        @Value("${app.login.issuer}") String issuer,
        @Value("${app.login.internal-url}") String internalUrl,
        @Value("${app.login.client-secret}") String clientSecret,
        @Value("${app.auth.frontend-base-url:http://localhost:5173}") String publicUrl
    ) {
        this.issuer = issuer;
        this.internalUrl = internalUrl;
        this.clientSecret = clientSecret;
        this.publicUrl = publicUrl.replaceAll("/$", "");
    }

    // Endpoints are set explicitly instead of discovered, so the site still starts when the login service is down.
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientName("Shared login")
            .clientId("overkill")
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(publicUrl + "/login/oauth2/code/" + REGISTRATION_ID)
            .scope("openid", "profile")
            .issuerUri(issuer)
            .authorizationUri(issuer + "/oauth2/authorize")
            .tokenUri(internalUrl + "/oauth2/token")
            .jwkSetUri(internalUrl + "/oauth2/jwks")
            .userNameAttributeName("sub")
            .build());
    }

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        ClientRegistrationRepository registrations,
        SharedLoginUserService users
    ) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver authorizationRequests = new DefaultOAuth2AuthorizationRequestResolver(
            registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
        // Accounts here are Steam accounts, so the login service skips straight to Steam
        authorizationRequests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce()
            .andThen(builder -> builder.additionalParameters(Map.of("provider_hint", "steam"))));

        http
            .cors(Customizer.withDefaults())
            // A session cookie is sent automatically, so state-changing requests need a CSRF token
            .csrf(csrf -> csrf.spa().ignoringRequestMatchers(NO_SESSION_POSTS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/api/google/**", "/api/dayz/**", "/actuator/**", "/shop/webhook", "/shop/products", "/shop/product-links", "/shop/download-stream").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .oauth2Login(login -> login
                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
                .userInfoEndpoint(info -> info.oidcUserService(users))
                .defaultSuccessUrl(publicUrl + "/steam-callback", true)
                .failureUrl(publicUrl + "/steam-callback?error=sign_in_failed"))
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> writeLogoutUrl(response, authentication)));
        return http.build();
    }

    // The frontend sends the browser to this URL, which also ends the shared sign-in
    private void writeLogoutUrl(HttpServletResponse response, Authentication authentication) throws IOException {
        UriComponentsBuilder logout = UriComponentsBuilder.fromUriString(issuer + "/connect/logout")
            .queryParam("post_logout_redirect_uri", publicUrl + "/");
        if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
            logout.queryParam("id_token_hint", user.getIdToken().getTokenValue());
        }
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"logoutUrl\":\"" + logout.build().encode().toUriString() + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // Allow Google OAuth redirects (origin may be https://accounts.google.com)
        CorsConfiguration googleConfig = new CorsConfiguration();
        googleConfig.setAllowedOrigins(List.of("https://accounts.google.com"));
        googleConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        googleConfig.setAllowedHeaders(List.of("*"));
        // Do not send credentials for Google redirects
        googleConfig.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/google/**", googleConfig);

        return source;
    }
}
