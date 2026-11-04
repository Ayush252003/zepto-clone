package com.ayush.zepto.auth.dto;

import com.ayush.zepto.user.Role;
import com.ayush.zepto.user.User;
import java.time.Instant;

public record UserResponse(Long id, String email, Role role, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
