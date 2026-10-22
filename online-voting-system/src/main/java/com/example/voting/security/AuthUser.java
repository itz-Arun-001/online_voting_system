package com.example.voting.security;

/** The authenticated principal, rebuilt from the JWT on every request. */
public record AuthUser(Long id, String email, String role) {
    public boolean isAdmin() { return "ADMIN".equals(role); }
}
