package com.scms.repository;

import com.scms.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByActiveTrue();

    Page<User> findAll(Pageable pageable);

    @Query("SELECT u FROM User u WHERE LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<User> search(@Param("q") String query, Pageable pageable);

    /** All users carrying a given role — used to staff the "assign to" dropdown and workload report. */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name IN :roleNames AND u.active = true ORDER BY u.firstName")
    List<User> findActiveByRoleNames(@Param("roleNames") List<String> roleNames);
}
