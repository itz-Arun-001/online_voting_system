package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Candidate photo bytes, kept in their own table so candidate lists stay light and photos survive redeploys. */
@Entity
@Table(name = "candidate_photos")
@Getter @Setter @NoArgsConstructor
public class CandidatePhoto {
    @Id
    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false, length = 2_097_152)
    private byte[] data;
}
