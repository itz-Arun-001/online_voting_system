package com.example.voting.controller;

import com.example.voting.repository.CandidatePhotoRepository;
import com.example.voting.repository.ElectionCoverRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves candidate photos and election cover photos. Public so plain <img> tags work. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PhotoController {
    private final CandidatePhotoRepository photos;
    private final ElectionCoverRepository covers;

    @GetMapping("/candidates/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return photos.findById(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/elections/{id}/cover")
    public ResponseEntity<byte[]> cover(@PathVariable Long id) {
        return covers.findById(id)
                .map(c -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(c.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(c.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
