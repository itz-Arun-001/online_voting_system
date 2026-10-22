package com.example.voting.service;

import com.example.voting.dto.Dtos.CandidateResult;
import com.example.voting.dto.Dtos.ResultsResponse;
import com.example.voting.entity.Election;
import com.example.voting.repository.CandidateRepository;
import com.example.voting.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ResultService {
    private final VoteRepository votes;
    private final CandidateRepository candidates;
    private final ElectionService electionService;

    /** Raw counts. Callers decide whether the person asking is allowed to see them. */
    public ResultsResponse compute(Election e) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : votes.countByCandidate(e.getId())) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<CandidateResult> list = candidates.findByElectionIdOrderByIdAsc(e.getId()).stream()
                .map(c -> new CandidateResult(c.getId(), c.getName(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
        long total = list.stream().mapToLong(CandidateResult::votes).sum();
        return new ResultsResponse(e.getId(), e.getTitle(), electionService.currentStatus(e), total, list);
    }
}
