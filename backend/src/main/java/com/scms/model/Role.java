package com.scms.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Role — RBAC role entity (USER, ADMIN).
 *
 * MENTOR NOTE — Roles as data, not hardcoded strings: Storing roles in the
 * database means a Super Admin can add new roles (e.g. DEPT_HEAD, STAFF)
 * without redeploying code. This is the Open/Closed Principle — open for
 * extension, closed for modification.
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 50)
    private String name;          // "USER" or "ADMIN"

    @Column(length = 255)
    private String description;
}
