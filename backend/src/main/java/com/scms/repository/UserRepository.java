package com.scms.repository;

import com.scms.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * UserRepository — Spring Data JPA repository for User.
 *
 * MENTOR NOTE — Zero implementation code:
 * Spring Data JPA reads the method names and generates the SQL at runtime.
 *   findByEmail   → SELECT * FROM users WHERE email = ?
 *   existsByEmail → SELECT COUNT(*) > 0 FROM users WHERE email = ?
 *
 * JpaRepository<User, Long> gives you save(), findById(), findAll(),
 * deleteById(), count() — all for free, no code needed.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
