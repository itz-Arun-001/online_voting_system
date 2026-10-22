package com.example.voting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "eligible_voters",
        uniqueConstraints = @UniqueConstraint(name = "uk_eligible_election_email", columnNames = {"election_id", "email"}))
@Getter @Setter @NoArgsConstructor
public class EligibleVoter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(length = 150)
    private String name;

    @Column(name = "roll_number", length = 50)
    private String rollNumber;

    public EligibleVoter(Long electionId, String email, String name, String rollNumber) {
        this.electionId = electionId;
        this.email = email;
        this.name = name;
        this.rollNumber = rollNumber;
    }
}
