package com.nanaskitchens.api.auth;

import com.nanaskitchens.api.audit.AuditLog;
import com.nanaskitchens.api.audit.AuditLogRepository;
import com.nanaskitchens.api.auth.dto.TokenPairResponse;
import com.nanaskitchens.api.common.Role;
import com.nanaskitchens.api.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Set<Role> SELF_SERVE_ROLES = EnumSet.of(Role.buyer, Role.seller);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTokenTtlDays;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${app.jwt.refresh-token-ttl-days:30}") long refreshTokenTtlDays) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    @Transactional
    public TokenPairResponse register(String email, String rawPassword, String roleValue) {
        Role role = parseSelfServeRole(roleValue);
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user = userRepository.save(user);
        audit(user.getId(), "User", "register");
        return issuePair(user);
    }

    @Transactional
    public TokenPairResponse login(String email, String rawPassword) {
        User user = userRepository
                .findByEmail(email)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        audit(user.getId(), "User", "login");
        return issuePair(user);
    }

    /** Refresh rotation: previous token is single-use and revoked once redeemed (mirrors AC3). */
    @Transactional
    public TokenPairResponse refresh(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RefreshToken row = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .filter(rt -> rt.getRevokedAt() == null && rt.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID"));
        row.setRevokedAt(now);
        refreshTokenRepository.save(row);

        User user = userRepository
                .findById(row.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_INVALID"));
        return issuePair(user);
    }

    private TokenPairResponse issuePair(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        String rawRefreshToken = generateOpaqueToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(sha256Hex(rawRefreshToken));
        refreshToken.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(refreshTokenTtlDays));
        refreshTokenRepository.save(refreshToken);

        return new TokenPairResponse(accessToken, rawRefreshToken);
    }

    private Role parseSelfServeRole(String raw) {
        Role role;
        try {
            role = Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE");
        }
        if (!SELF_SERVE_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ROLE_NOT_SELF_SERVE");
        }
        return role;
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void audit(String actor, String entity, String action) {
        AuditLog log = new AuditLog();
        log.setActor(actor);
        log.setEntity(entity);
        log.setAction(action);
        auditLogRepository.save(log); // NFR10
    }
}
