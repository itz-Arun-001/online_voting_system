package com.example.voting.service;

import com.example.voting.dto.Dtos.*;
import com.example.voting.entity.Candidate;
import com.example.voting.entity.CandidateApplication;
import com.example.voting.entity.CandidatePhoto;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.CandidateApplicationRepository;
import com.example.voting.repository.CandidatePhotoRepository;
import com.example.voting.repository.CandidateRepository;
import com.example.voting.repository.VoteRepository;
import com.example.voting.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final CandidateApplicationRepository repo;
    private final ElectionService elections;
    private final CandidateRepository candidates;
    private final CandidatePhotoRepository candidatePhotos;
    private final VoteRepository votes;

    public List<ApplicationView> listForElection(Long electionId, AuthUser user) {
        elections.requireElectionAccess(electionId, user);
        return repo.findByElectionIdOrderByAppliedAtAsc(electionId).stream().map(this::toView).toList();
    }

    public CandidateApplication get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Application not found."));
    }

    @Transactional
    public ApplicationView apply(Long electionId, String name, String manifesto, MultipartFile photo, AuthUser voter) {
        elections.get(electionId); // validate election exists
        if (name == null || name.isBlank()) throw ApiException.badRequest("Name is required.");
        if (repo.existsByElectionIdAndApplicantEmail(electionId, voter.email())) {
            throw ApiException.conflict("You have already applied for this election.");
        }
        CandidateApplication app = new CandidateApplication();
        app.setElectionId(electionId);
        app.setName(name.trim());
        app.setManifesto(manifesto != null && !manifesto.isBlank() ? manifesto.trim() : null);
        app.setApplicantEmail(voter.email());

        if (photo != null && !photo.isEmpty()) {
            if (photo.getSize() > 2 * 1024 * 1024) {
                throw ApiException.badRequest("The photo must be 2 MB or smaller.");
            }
            try {
                byte[] bytes = photo.getBytes();
                String type = ElectionService.imageType(bytes);
                if (type == null) {
                    throw ApiException.badRequest("Use a JPG, PNG, WebP or GIF image.");
                }
                app.setPhotoContentType(type);
                app.setPhotoData(bytes);
            } catch (IOException ex) {
                throw ApiException.badRequest("Could not read uploaded photo.");
            }
        }

        return toView(repo.save(app));
    }

    @Transactional
    public ApplicationView review(Long appId, String decision, AuthUser admin) {
        CandidateApplication app = repo.findById(appId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        elections.requireElectionAccess(app.getElectionId(), admin);
        if (!"PENDING".equals(app.getStatus())) throw ApiException.conflict("This application has already been reviewed.");
        String status = decision.toUpperCase();
        app.setStatus(status);
        app.setReviewedAt(Instant.now());
        app.setReviewedBy(admin.id());
        CandidateApplication saved = repo.save(app);
        if ("APPROVED".equals(status)) {
            if (votes.countByElectionId(saved.getElectionId()) == 0) {
                Candidate c = new Candidate(saved.getElectionId(), saved.getName().trim(), null, null);
                c.setManifesto(saved.getManifesto());
                Candidate savedCand = candidates.save(c);
                if (saved.getPhotoData() != null && saved.getPhotoContentType() != null) {
                    CandidatePhoto p = new CandidatePhoto();
                    p.setCandidateId(savedCand.getId());
                    p.setContentType(saved.getPhotoContentType());
                    p.setData(saved.getPhotoData());
                    candidatePhotos.save(p);
                    savedCand.setPhotoUrl("/api/public/candidates/" + savedCand.getId() + "/photo?v=" + System.currentTimeMillis());
                    candidates.save(savedCand);
                }
            }
        }
        return toView(saved);
    }

    private ApplicationView toView(CandidateApplication a) {
        String photoUrl = a.getPhotoData() != null ? "/api/public/applications/" + a.getId() + "/photo" : null;
        return new ApplicationView(a.getId(), a.getElectionId(), a.getName(), a.getManifesto(),
                a.getApplicantEmail(), a.getStatus(), a.getAppliedAt(), photoUrl);
    }
}
