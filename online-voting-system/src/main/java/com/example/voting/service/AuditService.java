package com.example.voting.service;

import com.example.voting.entity.AuditLog;
import com.example.voting.repository.AuditLogRepository;
import com.example.voting.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;

    public void log(AuthUser admin, String action, String details) {
        repo.save(new AuditLog(admin.email(), action, details));
    }
}
