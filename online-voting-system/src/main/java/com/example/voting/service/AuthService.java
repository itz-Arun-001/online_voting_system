package com.example.voting.service;

import com.example.voting.config.AppProperties;
import com.example.voting.dto.Dtos.AuthResponse;
import com.example.voting.dto.Dtos.MessageResponse;
import com.example.voting.entity.User;
import com.example.voting.enums.Role;
import com.example.voting.exception.ApiException;
import com.example.voting.repository.UserRepository;
import com.example.voting.security.JwtUtil;
import com.example.voting.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppProperties props;
    private final OtpService otpService;
    private final EmailService emailService;
    private final RateLimitService rateLimit;
    private final ElectionService electionService;
    private final UserRepository users;
    private final JwtUtil jwtUtil;

    public MessageResponse requestOtp(String rawEmail, String ip) {
        String email = EmailUtil.normalize(rawEmail);
        if (!EmailUtil.isValid(email)) throw ApiException.badRequest("Enter a valid email address.");
        if (!EmailUtil.hasDomain(email, props.domain())) {
            throw ApiException.badRequest("Use your college email address (ending in @" + props.domain() + ").");
        }
        var rl = props.getRateLimit();
        Duration window = Duration.ofMinutes(rl.getWindowMinutes());
        rateLimit.check("otp:email:" + email, rl.getOtpRequests(), window);
        rateLimit.check("otp:ip:" + ip, rl.getOtpIpRequests(), window);

        // Same response whether or not the address is eligible, so the endpoint reveals nothing.
        if (props.isAdminEmail(email) || electionService.hasAnyAccess(email)) {
            String code = otpService.issue(email);
            emailService.sendOtp(email, code);
        }
        return new MessageResponse("If the email is eligible, a code has been sent.");
    }

    public AuthResponse verifyOtp(String rawEmail, String otp, String ip) {
        String email = EmailUtil.normalize(rawEmail);
        if (!EmailUtil.isValid(email)) throw ApiException.badRequest("Enter a valid email address.");
        var rl = props.getRateLimit();
        rateLimit.check("verify:ip:" + ip, rl.getVerifyIpAttempts(), Duration.ofMinutes(rl.getWindowMinutes()));
        otpService.verify(email, otp == null ? "" : otp.trim());
        User user = findOrCreateUser(email);
        return new AuthResponse(jwtUtil.generate(user), user.getEmail(), user.getRole().name(), jwtUtil.expirySeconds());
    }

    public User findOrCreateUser(String normalizedEmail) {
        Role wanted = props.isAdminEmail(normalizedEmail) ? Role.ADMIN : Role.VOTER;
        User u = users.findByEmail(normalizedEmail).orElseGet(() -> {
            User n = new User();
            n.setEmail(normalizedEmail);
            n.setRole(wanted);
            return n;
        });
        if (u.getId() == null || (wanted == Role.ADMIN && u.getRole() != Role.ADMIN)) {
            u.setRole(wanted == Role.ADMIN ? Role.ADMIN : u.getRole());
            u = users.save(u);
        }
        return u;
    }
}
