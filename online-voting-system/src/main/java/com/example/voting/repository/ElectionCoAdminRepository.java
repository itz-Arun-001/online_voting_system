package com.example.voting.repository;

import com.example.voting.entity.ElectionCoAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ElectionCoAdminRepository extends JpaRepository<ElectionCoAdmin, Long> {
    List<ElectionCoAdmin> findByElectionId(Long electionId);
    List<ElectionCoAdmin> findByUserId(Long userId);
    boolean existsByElectionIdAndUserId(Long electionId, Long userId);

    @Transactional
    void deleteByElectionIdAndUserId(Long electionId, Long userId);

    @Transactional
    void deleteByElectionId(Long electionId);

    @Transactional
    void deleteByUserId(Long userId);
}

