package com.example.voting;

import com.example.voting.config.AppProperties;
import com.example.voting.dto.Dtos.ResultsResponse;
import com.example.voting.entity.*;
import com.example.voting.enums.ElectionStatus;
import com.example.voting.enums.ResultMode;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.*;
import com.example.voting.security.AuthUser;
import com.example.voting.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
class VotingFlowTest {
    @Autowired AuthService auth;
    @Autowired OtpService otp;
    @Autowired OtpTokenRepository otpRepo;
    @Autowired ElectionService electionService;
    @Autowired VoteService voteService;
    @Autowired ResultService resultService;
    @Autowired ElectionRepository elections;
    @Autowired CandidateRepository candidates;
    @Autowired EligibleVoterRepository eligible;
    @Autowired VoteRepository votes;
    @Autowired AppProperties props;

    private static int counter = 0;

    private String email(String prefix) {
        return prefix + (++counter) + "-" + System.nanoTime() % 100000 + "@" + props.domain();
    }

    private AuthUser voter(String email) {
        User u = auth.findOrCreateUser(email);
        return new AuthUser(u.getId(), u.getEmail(), "VOTER");
    }

    private Election election(Duration startOffset, Duration endOffset, boolean restricted, ResultMode mode) {
        Election e = new Election();
        e.setTitle("Test election");
        e.setStartTime(Instant.now().plus(startOffset));
        e.setEndTime(Instant.now().plus(endOffset));
        e.setRestrictToEligibleList(restricted);
        e.setResultMode(mode);
        return elections.save(e);
    }

    private Election openElection() {
        return election(Duration.ofMinutes(-5), Duration.ofHours(1), false, ResultMode.ON_CLOSE);
    }

    private Candidate candidate(Election e, String name) {
        return candidates.save(new Candidate(e.getId(), name, null, null));
    }

    private void assertStatus(HttpStatus expected, Runnable r) {
        ApiException ex = assertThrows(ApiException.class, r::run);
        assertEquals(expected, ex.getStatus(), ex.getMessage());
    }

    // ---------- OTP ----------

