package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "candidate_applications")
@Getter @Setter @NoArgsConstructor
public class CandidateApplication {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 2000)
    private String manifesto;

    @Column(nullable = false, length = 254)
    private String applicantEmail;

    /** PENDING, APPROVED, REJECTED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "photo_content_type", length = 50)
    private String photoContentType;

    @Column(name = "photo_data", length = 2_097_152)
    private byte[] photoData;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;
}
