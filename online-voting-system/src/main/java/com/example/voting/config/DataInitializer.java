package com.example.voting.config;

import com.example.voting.entity.User;
import com.example.voting.enums.Role;
import com.example.voting.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Seeds admin accounts from app.admin-emails. There is no admin signup. */
@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    ApplicationRunner seedAdmins(AppProperties props, UserRepository users) {
        return args -> {
            for (String email : props.normalizedAdminEmails()) {
                User u = users.findByEmail(email).orElseGet(() -> {
                    User n = new User();
                    n.setEmail(email);
                    return n;
                });
                if (u.getRole() != Role.ADMIN || u.getId() == null) {
                    u.setRole(Role.ADMIN);
                    users.save(u);
                    log.info("Admin account ready: {}", email);
                }
            }
        };
    }
}
