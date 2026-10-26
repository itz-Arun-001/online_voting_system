package com.example.voting.repository;

import com.example.voting.entity.ElectionCover;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionCoverRepository extends JpaRepository<ElectionCover, Long> {
}

