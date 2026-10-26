package com.example.voting.scheduler;

import com.example.voting.entity.Election;
import com.example.voting.enums.ElectionStatus;
import com.example.voting.enums.ResultMode;
import com.example.voting.repository.ElectionRepository;
import com.example.voting.repository.OtpTokenRepository;
import com.example.voting.service.AnnouncementService;
import com.example.voting.service.ElectionService;
import com.example.voting.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Background jobs. Status and visibility are also derived from the clock, so these only keep stored flags in step. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class Schedulers {
    private final ElectionRepository elections;
    private final ElectionService electionService;
    private final OtpTokenRepository otps;
    private final RateLimitService rateLimit;
    private final AnnouncementService announcementService;

    /** UPCOMING to ACTIVE at start time, ACTIVE to CLOSED at end time. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void syncElectionStatus() {
        for (Election e : elections.findByStatusNot(ElectionStatus.CLOSED)) {
            ElectionStatus now = electionService.currentStatus(e);
            if (now != e.getStatus()) {
                e.setStatus(now);
                elections.save(e);
            }
        }
    }

    /** Marks SCHEDULED and ON_CLOSE results as published once their moment arrives. */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void publishResults() {
        for (Election e : elections.findAll()) {
            boolean auto = e.getResultMode() == ResultMode.SCHEDULED || e.getResultMode() == ResultMode.ON_CLOSE;
            if (auto && !e.isResultsPublished() && electionService.canViewResults(e)) {
                e.setResultsPublished(true);
                elections.save(e);
                electionService.autoAnnounceWinnerIfDue(e.getId());
            }
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanup() {
        otps.deleteStale(Instant.now());
        rateLimit.purge();
    }

    @Scheduled(fixedDelay = 60_000)
    public void publishAnnouncements() {
        announcementService.publishDue();
    }
}
