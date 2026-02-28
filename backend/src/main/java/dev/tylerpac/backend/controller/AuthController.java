package dev.tylerpac.backend.controller;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.tylerpac.backend.dto.AuthRequest;
import dev.tylerpac.backend.dto.AuthResponse;
import dev.tylerpac.backend.dto.PasswordResetConfirmRequest;
import dev.tylerpac.backend.dto.PasswordResetRequest;
import dev.tylerpac.backend.dto.RefreshTokenRequest;
import dev.tylerpac.backend.dto.ResendVerificationRequest;
import dev.tylerpac.backend.model.User;
import dev.tylerpac.backend.model.UserTokenPurpose;
import dev.tylerpac.backend.repo.UserRepository;
import dev.tylerpac.backend.security.JwtUtil;
import dev.tylerpac.backend.service.AuthEmailService;
import dev.tylerpac.backend.service.AuthSecurityService;
import dev.tylerpac.backend.service.UserTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserTokenService userTokenService;
    private final AuthEmailService authEmailService;
    private final AuthSecurityService authSecurityService;
    private final long emailVerificationTtlMinutes;
    private final long passwordResetTtlMinutes;
    private final long accessTokenTtlMinutes;
    private final long refreshTokenTtlDays;

    public AuthController(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        AuthenticationManager authenticationManager,
        JwtUtil jwtUtil,
        UserTokenService userTokenService,
        AuthEmailService authEmailService,
        AuthSecurityService authSecurityService,
        @Value("${app.auth.verification-ttl-minutes}") long emailVerificationTtlMinutes,
        @Value("${app.auth.reset-ttl-minutes}") long passwordResetTtlMinutes,
        @Value("${app.auth.access-token-ttl-minutes:15}") long accessTokenTtlMinutes,
        @Value("${app.auth.refresh-token-ttl-days:7}") long refreshTokenTtlDays
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userTokenService = userTokenService;
        this.authEmailService = authEmailService;
        this.authSecurityService = authSecurityService;
        this.emailVerificationTtlMinutes = emailVerificationTtlMinutes;
        this.passwordResetTtlMinutes = passwordResetTtlMinutes;
        this.accessTokenTtlMinutes = accessTokenTtlMinutes;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest req) {
        if (!StringUtils.hasText(req.getEmail())) {
            return ResponseEntity.badRequest().body("email_required");
        }

        Optional<User> exists = userRepository.findByUsername(req.getUsername());
        if (exists.isPresent()) return ResponseEntity.badRequest().body("username_taken");
        if (userRepository.existsByEmail(req.getEmail())) return ResponseEntity.badRequest().body("email_taken");

        User u = new User(req.getUsername(), passwordEncoder.encode(req.getPassword()), req.getEmail());
        u.setEmailVerified(false);
        userRepository.save(u);

        String verificationToken = userTokenService.issueToken(
            u,
            UserTokenPurpose.EMAIL_VERIFICATION,
            Duration.ofMinutes(emailVerificationTtlMinutes)
        );
        authEmailService.sendVerificationEmail(u.getEmail(), u.getUsername(), verificationToken);

        return ResponseEntity.accepted().body("verification_sent");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest req, HttpServletRequest httpServletRequest) {
        String ipAddress = resolveClientIp(httpServletRequest);
        if (authSecurityService.isIpRateLimited(ipAddress)) {
            return ResponseEntity.status(429).body("too_many_requests");
        }
        if (authSecurityService.isCredentialLocked(req.getUsername(), ipAddress)) {
            return ResponseEntity.status(429).body("too_many_failed_attempts");
        }

        Optional<User> existingUser = userRepository.findByUsername(req.getUsername());
        if (existingUser.isPresent() && !existingUser.get().isEmailVerified()) {
            return ResponseEntity.status(403).body("email_not_verified");
        }

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        } catch (BadCredentialsException ex) {
            authSecurityService.recordAuthFailure(req.getUsername(), ipAddress);
            return ResponseEntity.status(401).body("invalid_credentials");
        } catch (AuthenticationException ex) {
            authSecurityService.recordAuthFailure(req.getUsername(), ipAddress);
            return ResponseEntity.status(401).body("authentication_failed");
        }

        authSecurityService.recordAuthSuccess(req.getUsername(), ipAddress);
        User user = existingUser.orElseThrow();
        String accessToken = jwtUtil.generateToken(req.getUsername(), 1000L * 60 * accessTokenTtlMinutes);
        String refreshToken = userTokenService.issueToken(
            user,
            UserTokenPurpose.REFRESH_SESSION,
            Duration.ofDays(refreshTokenTtlDays)
        );
        return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, "Bearer", accessTokenTtlMinutes * 60));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        Optional<User> userOpt = userTokenService.consumeToken(req.getRefreshToken(), UserTokenPurpose.REFRESH_SESSION);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body("invalid_refresh_token");
        }

        User user = userOpt.get();
        String accessToken = jwtUtil.generateToken(user.getUsername(), 1000L * 60 * accessTokenTtlMinutes);
        String nextRefreshToken = userTokenService.issueToken(
            user,
            UserTokenPurpose.REFRESH_SESSION,
            Duration.ofDays(refreshTokenTtlDays)
        );

        return ResponseEntity.ok(new AuthResponse(accessToken, nextRefreshToken, "Bearer", accessTokenTtlMinutes * 60));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        Optional<User> userOpt = userTokenService.consumeToken(token, UserTokenPurpose.EMAIL_VERIFICATION);
        if (userOpt.isEmpty()) {
            if (userTokenService.isAlreadyVerifiedFromToken(token)) {
                return ResponseEntity.ok("email_verified");
            }
            return ResponseEntity.badRequest().body("invalid_or_expired_token");
        }

        User user = userOpt.get();
        user.setEmailVerified(true);
        userRepository.save(user);
        return ResponseEntity.ok("email_verified");
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        Optional<User> userOpt = userRepository.findByEmail(req.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.isEmailVerified()) {
                String verificationToken = userTokenService.issueToken(
                    user,
                    UserTokenPurpose.EMAIL_VERIFICATION,
                    Duration.ofMinutes(emailVerificationTtlMinutes)
                );
                authEmailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);
            }
        }
        return ResponseEntity.ok("verification_if_exists");
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        Optional<User> userOpt = userRepository.findByEmail(req.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String resetToken = userTokenService.issueToken(
                user,
                UserTokenPurpose.PASSWORD_RESET,
                Duration.ofMinutes(passwordResetTtlMinutes)
            );
            authEmailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetToken);
        }

        return ResponseEntity.ok("password_reset_if_exists");
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        Optional<User> userOpt = userTokenService.consumeToken(req.getToken(), UserTokenPurpose.PASSWORD_RESET);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("invalid_or_expired_token");
        }

        User user = userOpt.get();
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);
        userTokenService.revokeForUser(user, UserTokenPurpose.PASSWORD_RESET);
        return ResponseEntity.ok("password_reset_success");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
