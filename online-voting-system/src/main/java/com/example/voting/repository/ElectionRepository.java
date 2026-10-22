package com.example.voting.repository;

import com.example.voting.entity.Election;
import com.example.voting.enums.ElectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ElectionRepository extends JpaRepository<Election, Long> {
    List<Election> findAllByOrderByStartTimeDesc();
    List<Election> findByStatusNot(ElectionStatus status);
    boolean existsByRestrictToEligibleListFalseAndStatusNot(ElectionStatus status);
}
