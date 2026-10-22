package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "candidates", indexes = @Index(name = "idx_candidate_election", columnList = "election_id"))
@Getter @Setter @NoArgsConstructor
public class Candidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    public Candidate(Long electionId, String name, String description, String photoUrl) {
        this.electionId = electionId;
        this.name = name;
        this.description = description;
        this.photoUrl = photoUrl;
    }
}
