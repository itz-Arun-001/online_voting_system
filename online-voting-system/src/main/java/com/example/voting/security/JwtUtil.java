package com.example.voting.security;

import com.example.voting.config.AppProperties;
import com.example.voting.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    private final AppProperties props;
    private SecretKey key;

    public JwtUtil(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret missing or too short. Set JWT_SECRET to at least 32 characters.");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long expirySeconds() {
        return props.getJwt().getExpiryMinutes() * 60L;
    }

    public String generate(User user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirySeconds() * 1000))
                .signWith(key)
                .compact();
    }

    /** Throws io.jsonwebtoken.JwtException if the token is invalid or expired. */
    public AuthUser parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        long uid = ((Number) c.get("uid")).longValue();
        return new AuthUser(uid, c.getSubject(), String.valueOf(c.get("role")));
    }
}
