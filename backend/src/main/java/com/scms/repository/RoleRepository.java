package com.scms.repository;

import com.scms.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * RoleRepository — data access for the roles table.
 *
 * MENTOR NOTE — findByName is used in three places:
 *   1. DataSeeder  — upsert roles at startup
 *   2. AuthService — assign USER role on registration
 *   3. AdminService — resolve the target role when changing a user's role
 *
 * Keeping it here (not duplicating the query logic) is the DRY principle
 * applied to the data-access layer.
 */
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByName(String name);
}
