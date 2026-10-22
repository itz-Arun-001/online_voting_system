package com.example.voting.repository;

import com.example.voting.entity.CandidatePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CandidatePhotoRepository extends JpaRepository<CandidatePhoto, Long> {
    @Transactional
    @Modifying
    @Query("delete from CandidatePhoto p where p.candidateId in (select c.id from Candidate c where c.electionId = :electionId)")
    void deleteByElectionId(@Param("electionId") Long electionId);
}
