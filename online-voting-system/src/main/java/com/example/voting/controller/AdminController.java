package com.example.voting.controller;

import com.example.voting.dto.Dtos.*;
import com.example.voting.dto.PageResponse;
import com.example.voting.dto.VoterRow;
import com.example.voting.entity.AuditLog;
import com.example.voting.entity.Candidate;
import com.example.voting.entity.Election;
import com.example.voting.entity.EligibleVoter;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.AuditLogRepository;
import com.example.voting.repository.EligibleVoterRepository;
import com.example.voting.repository.VoteRepository;
import com.example.voting.security.AuthUser;
import com.example.voting.service.*;
import com.example.voting.util.CsvUtil;
import com.example.voting.util.EmailUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Everything under /api/admin requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final ElectionService electionService;
    private final VoterListService voterListService;
    private final ResultService resultService;
    private final SseService sse;
    private final AuditService audit;
    private final VoteRepository votes;
    private final EligibleVoterRepository eligible;
    private final AuditLogRepository auditLogs;

    // ---------- elections ----------

    @GetMapping("/elections")
    public List<ElectionView> elections() {
        return electionService.listAll();
    }

    @PostMapping("/elections")
    public ElectionView create(@Valid @RequestBody ElectionRequest r, @AuthenticationPrincipal AuthUser me) {
        Election e = electionService.create(r, me);
        audit.log(me, "CREATE_ELECTION", "id=" + e.getId() + " title=" + e.getTitle());
        return electionService.toView(e, null);
    }

    @PutMapping("/elections/{id}")
    public ElectionView update(@PathVariable Long id, @Valid @RequestBody ElectionRequest r, @AuthenticationPrincipal AuthUser me) {
        Election e = electionService.update(id, r);
        audit.log(me, "EDIT_ELECTION", "id=" + id + " title=" + e.getTitle());
        return electionService.toView(e, null);
    }

    @DeleteMapping("/elections/{id}")
    public MessageResponse delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        String title = electionService.get(id).getTitle();
        electionService.delete(id);
        audit.log(me, "DELETE_ELECTION", "id=" + id + " title=" + title);
        return new MessageResponse("Election deleted.");
    }

    @PutMapping("/elections/{id}/close")
    public ElectionView close(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        Election e = electionService.close(id);
        audit.log(me, "CLOSE_ELECTION", "id=" + id);
        sse.broadcast(e);
        return electionService.toView(e, null);
    }

    // ---------- candidates ----------

    @GetMapping("/elections/{id}/candidates")
    public List<CandidateView> candidates(@PathVariable Long id) {
        electionService.get(id);
        return electionService.candidatesOf(id);
    }

    @PostMapping("/elections/{id}/candidates")
    public CandidateView addCandidate(@PathVariable Long id, @Valid @RequestBody CandidateRequest r, @AuthenticationPrincipal AuthUser me) {
        Candidate c = electionService.addCandidate(id, r);
        audit.log(me, "ADD_CANDIDATE", "election=" + id + " name=" + c.getName());
        return view(c);
    }

    @PutMapping("/candidates/{candidateId}")
    public CandidateView updateCandidate(@PathVariable Long candidateId, @Valid @RequestBody CandidateRequest r, @AuthenticationPrincipal AuthUser me) {
        Candidate c = electionService.updateCandidate(candidateId, r);
        audit.log(me, "EDIT_CANDIDATE", "id=" + candidateId + " name=" + c.getName());
        return view(c);
    }

    @PostMapping(value = "/candidates/{candidateId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CandidateView uploadPhoto(@PathVariable Long candidateId, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthUser me) {
        Candidate c = electionService.setPhoto(candidateId, file);
        audit.log(me, "SET_CANDIDATE_PHOTO", "id=" + candidateId + " name=" + c.getName());
        return view(c);
    }

    @DeleteMapping("/candidates/{candidateId}/photo")
    public CandidateView removePhoto(@PathVariable Long candidateId, @AuthenticationPrincipal AuthUser me) {
        Candidate c = electionService.removePhoto(candidateId);
        audit.log(me, "REMOVE_CANDIDATE_PHOTO", "id=" + candidateId + " name=" + c.getName());
        return view(c);
    }

    @DeleteMapping("/candidates/{candidateId}")
    public MessageResponse deleteCandidate(@PathVariable Long candidateId, @AuthenticationPrincipal AuthUser me) {
        Candidate c = electionService.deleteCandidate(candidateId);
        audit.log(me, "DELETE_CANDIDATE", "election=" + c.getElectionId() + " name=" + c.getName());
        return new MessageResponse("Candidate removed.");
    }

    // ---------- eligible voters ----------

    @PostMapping("/elections/{id}/voters/upload")
    public UploadSummary upload(@PathVariable Long id, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthUser me) {
        UploadSummary s = voterListService.importCsv(id, file);
        audit.log(me, "UPLOAD_VOTERS", "election=" + id + " added=" + s.added() + " duplicates=" + s.duplicates() + " invalid=" + s.invalid());
        return s;
    }

    @GetMapping("/elections/{id}/voters/eligible")
    public PageResponse<VoterRow> eligibleList(@PathVariable Long id, @RequestParam(defaultValue = "") String q,
                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        electionService.get(id);
        Page<EligibleVoter> p = eligible.findByElectionIdAndEmailContainingIgnoreCaseOrderByEmailAsc(id, q.trim(), pageable(page, size));
        return PageResponse.of(p, ev -> new VoterRow(ev.getEmail(), ev.getName(), null));
    }

    @DeleteMapping("/elections/{id}/voters/{email}")
    public MessageResponse removeVoter(@PathVariable Long id, @PathVariable String email, @AuthenticationPrincipal AuthUser me) {
        String normalized = EmailUtil.normalize(email);
        if (eligible.deleteByElectionIdAndEmail(id, normalized) == 0) throw ApiException.notFound("That voter is not on the list.");
        audit.log(me, "REMOVE_VOTER", "election=" + id + " email=" + normalized);
        return new MessageResponse("Voter removed.");
    }

    // ---------- monitoring: who voted, never whom ----------

    @GetMapping("/elections/{id}/voters/voted")
    public PageResponse<VoterRow> voted(@PathVariable Long id, @RequestParam(defaultValue = "") String q,
                                        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        electionService.get(id);
        Page<VoterRow> p = votes.findVoted(id, q.trim(), pageable(page, size));
        return PageResponse.of(p, r -> r);
    }

    @GetMapping("/elections/{id}/voters/not-voted")
    public PageResponse<VoterRow> notVoted(@PathVariable Long id, @RequestParam(defaultValue = "") String q,
                                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        electionService.get(id);
        Page<EligibleVoter> p = eligible.findNotVoted(id, q.trim(), pageable(page, size));
        return PageResponse.of(p, ev -> new VoterRow(ev.getEmail(), ev.getName(), null));
    }

    @GetMapping("/elections/{id}/turnout")
    public Turnout turnout(@PathVariable Long id) {
        electionService.get(id);
        long eligibleCount = eligible.countByElectionId(id);
        long voted = votes.countByElectionId(id);
        long pending = Math.max(eligibleCount - voted, 0);
        double percent = eligibleCount > 0 ? Math.round(voted * 1000.0 / eligibleCount) / 10.0 : 0.0;
        return new Turnout(eligibleCount, voted, pending, percent);
    }

    @GetMapping("/elections/{id}/voters/export")
    public ResponseEntity<byte[]> exportVoters(@PathVariable Long id, @RequestParam(defaultValue = "voted") String type,
                                               @AuthenticationPrincipal AuthUser me) {
        electionService.get(id);
        StringBuilder sb = new StringBuilder();
        if ("not-voted".equals(type)) {
            sb.append(CsvUtil.row("email", "name"));
            for (EligibleVoter ev : eligible.findNotVoted(id, "", Pageable.unpaged())) sb.append(CsvUtil.row(ev.getEmail(), ev.getName()));
        } else {
            sb.append(CsvUtil.row("email", "name", "voted_at"));
            for (VoterRow r : votes.findVoted(id, "", Pageable.unpaged())) sb.append(CsvUtil.row(r.email(), r.name(), r.votedAt()));
        }
        audit.log(me, "EXPORT_VOTERS", "election=" + id + " type=" + type);
        return csv(sb.toString(), "election-" + id + "-" + ("not-voted".equals(type) ? "not-voted" : "voted") + ".csv");
    }

    // ---------- results ----------

    @PutMapping("/elections/{id}/result-settings")
    public ElectionView resultSettings(@PathVariable Long id, @Valid @RequestBody ResultSettingsRequest r, @AuthenticationPrincipal AuthUser me) {
        Election e = electionService.setResultSettings(id, r);
        audit.log(me, "RESULT_SETTINGS", "election=" + id + " mode=" + r.mode() + " publishAt=" + r.resultsPublishAt());
        sse.broadcast(e);
        return electionService.toView(e, null);
    }

    @PostMapping("/elections/{id}/publish-results")
    public ElectionView publish(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        Election e = electionService.publishResults(id);
        audit.log(me, "PUBLISH_RESULTS", "election=" + id);
        return electionService.toView(e, null);
    }

    /** Admins always see live counts, whatever the result mode. */
    @GetMapping("/elections/{id}/results")
    public ResultsResponse results(@PathVariable Long id) {
        return resultService.compute(electionService.get(id));
    }

    @GetMapping("/elections/{id}/results/export")
    public ResponseEntity<byte[]> exportResults(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        ResultsResponse r = resultService.compute(electionService.get(id));
        StringBuilder sb = new StringBuilder(CsvUtil.row("candidate", "votes"));
        r.candidates().forEach(c -> sb.append(CsvUtil.row(c.name(), c.votes())));
        sb.append(CsvUtil.row("Total", r.totalVotes()));
        audit.log(me, "EXPORT_RESULTS", "election=" + id);
        return csv(sb.toString(), "election-" + id + "-results.csv");
    }

    // ---------- audit ----------

    @GetMapping("/audit-logs")
    public PageResponse<AuditLog> auditLogs(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return PageResponse.of(auditLogs.findAllByOrderByCreatedAtDesc(pageable(page, size)), a -> a);
    }

    // ---------- helpers ----------

    private static Pageable pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static CandidateView view(Candidate c) {
        return new CandidateView(c.getId(), c.getName(), c.getDescription(), c.getPhotoUrl());
    }

    private static ResponseEntity<byte[]> csv(String body, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
