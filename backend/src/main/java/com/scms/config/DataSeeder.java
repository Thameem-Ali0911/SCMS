package com.scms.config;

import com.scms.model.Role;
import com.scms.model.User;
import com.scms.repository.RoleRepository;
import com.scms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * DataSeeder — inserts required seed data on every startup.
 *
 * MENTOR NOTE — CommandLineRunner:
 * run() is called after the Spring ApplicationContext is fully ready.
 * We use "insert if not exists" logic so it is safe to run repeatedly.
 *
 * Default accounts:
 *   Admin : admin@scms.com   / Admin@1234
 *   Student: student@scms.com / User@1234
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository  roleRepository;
    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedRoles();
        seedAdmin();
        seedStudent();
    }

    private void seedRoles() {
        upsertRole("USER",  "Regular student — submits and tracks own complaints");
        upsertRole("ADMIN", "Administrator — manages all complaints and users");
    }

    private void upsertRole(String name, String desc) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(Role.builder().name(name).description(desc).build());
            log.info("Seeded role: {}", name);
        }
    }

    private void seedAdmin() {
        if (!userRepository.existsByEmail("admin@scms.com")) {
            Role adminRole = roleRepository.findByName("ADMIN").orElseThrow();
            userRepository.save(User.builder()
                    .firstName("System").lastName("Admin")
                    .email("admin@scms.com")
                    .password(passwordEncoder.encode("Admin@1234"))
                    .active(true)
                    .roles(Set.of(adminRole))
                    .build());
            log.info("Seeded admin: admin@scms.com / Admin@1234");
        }
    }

    private void seedStudent() {
        if (!userRepository.existsByEmail("student@scms.com")) {
            Role userRole = roleRepository.findByName("USER").orElseThrow();
            userRepository.save(User.builder()
                    .firstName("Ali").lastName("Student")
                    .email("student@scms.com")
                    .password(passwordEncoder.encode("User@1234"))
                    .phone("9876543210")
                    .active(true)
                    .roles(Set.of(userRole))
                    .build());
            log.info("Seeded student: student@scms.com / User@1234");
        }
    }
}
