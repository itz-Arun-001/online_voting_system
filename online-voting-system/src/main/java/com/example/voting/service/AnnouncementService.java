package com.example.voting.service;

import com.example.voting.dto.Dtos.*;
import com.example.voting.entity.Announcement;
import com.example.voting.entity.ElectionCoAdmin;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.AnnouncementRepository;
import com.example.voting.repository.ElectionCoAdminRepository;
import com.example.voting.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {
    private final AnnouncementRepository repo;
    private final ElectionService elections;
    private final ElectionCoAdminRepository electionCoAdmins;

    public List<AnnouncementView> listAll() {
        return repo.findAllByOrderByCreatedAtDesc().stream().map(this::toView).toList();
    }

    public List<AnnouncementView> listForAdmin(AuthUser admin) {
        if (admin.isFullAdmin()) return listAll();
        List<Long> assigned = electionCoAdmins.findByUserId(admin.id()).stream()
                .map(ElectionCoAdmin::getElectionId).toList();
        return repo.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> a.getElectionId() != null && assigned.contains(a.getElectionId()))
                .map(this::toView).toList();
    }

    public List<AnnouncementView> visibleForElection(Long electionId) {
        return repo.findVisibleForElection(electionId).stream().map(this::toView).toList();
    }

    public List<AnnouncementView> globalVisible() {
        return repo.findGlobalVisible().stream().map(this::toView).toList();
    }

    @Transactional
    public AnnouncementView create(AnnouncementRequest r, AuthUser admin) {
        if (r.electionId() == null) {
            if (!admin.isFullAdmin()) {
                throw ApiException.forbidden("Only full admins can create global announcements.");
            }
        } else {
            elections.requireElectionAccess(r.electionId(), admin);
        }
        Announcement a = new Announcement();
        a.setMessage(r.message().trim());
        a.setElectionId(r.electionId());
        a.setCreatedBy(admin.id());
        if (r.scheduledAt() != null) {
            a.setScheduledAt(r.scheduledAt());
        } else {
            a.setPublishedAt(Instant.now());
        }
        return toView(repo.save(a));
    }

    @Transactional
    public AnnouncementView publish(Long id, AuthUser admin) {
        Announcement a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Announcement not found."));
        if (a.getElectionId() == null) {
            if (!admin.isFullAdmin()) throw ApiException.forbidden("Only full admins can publish global announcements.");
        } else {
            elections.requireElectionAccess(a.getElectionId(), admin);
        }
        if (a.getPublishedAt() != null) throw ApiException.conflict("Already published.");
        a.setPublishedAt(Instant.now());
        return toView(repo.save(a));
    }

    @Transactional
    public void delete(Long id, AuthUser admin) {
        Announcement a = repo.findById(id).orElseThrow(() -> ApiException.notFound("Announcement not found."));
        if (a.getElectionId() == null) {
            if (!admin.isFullAdmin()) throw ApiException.forbidden("Only full admins can delete global announcements.");
        } else {
            elections.requireElectionAccess(a.getElectionId(), admin);
        }
        repo.deleteById(id);
    }

    /** Called by scheduler to auto-publish due announcements. */
    @Transactional
    public void publishDue() {
        Instant now = Instant.now();
        repo.findDueAnnouncements(now).forEach(a -> {
            a.setPublishedAt(now);
            repo.save(a);
        });
    }

    private AnnouncementView toView(Announcement a) {
        return new AnnouncementView(a.getId(), a.getElectionId(), a.getMessage(), a.getPublishedAt(), a.getScheduledAt());
    }
}
