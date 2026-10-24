package com.example.voting.controller;

import com.example.voting.dto.Dtos.*;
import com.example.voting.security.AuthUser;
import com.example.voting.service.AnnouncementService;
import com.example.voting.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService svc;
    private final AuditService audit;

    // Public endpoints
    @GetMapping("/api/public/announcements")
    public List<AnnouncementView> globalAnnouncements() {
        return svc.globalVisible();
    }

    @GetMapping("/api/public/elections/{id}/announcements")
    public List<AnnouncementView> electionAnnouncements(@PathVariable Long id) {
        return svc.visibleForElection(id);
    }

    // Admin endpoints
    @GetMapping("/api/admin/announcements")
    public List<AnnouncementView> listAll(@AuthenticationPrincipal AuthUser me) {
        return svc.listForAdmin(me);
    }

    @PostMapping("/api/admin/announcements")
    public AnnouncementView create(@Valid @RequestBody AnnouncementRequest r, @AuthenticationPrincipal AuthUser me) {
        AnnouncementView v = svc.create(r, me);
        audit.log(me, "CREATE_ANNOUNCEMENT", "id=" + v.id() + (v.electionId() != null ? " election=" + v.electionId() : " global"));
        return v;
    }

    @PutMapping("/api/admin/announcements/{id}/publish")
    public AnnouncementView publish(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        AnnouncementView v = svc.publish(id, me);
        audit.log(me, "PUBLISH_ANNOUNCEMENT", "id=" + id);
        return v;
    }

    @DeleteMapping("/api/admin/announcements/{id}")
    public MessageResponse delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        svc.delete(id, me);
        audit.log(me, "DELETE_ANNOUNCEMENT", "id=" + id);
        return new MessageResponse("Announcement deleted.");
    }
}
