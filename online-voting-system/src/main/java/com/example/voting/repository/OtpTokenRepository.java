package com.example.voting.repository;

import com.example.voting.entity.OtpToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(String email);

    @Transactional
    @Modifying
    @Query("update OtpToken t set t.used = true where t.email = :email and t.used = false")
    int invalidateAll(@Param("email") String email);

    @Transactional
    @Modifying
    @Query("delete from OtpToken t where t.used = true or t.expiresAt < :now")
    int deleteStale(@Param("now") Instant now);
}
