package com.example.voting.service;

import com.example.voting.config.AppProperties;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

    /** One clear line at startup so it is obvious whether real emails will go out. */
    @EventListener(ApplicationReadyEvent.class)
    public void logMailStatus() {
        JavaMailSender sender = activeSender();
        if (sender == null) {
            log.warn("Email is NOT configured: sign-in codes are only printed in this console (dev). Set SMTP_HOST, SMTP_USER and SMTP_PASSWORD in .env to send real emails.");
        } else {
            String host = sender instanceof JavaMailSenderImpl impl ? impl.getHost() : "the configured SMTP server";
            log.info("Email is configured: sign-in codes will be emailed through {} as {}.", host, props.getMailFrom());
        }
    }

    @Async
    public void sendOtp(String to, String code) {
        JavaMailSender sender = activeSender();
        if (sender == null) {
            if (env.acceptsProfiles(Profiles.of("dev"))) {
                log.warn("SMTP not configured (dev). OTP for {} is {}", to, code);
            } else {
                log.error("SMTP is not configured: cannot send the sign-in code to {}. Set SMTP_HOST, SMTP_USER and SMTP_PASSWORD.", to);
            }
            return;
        }
        try {
            int minutes = props.getOtp().getExpiryMinutes();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.getMailFrom());
            helper.setTo(to);
            helper.setSubject("Your voting code: " + code);
            String text = "Your sign-in code is " + code + ".\n\nIt expires in " + minutes
                    + " minutes. If you did not ask for it, ignore this email.";
            String html = "<p>Your sign-in code is</p><p style=\"font-size:28px;letter-spacing:4px;font-family:monospace\"><b>"
                    + code + "</b></p><p>It expires in " + minutes
                    + " minutes. If you did not ask for it, you can ignore this email.</p>";
            helper.setText(text, html);
            sender.send(message);
        } catch (Exception ex) {
            log.error("Could not send the sign-in code to {}: {}", to, ex.getMessage());
        }
    }
}
