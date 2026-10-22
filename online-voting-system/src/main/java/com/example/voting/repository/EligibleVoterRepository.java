package com.example.voting.repository;

import com.example.voting.entity.EligibleVoter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EligibleVoterRepository extends JpaRepository<EligibleVoter, Long> {
    boolean existsByElectionIdAndEmail(Long electionId, String email);
    boolean existsByEmail(String email);
    long countByElectionId(Long electionId);
    Page<EligibleVoter> findByElectionIdAndEmailContainingIgnoreCaseOrderByEmailAsc(Long electionId, String q, Pageable pageable);

    @Transactional
    long deleteByElectionIdAndEmail(Long electionId, String email);

    @Transactional
    void deleteByElectionId(Long electionId);

    @Query(value = "select ev from EligibleVoter ev where ev.electionId = :eid "
            + "and lower(ev.email) like lower(concat('%', :q, '%')) "
            + "and ev.email not in (select u.email from User u where u.id in "
            + "(select v.userId from Vote v where v.electionId = :eid)) order by ev.email",
            countQuery = "select count(ev) from EligibleVoter ev where ev.electionId = :eid "
            + "and lower(ev.email) like lower(concat('%', :q, '%')) "
            + "and ev.email not in (select u.email from User u where u.id in "
            + "(select v.userId from Vote v where v.electionId = :eid))")
    Page<EligibleVoter> findNotVoted(@Param("eid") Long electionId, @Param("q") String q, Pageable pageable);
}
