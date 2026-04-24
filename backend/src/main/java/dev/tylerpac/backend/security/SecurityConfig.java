package dev.tylerpac.backend.security;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.repo.UserRepository;

@Configuration
public class SecurityConfig {

    @Value("${app.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            Optional<User> u = userRepository.findByUsername(username);
            if (u.isEmpty()) throw new UsernameNotFoundException("User not found");
            User user = u.get();
            return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                    .password("{noop}oauth")
                    .authorities("USER")
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/api/google/**", "/actuator/**", "/shop/webhook", "/shop/products").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
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
