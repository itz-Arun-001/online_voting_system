package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "announcements")
@Getter @Setter @NoArgsConstructor
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = global announcement visible to all voters. */
    @Column(name = "election_id")
    private Long electionId;

    @Column(nullable = false, length = 2000)
    private String message;

    /** When to auto-publish (null = publish immediately). */
    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    /** Set when the announcement is live. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
