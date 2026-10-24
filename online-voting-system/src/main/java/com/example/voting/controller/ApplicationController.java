package com.example.voting.controller;

import com.example.voting.dto.Dtos.*;
import com.example.voting.entity.CandidateApplication;
import com.example.voting.security.AuthUser;
import com.example.voting.service.ApplicationService;
import com.example.voting.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService svc;
    private final AuditService audit;

    /** Voter submits a candidacy application (JSON) */
    @PostMapping(value = "/api/elections/{id}/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationView applyJson(@PathVariable Long id, @Valid @RequestBody ApplicationRequest r,
                                     @AuthenticationPrincipal AuthUser me) {
        return svc.apply(id, r.name(), r.manifesto(), null, me);
    }

    /** Voter submits a candidacy application with optional photo (multipart) */
    @PostMapping(value = "/api/elections/{id}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApplicationView applyMultipart(@PathVariable Long id,
                                          @RequestParam("name") String name,
                                          @RequestParam(value = "manifesto", required = false) String manifesto,
                                          @RequestParam(value = "photo", required = false) MultipartFile photo,
                                          @AuthenticationPrincipal AuthUser me) {
        return svc.apply(id, name, manifesto, photo, me);
    }

    /** Public endpoint to fetch an application's photo thumbnail */
    @GetMapping("/api/public/applications/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        CandidateApplication app = svc.get(id);
        if (app.getPhotoData() == null || app.getPhotoContentType() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(app.getPhotoContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(app.getPhotoData());
    }

    /** Admin views all applications for an election */
    @GetMapping("/api/admin/elections/{id}/applications")
    public List<ApplicationView> list(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        return svc.listForElection(id, me);
    }

    /** Admin approves or rejects */
    @PutMapping("/api/admin/applications/{id}/approve")
    public ApplicationView approve(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        ApplicationView v = svc.review(id, "APPROVED", me);
        audit.log(me, "APPROVE_APPLICATION", "id=" + id);
        return v;
    }

    @PutMapping("/api/admin/applications/{id}/reject")
    public ApplicationView reject(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        ApplicationView v = svc.review(id, "REJECTED", me);
        audit.log(me, "REJECT_APPLICATION", "id=" + id);
        return v;
    }
}
