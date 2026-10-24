package com.example.voting.repository;

import com.example.voting.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    /** Published announcements visible to voters: global OR for this election. */
    @Query("SELECT a FROM Announcement a WHERE a.publishedAt IS NOT NULL AND (a.electionId IS NULL OR a.electionId = :electionId) ORDER BY a.publishedAt DESC")
    List<Announcement> findVisibleForElection(@Param("electionId") Long electionId);

    @Query("SELECT a FROM Announcement a WHERE a.publishedAt IS NOT NULL AND a.electionId IS NULL ORDER BY a.publishedAt DESC")
    List<Announcement> findGlobalVisible();

    List<Announcement> findAllByOrderByCreatedAtDesc();

    /** Find scheduled (not yet published) announcements that are due. */
    @Query("SELECT a FROM Announcement a WHERE a.publishedAt IS NULL AND a.scheduledAt IS NOT NULL AND a.scheduledAt <= :now")
    List<Announcement> findDueAnnouncements(@Param("now") Instant now);

    void deleteByElectionId(Long electionId);
}
