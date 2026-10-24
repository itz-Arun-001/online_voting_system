package com.example.voting.controller;

import com.example.voting.dto.Dtos.*;
import com.example.voting.entity.Election;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.VoteRepository;
import com.example.voting.security.AuthUser;
import com.example.voting.service.ElectionService;
import com.example.voting.service.SseService;
import com.example.voting.service.VoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/elections")
@RequiredArgsConstructor
public class ElectionController {
    private final ElectionService electionService;
    private final VoteService voteService;
    private final VoteRepository votes;
    private final SseService sse;

    @GetMapping
    public List<ElectionView> list(@AuthenticationPrincipal AuthUser me) {
        return electionService.listFor(me);
    }

    @GetMapping("/{id}")
    public ElectionView get(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        Election e = visibleTo(id, me);
        return electionService.toView(e, votes.existsByElectionIdAndUserId(id, me.id()));
    }

    @GetMapping("/{id}/candidates")
    public List<CandidateView> candidates(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        visibleTo(id, me);
        return electionService.candidatesOf(id);
    }

    @GetMapping("/{id}/candidates/full")
    public List<CandidateView> candidatesFull(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        visibleTo(id, me);
        return electionService.candidatesOf(id);
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<MessageResponse> vote(@PathVariable Long id, @Valid @RequestBody VoteRequest r,
                                                @AuthenticationPrincipal AuthUser me) {
        voteService.castVote(id, me, r.candidateId());
        sse.broadcast(electionService.get(id));
        return ResponseEntity.status(201).body(new MessageResponse("Your vote has been recorded."));
    }

    /** Whether this user has voted, and when. Never the candidate. */
    @GetMapping("/{id}/my-status")
    public MyStatus myStatus(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        visibleTo(id, me);
        return votes.findByElectionIdAndUserId(id, me.id())
                .map(v -> new MyStatus(true, v.getVotedAt(), v.getReceiptToken()))
                .orElse(new MyStatus(false, null, null));
    }

    private Election visibleTo(Long id, AuthUser me) {
        Election e = electionService.get(id);
        if (!me.isAdmin() && !electionService.isEligible(e, me.email())) {
            throw ApiException.forbidden("You are not on the voter list for this election.");
        }
        return e;
    }
}
