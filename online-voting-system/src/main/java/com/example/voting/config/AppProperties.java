package com.example.voting.config;

import com.example.voting.util.EmailUtil;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
@Getter @Setter
public class AppProperties {
    private String collegeDomain = "yourcollege.edu";
    private List<String> adminEmails = new ArrayList<>();
    private String corsOrigins = "";
    private String displayZone = "Asia/Kolkata";
    private String mailFrom = "no-reply@voting.local";
    private Otp otp = new Otp();
    private Jwt jwt = new Jwt();
    private RateLimit rateLimit = new RateLimit();

    @Getter @Setter
    public static class Otp {
        private int expiryMinutes = 5;
        private int maxAttempts = 5;
        private int length = 6;
    }

    @Getter @Setter
    public static class Jwt {
        private String secret = "";
        private int expiryMinutes = 60;
    }

    @Getter @Setter
    public static class RateLimit {
        private int otpRequests = 3;
        private int otpIpRequests = 60;
        private int verifyIpAttempts = 120;
        private int windowMinutes = 10;
    }

    /** College domain, lowercased and without a leading @. */
    public String domain() {
        String d = EmailUtil.normalize(collegeDomain);
        return d != null && d.startsWith("@") ? d.substring(1) : d;
    }

    public List<String> normalizedAdminEmails() {
        return adminEmails.stream().map(EmailUtil::normalize).filter(s -> s != null && !s.isEmpty()).toList();
    }

    public boolean isAdminEmail(String normalizedEmail) {
        return normalizedAdminEmails().contains(normalizedEmail);
    }
}
