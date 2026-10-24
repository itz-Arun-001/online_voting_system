package com.example.voting.dto;

import com.example.voting.enums.ElectionStatus;
import com.example.voting.enums.ResultMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    public record OtpRequest(@NotBlank String email) {}
    public record VerifyRequest(@NotBlank String email, @NotBlank String otp) {}
    public record AuthResponse(String token, String email, String role, long expiresIn) {}
    public record MessageResponse(String message) {}

    public record ElectionRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull Instant startTime,
            @NotNull Instant endTime,
            ResultMode resultMode,
            Instant resultsPublishAt,
            Boolean restrictToEligibleList,
            /** Comma-separated extra email domains allowed for this election (e.g. "student.annauniv.edu"). Leave blank to use the global domain only. */
            @Size(max = 1000) String allowedDomains) {}

    public record ResultSettingsRequest(@NotNull ResultMode mode, Instant resultsPublishAt) {}

    public record CandidateRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 2000) String manifesto,
            @Size(max = 500) String photoUrl) {}

    public record VoteRequest(@NotNull Long candidateId) {}

    public record ElectionView(Long id, String title, String description, Instant startTime, Instant endTime,
                               ElectionStatus status, ResultMode resultMode, Instant resultsPublishAt,
                               boolean resultsPublished, boolean resultsVisible, boolean restrictToEligibleList,
                               Boolean hasVoted, String allowedDomains) {}

    public record CandidateView(Long id, String name, String description, String manifesto, String photoUrl) {}
    public record CandidateResult(Long candidateId, String name, long votes) {}
    public record ResultsResponse(Long electionId, String title, ElectionStatus status, long totalVotes,
                                  List<CandidateResult> candidates) {}
    public record Turnout(long eligible, long voted, long pending, double turnoutPercent) {}
    public record MyStatus(boolean hasVoted, Instant votedAt, String receiptToken) {}
    public record UploadSummary(int added, int duplicates, int invalid, List<String> invalidRows) {}

    // Announcements
    public record AnnouncementRequest(@NotBlank @Size(max = 2000) String message, Long electionId, Instant scheduledAt) {}
    public record AnnouncementView(Long id, Long electionId, String message, Instant publishedAt, Instant scheduledAt) {}

    // Candidate applications
    public record ApplicationRequest(@NotBlank @Size(max = 150) String name, @Size(max = 2000) String manifesto) {}
    public record ApplicationView(Long id, Long electionId, String name, String manifesto, String applicantEmail, String status, Instant appliedAt, String photoUrl) {}

    // Co-admin
    public record PromoteRequest(@NotBlank String email, String name) {}
    public record AdminUserView(Long id, String email, String name, String role, List<Long> assignedElectionIds) {}
    public record AssignElectionsRequest(List<Long> electionIds) {}

    // Anonymity receipt
    public record VoteReceipt(String receiptToken, String message) {}

    // Updated CandidateView with manifesto
    public record CandidateViewFull(Long id, String name, String description, String manifesto, String photoUrl) {}
}
