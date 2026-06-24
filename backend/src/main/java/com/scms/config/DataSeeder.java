package com.scms.config;

import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * DataSeeder — inserts DEMO user accounts on startup, for local development
 * and evaluation only.
 *
 * CHANGE in v2.0 (production hardening):
 *
 *   • @Profile("!prod") — v1.3 finding: "Default credentials Admin@1234 /
 *     User@1234 are predictable and hardcoded... If DataSeeder runs in
 *     production, these accounts exist in production with known passwords."
 *     This bean simply does not exist as a Spring component when the
 *     `prod` profile is active (set SPRING_PROFILES_ACTIVE=prod for any
 *     real deployment — see docker-compose.yml and README.md). A real
 *     deployment creates its first admin via the SQL snippet documented in
 *     README.md's "First production admin" section instead.
 *
 *   • Roles are no longer seeded here — they're reference data, seeded by
 *     the Flyway migration V2__seed_reference_data.sql, which runs before
 *     this CommandLineRunner regardless of profile. Reference data belongs
 *     in a migration (versioned, reviewable, runs once) — demo *accounts*
 *     belong here (dev-only, upsert-style, intentionally never run in prod).
 *
 *   • No password values are logged anymore — v1.3's
 *     `log.info("Seeded admin: admin@scms.com / Admin@1234")` is gone.
 *     The demo credentials are documented once, in README.md, where a
 *     developer expects to find setup instructions — not duplicated into
 *     every application log file (which may ship to a log aggregator).
 *
 *   • Added a STAFF demo account (staff@scms.com) to exercise the new
 *     assignment workflow end-to-end out of the box.
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository  roleRepository;
    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedAdmin();
        seedStaff();
        seedStudent();
        log.info("Demo accounts ready — see README.md 'Default accounts' section for credentials. " +
                 "This seeder is disabled when SPRING_PROFILES_ACTIVE includes 'prod'.");
    }

    private void seedAdmin() {
        if (!userRepository.existsByEmail("admin@scms.com")) {
            Role adminRole = roleRepository.findByName("ADMIN")
                    .orElseThrow(() -> new IllegalStateException("ADMIN role not found — check Flyway migrations."));
            userRepository.save(User.builder()
                    .firstName("System").lastName("Admin")
                    .email("admin@scms.com")
                    .password(passwordEncoder.encode("Admin@1234"))
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build());
            log.info("Seeded demo admin account: admin@scms.com");
        }
    }

    private void seedStaff() {
        if (!userRepository.existsByEmail("staff@scms.com")) {
            Role staffRole = roleRepository.findByName("STAFF")
                    .orElseThrow(() -> new IllegalStateException("STAFF role not found — check Flyway migrations."));
            userRepository.save(User.builder()
                    .firstName("Sam").lastName("Staff")
                    .email("staff@scms.com")
                    .password(passwordEncoder.encode("Staff@1234"))
                    .active(true)
                    .roles(Set.of(staffRole))
                    .build());
            log.info("Seeded demo staff account: staff@scms.com");
        }
    }

    private void seedStudent() {
        if (!userRepository.existsByEmail("student@scms.com")) {
            Role userRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new IllegalStateException("USER role not found — check Flyway migrations."));
            userRepository.save(User.builder()
                    .firstName("Ali").lastName("Student")
                    .email("student@scms.com")
                    .password(passwordEncoder.encode("User@1234"))
                    .phone("9876543210")
                    .active(true)
                    .roles(Set.of(userRole))
                    .build());
            log.info("Seeded demo student account: student@scms.com");
        }
    }
}
