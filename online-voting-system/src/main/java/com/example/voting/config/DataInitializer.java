package com.example.voting.config;

import com.example.voting.entity.User;
import com.example.voting.enums.Role;
import com.example.voting.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.sql.ResultSet;

/** Seeds admin accounts from app.admin-emails. There is no admin signup. */
@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Order(1)
    ApplicationRunner migrateSchema(DataSource dataSource) {
        return args -> {
            try (var conn = dataSource.getConnection(); var stmt = conn.createStatement()) {
                // Alter H2/Postgres/MySQL role column to VARCHAR(20) so CO_ADMIN is accepted
                try {
                    stmt.execute("ALTER TABLE users ALTER COLUMN role VARCHAR(20)");
                    log.info("Migrated users.role column to VARCHAR(20)");
                } catch (Exception e1) {
                    try {
                        stmt.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(20)");
                        log.info("Modified users.role column to VARCHAR(20)");
                    } catch (Exception e2) {
                        log.debug("Role column alteration not needed or failed: {}", e2.getMessage());
                    }
                }

                // If H2 created a CHECK constraint on role in users table, find and drop it
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                        "WHERE TABLE_NAME = 'USERS' AND CONSTRAINT_TYPE = 'CHECK'")) {
                    while (rs.next()) {
                        String cName = rs.getString(1);
                        try (var dropStmt = conn.createStatement()) {
                            dropStmt.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS " + cName);
                            log.info("Dropped check constraint on users: {}", cName);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            } catch (Exception ex) {
                log.warn("Schema migration notice: {}", ex.getMessage());
            }
        };
    }

    @Bean
    @Order(2)
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
