package com.ayush.zepto.auth;

import com.ayush.zepto.auth.dto.LoginRequest;
import com.ayush.zepto.auth.dto.RegisterRequest;
import com.ayush.zepto.auth.dto.TokenResponse;
import com.ayush.zepto.auth.dto.UserResponse;
import com.ayush.zepto.common.error.BadRequestException;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.common.error.ResourceNotFoundException;
import com.ayush.zepto.user.Role;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtTokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        validatePasswordBytes(request.password());
        if (users.existsByEmail(email)) throw new ResourceConflictException("An account with that email already exists.");
        User user = users.save(new User(email, passwords.encode(request.password()), Role.CUSTOMER));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = users.findByEmail(email)
                .filter(found -> passwords.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        JwtTokenService.IssuedToken token = tokens.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        return users.findByEmail(email).map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User account no longer exists."));
    }

    @Transactional
    public void createBootstrapAdmin(String rawEmail, String rawPassword) {
        String email = normalizeEmail(rawEmail);
        validatePasswordBytes(rawPassword);
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() == Role.ADMIN) return;
            throw new IllegalStateException("Bootstrap admin email belongs to a CUSTOMER account; choose a new email.");
        }
        users.save(new User(email, passwords.encode(rawPassword), Role.ADMIN));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validatePasswordBytes(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) throw new BadRequestException("Password must be 8 to 72 UTF-8 bytes.");
    }
}
