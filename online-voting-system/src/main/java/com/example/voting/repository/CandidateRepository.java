package com.example.voting.repository;

import com.example.voting.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findByElectionIdOrderByIdAsc(Long electionId);
    Optional<Candidate> findByIdAndElectionId(Long id, Long electionId);

    @Transactional
    void deleteByElectionId(Long electionId);
}
