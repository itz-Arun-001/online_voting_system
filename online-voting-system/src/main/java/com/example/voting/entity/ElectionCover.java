package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "election_covers")
@Getter @Setter @NoArgsConstructor
public class ElectionCover {
    @Id
    @Column(name = "election_id")
    private Long electionId;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false, length = 2_097_152)
    private byte[] data;
}

