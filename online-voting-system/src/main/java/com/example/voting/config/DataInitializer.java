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
                // 1. Direct drop of standard Hibernate check constraint names across dialects (Postgres / H2 / MySQL)
                String[] directDrops = {
                    "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check",
                    "ALTER TABLE users DROP CONSTRAINT IF EXISTS \"users_role_check\"",
                    "ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check1",
                    "ALTER TABLE users DROP CONSTRAINT IF EXISTS \"users_role_check1\"",
                    "ALTER TABLE users DROP CHECK IF EXISTS users_role_check",
                    "ALTER TABLE users DROP CHECK users_role_check",
                    "ALTER TABLE elections DROP CONSTRAINT IF EXISTS elections_result_mode_check",
                    "ALTER TABLE elections DROP CONSTRAINT IF EXISTS \"elections_result_mode_check\"",
                    "ALTER TABLE elections DROP CONSTRAINT IF EXISTS elections_result_mode_check1",
                    "ALTER TABLE elections DROP CONSTRAINT IF EXISTS \"elections_result_mode_check1\"",
                    "ALTER TABLE elections DROP CHECK IF EXISTS elections_result_mode_check",
                    "ALTER TABLE elections DROP CHECK elections_result_mode_check"
                };
                for (String sql : directDrops) {
                    try (var s = conn.createStatement()) {
                        s.execute(sql);
                        log.info("Direct schema drop executed: {}", sql);
                    } catch (Exception ignored) {}
                }

                // 2. Query Postgres pg_constraint directly if running on PostgreSQL
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT conname FROM pg_constraint WHERE conrelid = 'users'::regclass AND contype = 'c'")) {
                    while (rs.next()) {
                        String cName = rs.getString(1);
                        try (var dropStmt = conn.createStatement()) {
                            dropStmt.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS \"" + cName + "\" CASCADE");
                            log.info("Dropped Postgres check constraint on users: {}", cName);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}

                // 3. Query INFORMATION_SCHEMA for any check constraint on users table (H2 / Postgres / MySQL)
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                        "WHERE LOWER(TABLE_NAME) = 'users' AND UPPER(CONSTRAINT_TYPE) = 'CHECK'")) {
                    while (rs.next()) {
                        String cName = rs.getString(1);
                        if (cName != null && !cName.isBlank()) {
                            try (var dropStmt = conn.createStatement()) {
                                dropStmt.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS \"" + cName + "\"");
                                log.info("Dropped check constraint on users: {}", cName);
                            } catch (Exception e) {
                                try (var dropStmt2 = conn.createStatement()) {
                                    dropStmt2.execute("ALTER TABLE users DROP CHECK " + cName);
                                    log.info("Dropped MySQL check constraint on users: {}", cName);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                } catch (Exception ignored) {}

                // 4. Alter column type to VARCHAR(20) across Postgres (TYPE syntax), H2, and MySQL (MODIFY syntax)
                try {
                    stmt.execute("ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20)");
                    log.info("Migrated users.role column to VARCHAR(20) (Postgres syntax)");
                } catch (Exception e1) {
                    try {
                        stmt.execute("ALTER TABLE users ALTER COLUMN role VARCHAR(20)");
                        log.info("Migrated users.role column to VARCHAR(20) (H2 syntax)");
                    } catch (Exception e2) {
                        try {
                            stmt.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(20)");
                            log.info("Modified users.role column to VARCHAR(20) (MySQL syntax)");
                        } catch (Exception ignored) {}
                    }
                }
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
