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

        boolean isFullAdmin = props.isAdminEmail(email);
        User existingUser = users.findByEmail(email).orElse(null);
        boolean isCoAdmin = existingUser != null && existingUser.getRole() == Role.CO_ADMIN;
        boolean isAdminOrCoAdmin = isFullAdmin || isCoAdmin;

        // Verify the email's domain actually has active mail servers
        String domain = EmailUtil.getDomain(email);
        if (!isAdminOrCoAdmin && !EmailUtil.hasValidMailServer(domain) && !props.hasCollegeDomain(email)) {
            throw ApiException.badRequest("The domain '@" + domain + "' does not exist or has no active mail server.");
        }

        // Accept admins, co-admins (from any domain), or emails matching the college or per-election allowed domains
        boolean domainAllowed = isAdminOrCoAdmin || electionService.isEmailDomainAllowedAnywhere(email, props);
        if (!domainAllowed) {
            String hint = electionService.allKnownDomainsHint(props);
            throw ApiException.badRequest("Use your college email address (ending in " + hint + ").");
        }

        // Verify voter registration / eligibility for an active election (admins and co-admins always have access)
        boolean hasAccess = isAdminOrCoAdmin || electionService.hasAnyAccessWithDomains(email);
        if (!hasAccess) {
            throw ApiException.badRequest("This email is not registered for any election. "
                    + "If your election requires a voter list, please ensure your email was added by the administrator.");
        }

        var rl = props.getRateLimit();
        Duration window = Duration.ofMinutes(rl.getWindowMinutes());
        rateLimit.check("otp:email:" + email, rl.getOtpRequests(), window);
        rateLimit.check("otp:ip:" + ip, rl.getOtpIpRequests(), window);

        // Issue code and send email
        String code = otpService.issue(email);
        emailService.sendOtp(email, code);

        return new MessageResponse("A sign-in code has been sent to your email.");
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
