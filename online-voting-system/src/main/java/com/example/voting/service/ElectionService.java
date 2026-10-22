package com.example.voting.service;

import com.example.voting.config.AppProperties;
import com.example.voting.dto.Dtos.*;
import com.example.voting.entity.Candidate;
import com.example.voting.entity.CandidatePhoto;
import com.example.voting.entity.Election;
import com.example.voting.enums.ElectionStatus;
import com.example.voting.enums.ResultMode;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.CandidatePhotoRepository;
import com.example.voting.repository.CandidateRepository;
import com.example.voting.repository.ElectionRepository;
import com.example.voting.repository.EligibleVoterRepository;
import com.example.voting.repository.VoteRepository;
import com.example.voting.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ElectionService {
    private final ElectionRepository elections;
    private final CandidateRepository candidates;
    private final CandidatePhotoRepository photos;
    private final EligibleVoterRepository eligible;
    private final VoteRepository votes;
    private final AppProperties props;

    // ---------- lookups and rules ----------

    public Election get(Long id) {
        return elections.findById(id).orElseThrow(() -> ApiException.notFound("Election not found."));
    }

    /** Status is derived from the clock, so it is right even if the scheduler has not run yet. */
    public ElectionStatus currentStatus(Election e) {
        Instant now = Instant.now();
        if (e.getStatus() == ElectionStatus.CLOSED || !now.isBefore(e.getEndTime())) return ElectionStatus.CLOSED;
        if (now.isBefore(e.getStartTime())) return ElectionStatus.UPCOMING;
        return ElectionStatus.ACTIVE;
    }

    public boolean canViewResults(Election e) {
        return switch (e.getResultMode()) {
            case LIVE -> true;
            case ON_CLOSE -> currentStatus(e) == ElectionStatus.CLOSED;
            case SCHEDULED -> e.getResultsPublishAt() != null && !Instant.now().isBefore(e.getResultsPublishAt());
            case MANUAL -> e.isResultsPublished();
        };
    }

    public String hiddenMessage(Election e) {
        return switch (e.getResultMode()) {
            case ON_CLOSE -> "Results will be available once the election closes.";
            case SCHEDULED -> "Results will be available at " + DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a z", Locale.ENGLISH)
                    .withZone(ZoneId.of(props.getDisplayZone())).format(e.getResultsPublishAt()) + ".";
            case MANUAL -> "Results have not been published yet.";
            case LIVE -> "Results are available.";
        };
    }

    public boolean isEligible(Election e, String email) {
        return !e.isRestrictToEligibleList() || eligible.existsByElectionIdAndEmail(e.getId(), email);
    }

    /** True if this email could vote in at least one election, which is what allows an OTP to be issued. */
    public boolean hasAnyAccess(String email) {
        return eligible.existsByEmail(email)
                || elections.existsByRestrictToEligibleListFalseAndStatusNot(ElectionStatus.CLOSED);
    }

    public ElectionView toView(Election e, Boolean hasVoted) {
        return new ElectionView(e.getId(), e.getTitle(), e.getDescription(), e.getStartTime(), e.getEndTime(),
                currentStatus(e), e.getResultMode(), e.getResultsPublishAt(), e.isResultsPublished(),
                canViewResults(e), e.isRestrictToEligibleList(), hasVoted);
    }

    public List<ElectionView> listFor(AuthUser me) {
        return elections.findAllByOrderByStartTimeDesc().stream()
                .filter(e -> isEligible(e, me.email()))
                .map(e -> toView(e, votes.existsByElectionIdAndUserId(e.getId(), me.id())))
                .toList();
    }

    public List<ElectionView> listAll() {
        return elections.findAllByOrderByStartTimeDesc().stream().map(e -> toView(e, null)).toList();
    }

    public List<CandidateView> candidatesOf(Long electionId) {
        return candidates.findByElectionIdOrderByIdAsc(electionId).stream()
                .map(c -> new CandidateView(c.getId(), c.getName(), c.getDescription(), c.getPhotoUrl())).toList();
    }

    // ---------- admin: elections ----------

    @Transactional
    public Election create(ElectionRequest r, AuthUser admin) {
        Election e = new Election();
        apply(e, r);
        e.setCreatedBy(admin.id());
        return elections.save(e);
    }

    @Transactional
    public Election update(Long id, ElectionRequest r) {
        Election e = get(id);
        if (currentStatus(e) != ElectionStatus.UPCOMING) {
            throw ApiException.conflict("Only elections that have not started can be edited.");
        }
        apply(e, r);
        return elections.save(e);
    }

    @Transactional
    public void delete(Long id) {
        Election e = get(id);
        if (votes.countByElectionId(id) > 0) throw ApiException.conflict("This election has votes and cannot be deleted.");
        photos.deleteByElectionId(id);
        candidates.deleteByElectionId(id);
        eligible.deleteByElectionId(id);
        elections.delete(e);
    }

    @Transactional
    public Election close(Long id) {
        Election e = get(id);
        if (currentStatus(e) == ElectionStatus.CLOSED) throw ApiException.conflict("This election is already closed.");
        e.setStatus(ElectionStatus.CLOSED);
        Instant now = Instant.now();
        if (now.isBefore(e.getEndTime())) e.setEndTime(now);
        if (e.getStartTime().isAfter(e.getEndTime())) e.setStartTime(e.getEndTime());
        return elections.save(e);
    }

    @Transactional
    public Election setResultSettings(Long id, ResultSettingsRequest r) {
        Election e = get(id);
        if (r.mode() == ResultMode.SCHEDULED && r.resultsPublishAt() == null) {
            throw ApiException.badRequest("Choose the date and time to publish results.");
        }
        e.setResultMode(r.mode());
        e.setResultsPublishAt(r.mode() == ResultMode.SCHEDULED ? r.resultsPublishAt() : null);
        e.setResultsPublished(false);
        return elections.save(e);
    }

    @Transactional
    public Election publishResults(Long id) {
        Election e = get(id);
        if (e.getResultMode() != ResultMode.MANUAL) {
            throw ApiException.badRequest("Publishing by hand only applies when results are set to manual.");
        }
        e.setResultsPublished(true);
        return elections.save(e);
    }

    private void apply(Election e, ElectionRequest r) {
        if (!r.endTime().isAfter(r.startTime())) throw ApiException.badRequest("The end time must be after the start time.");
        ResultMode mode = r.resultMode() == null ? ResultMode.ON_CLOSE : r.resultMode();
        if (mode == ResultMode.SCHEDULED && r.resultsPublishAt() == null) {
            throw ApiException.badRequest("Choose the date and time to publish results.");
        }
        e.setTitle(r.title().trim());
        e.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        e.setStartTime(r.startTime());
        e.setEndTime(r.endTime());
        e.setResultMode(mode);
        e.setResultsPublishAt(mode == ResultMode.SCHEDULED ? r.resultsPublishAt() : null);
        e.setRestrictToEligibleList(r.restrictToEligibleList() == null || r.restrictToEligibleList());
    }

    // ---------- admin: candidates ----------

    @Transactional
    public Candidate addCandidate(Long electionId, CandidateRequest r) {
        Election e = get(electionId);
        requireEditableBallot(e);
        return candidates.save(new Candidate(electionId, r.name().trim(), blankToNull(r.description()), blankToNull(r.photoUrl())));
    }

    @Transactional
    public Candidate updateCandidate(Long candidateId, CandidateRequest r) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate not found."));
        requireEditableBallot(get(c.getElectionId()));
        c.setName(r.name().trim());
        c.setDescription(blankToNull(r.description()));
        // An uploaded photo wins over a pasted link; remove the photo first to switch back to a link.
        if (!photos.existsById(candidateId)) c.setPhotoUrl(blankToNull(r.photoUrl()));
        return candidates.save(c);
    }

    @Transactional
    public Candidate deleteCandidate(Long candidateId) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate not found."));
        requireEditableBallot(get(c.getElectionId()));
        photos.deleteById(candidateId);
        candidates.delete(c);
        return c;
    }

    private static final long MAX_PHOTO_BYTES = 2L * 1024 * 1024;

    @Transactional
    public Candidate setPhoto(Long candidateId, MultipartFile file) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate not found."));
        requireEditableBallot(get(c.getElectionId()));
        if (file == null || file.isEmpty()) throw ApiException.badRequest("Choose an image file.");
        if (file.getSize() > MAX_PHOTO_BYTES) throw ApiException.badRequest("The image must be 2 MB or smaller.");
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("Could not read that image.");
        }
        String type = imageType(data);
        if (type == null) throw ApiException.badRequest("Use a JPG, PNG, WebP or GIF image.");
        CandidatePhoto p = photos.findById(candidateId).orElseGet(CandidatePhoto::new);
        p.setCandidateId(candidateId);
        p.setContentType(type);
        p.setData(data);
        photos.save(p);
        c.setPhotoUrl("/api/public/candidates/" + candidateId + "/photo?v=" + System.currentTimeMillis());
        return candidates.save(c);
    }

    @Transactional
    public Candidate removePhoto(Long candidateId) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate not found."));
        requireEditableBallot(get(c.getElectionId()));
        photos.deleteById(candidateId);
        c.setPhotoUrl(null);
        return candidates.save(c);
    }

    /** Checks the file's real signature instead of trusting the filename or the browser's content type. SVG is refused on purpose. */
    private static String imageType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') return "image/gif";
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }

    private void requireEditableBallot(Election e) {
        if (votes.countByElectionId(e.getId()) > 0) {
            throw ApiException.conflict("Votes have already been cast, so the candidate list is locked.");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
