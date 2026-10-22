package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_email", nullable = false, length = 254)
    private String adminEmail;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLog(String adminEmail, String action, String details) {
        this.adminEmail = adminEmail;
        this.action = action;
        this.details = details != null && details.length() > 1000 ? details.substring(0, 1000) : details;
    }
}
