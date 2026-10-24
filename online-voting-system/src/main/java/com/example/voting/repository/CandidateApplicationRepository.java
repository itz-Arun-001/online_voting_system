package com.example.voting.repository;

import com.example.voting.entity.CandidateApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CandidateApplicationRepository extends JpaRepository<CandidateApplication, Long> {
    List<CandidateApplication> findByElectionIdOrderByAppliedAtAsc(Long electionId);
    boolean existsByElectionIdAndApplicantEmail(Long electionId, String applicantEmail);
    void deleteByElectionId(Long electionId);
}
