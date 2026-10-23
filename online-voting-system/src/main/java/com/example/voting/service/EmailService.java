package com.example.voting.service;

import com.example.voting.config.AppProperties;
import com.example.voting.exception.ApiException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;
    private final Environment env;

    /** Returns the mail sender only if an SMTP host is really set (an empty SMTP_HOST means "print codes in the console"). */
    private JavaMailSender activeSender() {
        JavaMailSender s = mailSender.getIfAvailable();
        if (s instanceof JavaMailSenderImpl impl && (impl.getHost() == null || impl.getHost().isBlank())) {
            return null;
        }
        return s;
    }

    private String resolveFrom(JavaMailSender sender) {
        String from = props.getMailFrom();
        if (from != null && !from.isBlank() && !from.equals("no-reply@voting.local")) {
            return from.trim();
        }
        if (sender instanceof JavaMailSenderImpl impl && impl.getUsername() != null && !impl.getUsername().isBlank()) {
            return impl.getUsername().trim();
        }
        return from != null && !from.isBlank() ? from.trim() : "no-reply@voting.local";
    }

    /** One clear line at startup so it is obvious whether real emails will go out. */
    @EventListener(ApplicationReadyEvent.class)
    public void logMailStatus() {
        if (props.getBrevoApiKey() != null && !props.getBrevoApiKey().isBlank()) {
            log.info("Email is configured via Brevo HTTPS API (Port 443, cloud firewall friendly).");
            return;
        }
        if (props.getResendApiKey() != null && !props.getResendApiKey().isBlank()) {
            log.info("Email is configured via Resend HTTPS API (Port 443, cloud firewall friendly).");
            return;
        }
        JavaMailSender sender = activeSender();
        if (sender == null) {
            log.warn("Email is NOT configured: sign-in codes are only printed in this console (dev). Set SMTP_HOST, SMTP_USER and SMTP_PASSWORD in .env to send real emails.");
        } else {
            String host = sender instanceof JavaMailSenderImpl impl ? impl.getHost() : "the configured SMTP server";
            log.info("Email is configured: sign-in codes will be emailed through {} as {}.", host, resolveFrom(sender));
        }
    }

    public void sendOtp(String to, String code) {
        int minutes = props.getOtp().getExpiryMinutes();

        // 1. Check Brevo HTTP API (Port 443 - works on Render, Railway, AWS without blocked ports)
        if (props.getBrevoApiKey() != null && !props.getBrevoApiKey().isBlank()) {
            try {
                sendViaBrevo(props.getBrevoApiKey().trim(), to, code, minutes);
                return;
            } catch (Exception ex) {
                log.error("Could not send code via Brevo API to {}: {}", to, ex.getMessage());
                throw ApiException.badRequest("Failed to deliver email via Brevo to " + to + ". Check API key or sender settings.");
            }
        }

        // 2. Check Resend HTTP API (Port 443)
        if (props.getResendApiKey() != null && !props.getResendApiKey().isBlank()) {
            try {
                sendViaResend(props.getResendApiKey().trim(), to, code, minutes);
                return;
            } catch (Exception ex) {
                log.error("Could not send code via Resend API to {}: {}", to, ex.getMessage());
                throw ApiException.badRequest("Failed to deliver email via Resend to " + to + ". Check API key or sender settings.");
            }
        }

        // 3. Fallback to standard SMTP
        JavaMailSender sender = activeSender();
        if (sender == null) {
            if (env.acceptsProfiles(Profiles.of("dev"))) {
                log.warn("SMTP not configured (dev). OTP for {} is {}", to, code);
            } else {
                log.error("SMTP is not configured: cannot send the sign-in code to {}. Set BREVO_API_KEY, RESEND_API_KEY or SMTP_HOST/SMTP_USER/SMTP_PASSWORD.", to);
                throw ApiException.badRequest("Email service is not configured on the server.");
            }
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(resolveFrom(sender));
            helper.setTo(to);
            helper.setSubject("Your voting code: " + code);
            String text = "Your sign-in code is " + code + ".\n\nIt expires in " + minutes
                    + " minutes. If you did not ask for it, ignore this email.";
            String html = "<p>Your sign-in code is</p><p style=\"font-size:28px;letter-spacing:4px;font-family:monospace\"><b>"
                    + code + "</b></p><p>It expires in " + minutes
                    + " minutes. If you did not ask for it, you can ignore this email.</p>";
            helper.setText(text, html);
            sender.send(message);
            log.info("Sent real voting OTP email to {}", to);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Could not send the sign-in code to {}: {}", to, ex.getMessage());
            String errorMsg = ex.getMessage() != null ? ex.getMessage() : "";
            if (errorMsg.contains("550") || errorMsg.contains("5.1.1") || errorMsg.toLowerCase().contains("does not exist")
                    || errorMsg.toLowerCase().contains("invalid addresses") || errorMsg.toLowerCase().contains("recipient address rejected")) {
                throw ApiException.badRequest("The email address does not exist or was rejected by the mail server.");
            }
            throw ApiException.badRequest("Failed to deliver email to " + to + ". Please ensure the address exists and is correct.");
        }
    }

    private void sendViaBrevo(String apiKey, String to, String code, int minutes) throws Exception {
        JavaMailSender sender = mailSender.getIfAvailable();
        String from = resolveFrom(sender);
        if (from.equals("no-reply@voting.local")) {
            from = "arun0012007@gmail.com";
        }

        String json = """
            {
              "sender": {"email": "%s", "name": "College Election"},
              "to": [{"email": "%s"}],
              "subject": "Your voting code: %s",
              "htmlContent": "<p>Your sign-in code is</p><p style=\\"font-size:28px;letter-spacing:4px;font-family:monospace\\"><b>%s</b></p><p>It expires in %d minutes. If you did not ask for it, you can ignore this email.</p>"
            }
            """.formatted(from, to, code, code, minutes);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.error("Brevo API returned error status {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Brevo API error " + response.statusCode() + ": " + response.body());
        }
        log.info("Sent real voting OTP email to {} via Brevo HTTPS API", to);
    }

    private void sendViaResend(String apiKey, String to, String code, int minutes) throws Exception {
        JavaMailSender sender = mailSender.getIfAvailable();
        String from = resolveFrom(sender);
        String senderEmail = from.contains("@") && !from.endsWith("@voting.local") ? from : "onboarding@resend.dev";

        String json = """
            {
              "from": "%s",
              "to": ["%s"],
              "subject": "Your voting code: %s",
              "html": "<p>Your sign-in code is</p><p style=\\"font-size:28px;letter-spacing:4px;font-family:monospace\\"><b>%s</b></p><p>It expires in %d minutes.</p>"
            }
            """.formatted(senderEmail, to, code, code, minutes);

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            log.error("Resend API returned error status {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Resend API error " + response.statusCode() + ": " + response.body());
        }
        log.info("Sent real voting OTP email to {} via Resend HTTPS API", to);
    }
}
