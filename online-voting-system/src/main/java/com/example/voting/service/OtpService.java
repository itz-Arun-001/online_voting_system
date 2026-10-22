package com.example.voting.service;

import com.example.voting.config.AppProperties;
import com.example.voting.entity.OtpToken;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Not @Transactional on purpose: failed-attempt counters must persist even though we throw. */
@Service
@RequiredArgsConstructor
public class OtpService {
    private final OtpTokenRepository repo;
    private final PasswordEncoder encoder;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    /** Creates a new code (older unused codes are invalidated) and returns the plain code for emailing. */
    public String issue(String email) {
        repo.invalidateAll(email);
        int len = Math.min(Math.max(props.getOtp().getLength(), 4), 9);
        int bound = (int) Math.pow(10, len);
        String code = String.format("%0" + len + "d", random.nextInt(bound));
        OtpToken t = new OtpToken();
        t.setEmail(email);
        t.setOtpHash(encoder.encode(code));
        t.setExpiresAt(Instant.now().plus(props.getOtp().getExpiryMinutes(), ChronoUnit.MINUTES));
        repo.save(t);
        return code;
    }

    public void verify(String email, String code) {
        int max = props.getOtp().getMaxAttempts();
        OtpToken t = repo.findFirstByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired code. Request a new one."));
        if (t.getExpiresAt().isBefore(Instant.now())) {
            t.setUsed(true);
            repo.save(t);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "That code has expired. Request a new one.");
        }
        if (t.getAttempts() >= max) {
            t.setUsed(true);
            repo.save(t);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Too many wrong attempts. Request a new code.");
        }
        if (!encoder.matches(code, t.getOtpHash())) {
            t.setAttempts(t.getAttempts() + 1);
            int left = max - t.getAttempts();
            if (left <= 0) t.setUsed(true);
            repo.save(t);
            throw new ApiException(HttpStatus.UNAUTHORIZED, left > 0
                    ? "Incorrect code. " + left + (left == 1 ? " attempt" : " attempts") + " left."
                    : "Too many wrong attempts. Request a new code.");
        }
        t.setUsed(true);
        repo.save(t);
    }
}
