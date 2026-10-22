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
            Boolean restrictToEligibleList) {}

    public record ResultSettingsRequest(@NotNull ResultMode mode, Instant resultsPublishAt) {}

    public record CandidateRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Size(max = 500) String photoUrl) {}

    public record VoteRequest(@NotNull Long candidateId) {}

    public record ElectionView(Long id, String title, String description, Instant startTime, Instant endTime,
                               ElectionStatus status, ResultMode resultMode, Instant resultsPublishAt,
                               boolean resultsPublished, boolean resultsVisible, boolean restrictToEligibleList,
                               Boolean hasVoted) {}

    public record CandidateView(Long id, String name, String description, String photoUrl) {}
    public record CandidateResult(Long candidateId, String name, long votes) {}
    public record ResultsResponse(Long electionId, String title, ElectionStatus status, long totalVotes,
                                  List<CandidateResult> candidates) {}
    public record Turnout(long eligible, long voted, long pending, double turnoutPercent) {}
    public record MyStatus(boolean hasVoted, Instant votedAt) {}
    public record UploadSummary(int added, int duplicates, int invalid, List<String> invalidRows) {}
}
