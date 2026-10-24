package com.example.voting.service;

import com.example.voting.entity.Candidate;
import com.example.voting.entity.Election;
import com.example.voting.entity.Vote;
import com.example.voting.enums.ElectionStatus;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.CandidateRepository;
import com.example.voting.repository.VoteRepository;
import com.example.voting.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoteService {
    private final ElectionService electionService;
    private final CandidateRepository candidates;
    private final VoteRepository votes;

    @Transactional
    public void castVote(Long electionId, AuthUser me, Long candidateId) {
        Election e = electionService.get(electionId);

        ElectionStatus status = electionService.currentStatus(e);
        if (status == ElectionStatus.UPCOMING) throw ApiException.badRequest("Voting has not started yet.");
        if (status == ElectionStatus.CLOSED) throw ApiException.badRequest("Voting is closed for this election.");

        if (!electionService.isEligible(e, me.email())) {
            throw ApiException.forbidden("You are not on the voter list for this election.");
        }
        if (votes.existsByElectionIdAndUserId(electionId, me.id())) {
            throw ApiException.conflict("You have already voted in this election.");
        }
        Candidate c = candidates.findByIdAndElectionId(candidateId, electionId)
                .orElseThrow(() -> ApiException.badRequest("Choose a candidate from this election."));
        try {
            Vote vote = new Vote(electionId, me.id(), c.getId());
            vote.setReceiptToken("RCPT-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
            votes.saveAndFlush(vote);
        } catch (DataIntegrityViolationException ex) {
            // Two requests raced: the unique (election_id, user_id) constraint rejected the second.
            throw ApiException.conflict("You have already voted in this election.");
        }
    }
}
