package com.example.voting.entity;

import com.example.voting.enums.ElectionStatus;
import com.example.voting.enums.ResultMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "elections")
@Getter @Setter @NoArgsConstructor
public class Election {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ElectionStatus status = ElectionStatus.UPCOMING;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_mode", nullable = false, length = 20)
    private ResultMode resultMode = ResultMode.ON_CLOSE;

    @Column(name = "results_publish_at")
    private Instant resultsPublishAt;

    @Column(name = "results_published", nullable = false)
    private boolean resultsPublished = false;

    @Column(name = "restrict_to_eligible_list", nullable = false)
    private boolean restrictToEligibleList = true;

    @Column(name = "created_by")
    private Long createdBy;

    /**
     * Comma-separated allowed email domains (without leading @), e.g.
     * "student.annauniv.edu,staff.annauniv.edu".
     * When blank/null the global app.college-domain is used as the sole allowed domain.
     */
    @Column(name = "allowed_domains", length = 1000)
    private String allowedDomains;
}
