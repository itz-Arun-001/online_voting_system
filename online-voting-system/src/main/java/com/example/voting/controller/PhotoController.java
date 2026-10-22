package com.example.voting.controller;

import com.example.voting.repository.CandidatePhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Serves candidate photos. Public so plain <img> tags work; the URL carries a version so browsers cache safely. */
@RestController
@RequestMapping("/api/public/candidates")
@RequiredArgsConstructor
public class PhotoController {
    private final CandidatePhotoRepository photos;

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        return photos.findById(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(p.getData()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
