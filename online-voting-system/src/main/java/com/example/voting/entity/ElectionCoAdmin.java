package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "election_co_admins",
        uniqueConstraints = @UniqueConstraint(name = "uk_election_co_admin", columnNames = {"election_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class ElectionCoAdmin {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt = Instant.now();

    public ElectionCoAdmin(Long electionId, Long userId) {
        this.electionId = electionId;
        this.userId = userId;
    }
}

