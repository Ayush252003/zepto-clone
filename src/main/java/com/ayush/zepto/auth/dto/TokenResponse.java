package com.ayush.zepto.auth.dto;

import com.ayush.zepto.user.Role;
import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, Long userId,
                            String email, Role role) {}
