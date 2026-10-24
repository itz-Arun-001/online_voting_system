package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** The unique constraint on (election_id, user_id) is what guarantees one vote per person, even under races. */
@Entity
@Table(name = "votes",
        uniqueConstraints = @UniqueConstraint(name = "uk_vote_election_user", columnNames = {"election_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class Vote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt = Instant.now();

    @Column(name = "receipt_token", length = 64)
    private String receiptToken;

    public Vote(Long electionId, Long userId, Long candidateId) {
        this.electionId = electionId;
        this.userId = userId;
        this.candidateId = candidateId;
    }
}