    @Test
    void nonCollegeEmailIsRejected() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> auth.requestOtp("someone@gmail.com", "10.0.0.1"));
    }

    @Test
    void wrongOtpIsRejectedAndLocksAfterMaxAttempts() {
        String em = email("otp");
        String code = otp.issue(em);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < props.getOtp().getMaxAttempts(); i++) {
            assertStatus(HttpStatus.UNAUTHORIZED, () -> otp.verify(em, wrong));
        }
        // Even the correct code no longer works.
        assertStatus(HttpStatus.UNAUTHORIZED, () -> otp.verify(em, code));
    }

    @Test
    void expiredOtpIsRejected() {
        String em = email("expired");
        String code = otp.issue(em);
        OtpToken t = otpRepo.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(em).orElseThrow();
        t.setExpiresAt(Instant.now().minusSeconds(5));
        otpRepo.save(t);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> otp.verify(em, code));
    }

    @Test
    void correctOtpWorksOnceOnly() {
        String em = email("good");
        String code = otp.issue(em);
        otp.verify(em, code);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> otp.verify(em, code));
    }

    @Test
    void requestingAnotherOtpInvalidatesTheOldOne() {
        String em = email("reissue");
        String first = otp.issue(em);
        otp.issue(em);
        assertStatus(HttpStatus.UNAUTHORIZED, () -> otp.verify(em, first));
    }

    @Test
    void otpRequestsAreRateLimitedPerEmail() {
        String em = props.normalizedAdminEmails().get(0);
        String ip = "10.9.9." + (counter++ % 200);
        int max = props.getRateLimit().getOtpRequests();
        for (int i = 0; i < max; i++) auth.requestOtp(em.toUpperCase(), ip);
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> auth.requestOtp(em, ip));
    }

    // ---------- voting ----------

    @Test
    void votingTwiceReturnsConflict() {
        Election e = openElection();
        Candidate c = candidate(e, "A");
        AuthUser v = voter(email("twice"));
        voteService.castVote(e.getId(), v, c.getId());
        assertStatus(HttpStatus.CONFLICT, () -> voteService.castVote(e.getId(), v, c.getId()));
        assertEquals(1, votes.countByElectionId(e.getId()));
    }

    @Test
    void votingBeforeStartAndAfterCloseFails() {
        Election upcoming = election(Duration.ofHours(1), Duration.ofHours(2), false, ResultMode.ON_CLOSE);
        Candidate c1 = candidate(upcoming, "A");
        assertStatus(HttpStatus.BAD_REQUEST, () -> voteService.castVote(upcoming.getId(), voter(email("early")), c1.getId()));

        Election closed = openElection();
        Candidate c2 = candidate(closed, "A");
        electionService.close(closed.getId());
        assertStatus(HttpStatus.BAD_REQUEST, () -> voteService.castVote(closed.getId(), voter(email("late")), c2.getId()));
    }

    @Test
    void voterOutsideTheEligibleListIsRejected() {
        Election e = election(Duration.ofMinutes(-5), Duration.ofHours(1), true, ResultMode.ON_CLOSE);
        Candidate c = candidate(e, "A");
        String listed = email("listed");
        String unlisted = email("unlisted");
        eligible.save(new EligibleVoter(e.getId(), listed, null, null));
        assertStatus(HttpStatus.FORBIDDEN, () -> voteService.castVote(e.getId(), voter(unlisted), c.getId()));
        assertDoesNotThrow(() -> voteService.castVote(e.getId(), voter(listed), c.getId()));
    }

    @Test
    void candidateFromAnotherElectionIsRejected() {
        Election e1 = openElection();
        Election e2 = openElection();
        Candidate other = candidate(e2, "Other");
        assertStatus(HttpStatus.BAD_REQUEST, () -> voteService.castVote(e1.getId(), voter(email("cross")), other.getId()));
    }

    @Test
    void parallelVotesFromOneUserSaveExactlyOneVote() throws Exception {
        Election e = openElection();
        Candidate c = candidate(e, "A");
        AuthUser v = voter(email("race"));
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    voteService.castVote(e.getId(), v, c.getId());
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                    // expected for all but one
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, ok.get());
        assertEquals(1, votes.countByElectionId(e.getId()));
    }

    // ---------- results visibility ----------

    @Test
    void onCloseResultsAreHiddenUntilTheElectionCloses() {
        Election e = election(Duration.ofMinutes(-5), Duration.ofHours(1), false, ResultMode.ON_CLOSE);
        assertFalse(electionService.canViewResults(e));
        electionService.close(e.getId());
        assertTrue(electionService.canViewResults(electionService.get(e.getId())));
    }

    @Test
    void scheduledResultsAreHiddenUntilThePublishTime() {
        Election e = election(Duration.ofMinutes(-5), Duration.ofMinutes(-1), false, ResultMode.SCHEDULED);
        e.setResultsPublishAt(Instant.now().plus(Duration.ofHours(1)));
        e = elections.save(e);
        assertEquals(ElectionStatus.CLOSED, electionService.currentStatus(e));
        assertFalse(electionService.canViewResults(e));
        e.setResultsPublishAt(Instant.now().minusSeconds(1));
        assertTrue(electionService.canViewResults(e));
    }

    @Test
    void manualResultsNeedPublishAndLiveResultsAreAlwaysVisible() {
        Election manual = election(Duration.ofMinutes(-5), Duration.ofHours(1), false, ResultMode.MANUAL);
        assertFalse(electionService.canViewResults(manual));
        assertTrue(electionService.canViewResults(electionService.publishResults(manual.getId())));
        assertTrue(electionService.canViewResults(election(Duration.ofMinutes(-5), Duration.ofHours(1), false, ResultMode.LIVE)));
    }

    @Test
    void adminCountsAreAvailableEvenWhileHiddenFromVoters() {
        Election e = openElection();
        Candidate c = candidate(e, "A");
        voteService.castVote(e.getId(), voter(email("count")), c.getId());
        assertFalse(electionService.canViewResults(e));
        ResultsResponse r = resultService.compute(e);
        assertEquals(1, r.totalVotes());
        assertEquals(1, r.candidates().get(0).votes());
    }
}
