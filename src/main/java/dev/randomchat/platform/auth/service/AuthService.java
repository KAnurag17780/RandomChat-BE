package dev.randomchat.platform.auth.service;

import dev.randomchat.platform.auth.dto.AuthResponse;
import dev.randomchat.platform.auth.dto.LoginRequest;
import dev.randomchat.platform.auth.dto.RegisterRequest;
import dev.randomchat.platform.common.exception.AuthenticationFailedException;
import dev.randomchat.platform.common.exception.DuplicateResourceException;
import dev.randomchat.platform.common.exception.ResourceNotFoundException;
import dev.randomchat.platform.user.entity.UserEntity;
import dev.randomchat.platform.user.entity.UserProfileEntity;
import dev.randomchat.platform.user.repository.UserProfileRepository;
import dev.randomchat.platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles user registration, login, and session-based authentication.
 * <p>
 * Registration creates both the {@code users} and {@code user_profiles} records
 * in a single transaction. Email is normalized (lowercase, trimmed) before any
 * uniqueness check or storage.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthService(UserRepository userRepository,
                       UserProfileRepository profileRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       SecurityContextRepository securityContextRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Registers a new user account and profile.
     *
     * @throws DuplicateResourceException if email or username already exists
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().toLowerCase().trim();
        String username = request.username().trim();

        // Check uniqueness
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("User", "email", normalizedEmail);
        }
        if (profileRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("Profile", "username", username);
        }

        // Create user
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity(userId, normalizedEmail, passwordEncoder.encode(request.password()));
        userRepository.save(user);

        // Create profile
        UUID profileId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity(profileId, userId, username);
        profile.setDisplayName(username); // Default display name is the username
        profileRepository.save(profile);

        log.info("User registered: userId={}, username={}", userId, username);

        return new AuthResponse(
                userId,
                normalizedEmail,
                username,
                profile.getDisplayName(),
                user.getCreatedAt()
        );
    }

    /**
     * Authenticates a user and establishes a session.
     *
     * @throws AuthenticationFailedException if credentials are invalid
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, null, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String normalizedEmail = request.email().toLowerCase().trim();

        // Spring Security authentication — will throw if credentials are bad
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        if (httpRequest != null && httpResponse != null) {
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
        }

        // The principal name is the user ID (set by CustomUserDetailsService)
        UUID userId = UUID.fromString(authentication.getName());

        // Update last login timestamp
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("User not found after authentication"));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId.toString()));

        log.info("User logged in: userId={}", userId);

        return new AuthResponse(
                userId,
                user.getEmail(),
                profile.getUsername(),
                profile.getDisplayName(),
                user.getCreatedAt()
        );
    }

    /**
     * Returns the currently authenticated user's information.
     */
    @Transactional(readOnly = true)
    public AuthResponse getCurrentUser() {
        UUID userId = getAuthenticatedUserId();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
        UserProfileEntity profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Profile", userId.toString()));

        return new AuthResponse(
                userId,
                user.getEmail(),
                profile.getUsername(),
                profile.getDisplayName(),
                user.getCreatedAt()
        );
    }

    /**
     * Extracts the authenticated user's UUID from the security context.
     */
    public UUID getAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AuthenticationFailedException("Not authenticated");
        }
        return UUID.fromString(auth.getName());
    }
}
