package com.ayush.zepto.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ayush.zepto.auth.dto.LoginRequest;
import com.ayush.zepto.auth.dto.RegisterRequest;
import com.ayush.zepto.common.error.ResourceConflictException;
import com.ayush.zepto.user.Role;
import com.ayush.zepto.user.User;
import com.ayush.zepto.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    @Mock JwtTokenService tokens;

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, passwords, tokens);
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndAlwaysCreatesCustomer() {
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        when(passwords.encode("Strong-pass-2026!")).thenReturn("bcrypt-hash");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        var result = auth.register(new RegisterRequest("PERSON@EXAMPLE.COM", "Strong-pass-2026!"));

        assertEquals("person@example.com", result.email());
        assertEquals(Role.CUSTOMER, result.role());
        verify(passwords).encode("Strong-pass-2026!");
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmail("person@example.com")).thenReturn(true);

        assertThrows(ResourceConflictException.class,
                () -> auth.register(new RegisterRequest("person@example.com", "Strong-pass-2026!")));
    }

    @Test
    void loginReturnsTokenForValidPassword() {
        User user = new User("person@example.com", "bcrypt-hash", Role.CUSTOMER);
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(passwords.matches("Strong-pass-2026!", "bcrypt-hash")).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new JwtTokenService.IssuedToken("signed-token", Instant.parse("2030-01-01T00:15:00Z")));

        var result = auth.login(new LoginRequest("PERSON@EXAMPLE.COM", "Strong-pass-2026!"));

        assertEquals("signed-token", result.accessToken());
        assertEquals("Bearer", result.tokenType());
        assertEquals(Role.CUSTOMER, result.role());
    }

    @Test
    void loginUsesGenericFailureForUnknownAccountOrWrongPassword() {
        when(users.findByEmail("person@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> auth.login(new LoginRequest("person@example.com", "Wrong-pass-2026!")));
    }
}
