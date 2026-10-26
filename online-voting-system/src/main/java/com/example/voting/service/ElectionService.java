package com.example.voting.service;

import com.example.voting.config.AppProperties;
import com.example.voting.dto.Dtos.*;
import com.example.voting.entity.Candidate;
import com.example.voting.entity.CandidatePhoto;
import com.example.voting.entity.Election;
import com.example.voting.entity.ElectionCoAdmin;
import com.example.voting.entity.ElectionCover;
import com.example.voting.entity.Announcement;
import com.example.voting.enums.ElectionStatus;
import com.example.voting.enums.ResultMode;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.CandidatePhotoRepository;
import com.example.voting.repository.CandidateRepository;
import com.example.voting.repository.ElectionCoAdminRepository;
import com.example.voting.repository.ElectionCoverRepository;
import com.example.voting.repository.ElectionRepository;
import com.example.voting.repository.EligibleVoterRepository;
import com.example.voting.repository.VoteRepository;
import com.example.voting.security.AuthUser;
import com.example.voting.util.EmailUtil;
import com.example.voting.repository.CandidateApplicationRepository;
import com.example.voting.repository.AnnouncementRepository;
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
    private final ElectionCoverRepository covers;
    private final EligibleVoterRepository eligible;
    private final VoteRepository votes;
    private final ElectionCoAdminRepository electionCoAdmins;
    private final CandidateApplicationRepository applications;
    private final AnnouncementRepository announcements;
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
        String norm = EmailUtil.normalize(email);
        if (norm == null) return false;
        if (!isAllowedDomain(e, norm)) return false;
        return !e.isRestrictToEligibleList() || eligible.existsByElectionIdAndEmail(e.getId(), norm);
    }

    /** True if this email could vote in at least one election, which is what allows an OTP to be issued. */
    public boolean hasAnyAccess(String email) {
        return eligible.existsByEmail(email)
                || elections.existsByRestrictToEligibleListFalseAndStatusNot(ElectionStatus.CLOSED);
    }

    /**
     * Returns the effective list of allowed email domains for the election.
     * When the election has no custom domains the global college domain is used.
     */
    public List<String> effectiveDomains(Election e) {
        String raw = e.getAllowedDomains();
        if (raw == null || raw.isBlank()) {
            return List.of(props.domain());
        }
        return java.util.Arrays.stream(raw.split("[,;\\s]+"))
                .map(String::trim)
                .map(d -> d.startsWith("@") ? d.substring(1) : d)
                .map(d -> d.toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .distinct()
                .toList();
    }

    /** Returns true if the email ends with one of this election's allowed domains. */
    public boolean isAllowedDomain(Election e, String email) {
        String norm = EmailUtil.normalize(email);
        if (norm == null) return false;
        return effectiveDomains(e).stream().anyMatch(domain -> norm.endsWith("@" + domain));
    }

    /**
     * True if this email could vote in at least one election (used to gate OTP issue).
     * Checks eligibility list AND per-election domain restrictions on open elections.
     */
    public boolean hasAnyAccessWithDomains(String email) {
        if (eligible.existsByEmail(email)) return true;
        // Check non-closed elections that don't restrict to an eligible list
        return elections.findByStatusNot(ElectionStatus.CLOSED).stream()
                .filter(e -> !e.isRestrictToEligibleList())
                .anyMatch(e -> isAllowedDomain(e, email));
    }

    /** Returns true if the email domain is accepted anywhere — global domains, voter list, or any per-election domain. */
    public boolean isEmailDomainAllowedAnywhere(String email, com.example.voting.config.AppProperties props) {
        String norm = EmailUtil.normalize(email);
        if (norm == null) return false;
        if (props.hasCollegeDomain(norm)) return true;
        if (eligible.existsByEmail(norm)) return true;
        return elections.findAll().stream().anyMatch(e -> isAllowedDomain(e, norm));
    }

    /** Returns all known allowed domains across configuration and elections. */
    public java.util.Set<String> allKnownDomains(com.example.voting.config.AppProperties props) {
        java.util.Set<String> knownDomains = new java.util.LinkedHashSet<>(props.domains());
        elections.findAll().forEach(e -> knownDomains.addAll(effectiveDomains(e)));
        return knownDomains;
    }

    /** Builds a human-readable hint listing all known domains (global + per-election). */
    public String allKnownDomainsHint(com.example.voting.config.AppProperties props) {
        java.util.Set<String> knownDomains = allKnownDomains(props);
        if (knownDomains.isEmpty()) return "@" + props.domain();
        return knownDomains.stream().map(d -> "@" + d).collect(java.util.stream.Collectors.joining(", "));
    }

    public ElectionView toView(Election e, Boolean hasVoted) {
        String winnerName = null;
        Long winnerVotes = null;
        if (canViewResults(e) && currentStatus(e) == ElectionStatus.CLOSED) {
            List<Object[]> counts = votes.countByCandidate(e.getId());
            if (!counts.isEmpty()) {
                long maxVotes = -1;
                Long topCandId = null;
                boolean tie = false;
                for (Object[] row : counts) {
                    long cVotes = ((Number) row[1]).longValue();
                    if (cVotes > maxVotes) {
                        maxVotes = cVotes;
                        topCandId = ((Number) row[0]).longValue();
                        tie = false;
                    } else if (cVotes == maxVotes && maxVotes > 0) {
                        tie = true;
                    }
                }
                if (topCandId != null) {
                    winnerVotes = Math.max(0, maxVotes);
                    if (tie) {
                        winnerName = "Tie for 1st Place";
                    } else {
                        winnerName = candidates.findById(topCandId).map(Candidate::getName).orElse("Winner");
                    }
                }
            }
        }
        return new ElectionView(e.getId(), e.getTitle(), e.getDescription(), e.getStartTime(), e.getEndTime(),
                currentStatus(e), e.getResultMode(), e.getResultsPublishAt(), e.isResultsPublished(),
                canViewResults(e), e.isRestrictToEligibleList(), hasVoted, e.getAllowedDomains(),
                e.getCoverUrl(), winnerName, winnerVotes);
    }

    public List<ElectionView> listFor(AuthUser me) {
        return elections.findAllByOrderByStartTimeDesc().stream()
                .filter(e -> isEligible(e, me.email()) && isAllowedDomain(e, me.email()))
                .map(e -> toView(e, votes.existsByElectionIdAndUserId(e.getId(), me.id())))
                .toList();
    }

    public List<ElectionView> listAll() {
        return elections.findAllByOrderByStartTimeDesc().stream().map(e -> toView(e, null)).toList();
    }

    public List<ElectionView> listForAdmin(AuthUser user) {
        if (user.isFullAdmin()) return listAll();
        List<Long> assigned = electionCoAdmins.findByUserId(user.id()).stream()
                .map(ElectionCoAdmin::getElectionId).toList();
        return elections.findAllByOrderByStartTimeDesc().stream()
                .filter(e -> assigned.contains(e.getId()))
                .map(e -> toView(e, null))
                .toList();
    }

    public void requireElectionAccess(Long electionId, AuthUser user) {
        if (user.isFullAdmin()) return;
        if (user.isCoAdmin() && electionCoAdmins.existsByElectionIdAndUserId(electionId, user.id())) return;
        throw ApiException.forbidden("You do not have permission to manage this election.");
    }

    public List<CandidateView> candidatesOf(Long electionId) {
        return candidates.findByElectionIdOrderByIdAsc(electionId).stream()
                .map(c -> new CandidateView(c.getId(), c.getName(), c.getDescription(), c.getManifesto(), c.getPhotoUrl())).toList();
    }

    public List<CandidateViewFull> candidatesWithManifesto(Long electionId) {
        return candidates.findByElectionIdOrderByIdAsc(electionId).stream()
                .map(c -> new CandidateViewFull(c.getId(), c.getName(), c.getDescription(), c.getManifesto(), c.getPhotoUrl())).toList();
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
        votes.deleteByElectionId(id);
        photos.deleteByElectionId(id);
        covers.deleteById(id);
        candidates.deleteByElectionId(id);
        eligible.deleteByElectionId(id);
        electionCoAdmins.deleteByElectionId(id);
        applications.deleteByElectionId(id);
        announcements.deleteByElectionId(id);
        elections.delete(e);
    }

    @Transactional
    public void deleteAllElections() {
        votes.deleteAll();
        photos.deleteAll();
        covers.deleteAll();
        candidates.deleteAll();
        eligible.deleteAll();
        electionCoAdmins.deleteAll();
        applications.deleteAll();
        announcements.deleteAll();
        elections.deleteAll();
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
        // Normalise and persist the allowed domains list
        String domains = r.allowedDomains() == null ? null : r.allowedDomains().trim();
        e.setAllowedDomains(domains == null || domains.isBlank() ? null : domains);
        if (r.coverUrl() != null) {
            e.setCoverUrl(r.coverUrl().isBlank() ? null : r.coverUrl().trim());
        }
    }

    @Transactional
    public Election save(Election e) {
        return elections.save(e);
    }

    @Transactional
    public Election setCoverPhoto(Long electionId, MultipartFile file) {
        Election e = get(electionId);
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
        ElectionCover c = covers.findById(electionId).orElseGet(ElectionCover::new);
        c.setElectionId(electionId);
        c.setContentType(type);
        c.setData(data);
        covers.save(c);
        e.setCoverUrl("/api/public/elections/" + electionId + "/cover?v=" + System.currentTimeMillis());
        return elections.save(e);
    }

    @Transactional
    public Election removeCoverPhoto(Long electionId) {
        Election e = get(electionId);
        covers.deleteById(electionId);
        e.setCoverUrl(null);
        return elections.save(e);
    }

    @Transactional
    public Announcement announceWinner(Long electionId, AuthUser admin) {
        Election e = get(electionId);
        requireElectionAccess(electionId, admin);
        List<Object[]> counts = votes.countByCandidate(electionId);
        String winnerInfo;
        if (counts.isEmpty()) {
            winnerInfo = "Voting for '" + e.getTitle() + "' has concluded. No votes were recorded.";
        } else {
            long maxVotes = -1;
            Long topCandId = null;
            boolean tie = false;
            for (Object[] row : counts) {
                long cVotes = ((Number) row[1]).longValue();
                if (cVotes > maxVotes) {
                    maxVotes = cVotes;
                    topCandId = ((Number) row[0]).longValue();
                    tie = false;
                } else if (cVotes == maxVotes && maxVotes > 0) {
                    tie = true;
                }
            }
            if (tie) {
                winnerInfo = "🏆 Official Result: The election '" + e.getTitle() + "' has concluded in a tie for 1st place with " + maxVotes + " votes each! Certified results are now published.";
            } else if (topCandId != null) {
                String candName = candidates.findById(topCandId).map(Candidate::getName).orElse("Candidate");
                winnerInfo = "🏆 Official Result: " + candName + " has won the election '" + e.getTitle() + "' with " + maxVotes + " votes! Congratulations to all candidates and student voters.";
            } else {
                winnerInfo = "Voting for '" + e.getTitle() + "' has concluded.";
            }
        }
        Announcement a = new Announcement();
        a.setElectionId(electionId);
        a.setMessage(winnerInfo);
        a.setPublishedAt(Instant.now());
        a.setCreatedBy(admin.id());
        return announcements.save(a);
    }

    @Transactional
    public void autoAnnounceWinnerIfDue(Long electionId) {
        Election e = get(electionId);
        boolean alreadyAnnounced = announcements.findVisibleForElection(electionId).stream()
                .anyMatch(a -> a.getMessage() != null && a.getMessage().contains("Official Result"));
        if (!alreadyAnnounced) {
            List<Object[]> counts = votes.countByCandidate(electionId);
            if (!counts.isEmpty()) {
                long maxVotes = -1;
                Long topCandId = null;
                boolean tie = false;
                for (Object[] row : counts) {
                    long cVotes = ((Number) row[1]).longValue();
                    if (cVotes > maxVotes) {
                        maxVotes = cVotes;
                        topCandId = ((Number) row[0]).longValue();
                        tie = false;
                    } else if (cVotes == maxVotes && maxVotes > 0) {
                        tie = true;
                    }
                }
                String winnerInfo;
                if (tie) {
                    winnerInfo = "🏆 Official Result: The election '" + e.getTitle() + "' has concluded in a tie for 1st place with " + maxVotes + " votes each! Certified results are now published.";
                } else if (topCandId != null) {
                    String candName = candidates.findById(topCandId).map(Candidate::getName).orElse("Candidate");
                    winnerInfo = "🏆 Official Result: " + candName + " has won the election '" + e.getTitle() + "' with " + maxVotes + " votes! Congratulations to all candidates and student voters.";
                } else {
                    return;
                }
                Announcement a = new Announcement();
                a.setElectionId(electionId);
                a.setMessage(winnerInfo);
                a.setPublishedAt(Instant.now());
                a.setCreatedBy(e.getCreatedBy() != null ? e.getCreatedBy() : 1L);
                announcements.save(a);
            }
        }
    }

    // ---------- admin: candidates ----------

    @Transactional
    public Candidate addCandidate(Long electionId, CandidateRequest r) {
        Election e = get(electionId);
        requireEditableBallot(e);
        Candidate c = new Candidate(electionId, r.name().trim(), blankToNull(r.description()), blankToNull(r.photoUrl()));
        c.setManifesto(blankToNull(r.manifesto()));
        return candidates.save(c);
    }

    @Transactional
    public Candidate updateCandidate(Long candidateId, CandidateRequest r) {
        Candidate c = candidates.findById(candidateId).orElseThrow(() -> ApiException.notFound("Candidate not found."));
        requireEditableBallot(get(c.getElectionId()));
        c.setName(r.name().trim());
        c.setDescription(blankToNull(r.description()));
        c.setManifesto(blankToNull(r.manifesto()));
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
    public static String imageType(byte[] d) {
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
