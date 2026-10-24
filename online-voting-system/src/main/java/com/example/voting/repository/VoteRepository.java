package com.example.voting.repository;

import com.example.voting.dto.VoterRow;
import com.example.voting.entity.Vote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {
    boolean existsByElectionIdAndUserId(Long electionId, Long userId);
    Optional<Vote> findByElectionIdAndUserId(Long electionId, Long userId);
    long countByElectionId(Long electionId);
    void deleteByElectionId(Long electionId);

    @Query("select v.candidateId, count(v) from Vote v where v.electionId = :eid group by v.candidateId")
    List<Object[]> countByCandidate(@Param("eid") Long electionId);

    /** Who voted and when. Deliberately never selects candidate_id. */
    @Query(value = "select new com.example.voting.dto.VoterRow(u.email, coalesce(ev.name, u.name), v.votedAt) "
            + "from Vote v join User u on u.id = v.userId "
            + "left join EligibleVoter ev on ev.electionId = v.electionId and ev.email = u.email "
            + "where v.electionId = :eid and lower(u.email) like lower(concat('%', :q, '%')) "
            + "order by v.votedAt desc",
            countQuery = "select count(v) from Vote v join User u on u.id = v.userId "
            + "where v.electionId = :eid and lower(u.email) like lower(concat('%', :q, '%'))")
    Page<VoterRow> findVoted(@Param("eid") Long electionId, @Param("q") String q, Pageable pageable);
}
