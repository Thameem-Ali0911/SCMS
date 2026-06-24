package com.scms.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Category — replaces the free-text `category` VARCHAR that complaints used
 * to store directly (v1.3 finding: "category is free-text with no FK to a
 * category table — categories are inconsistent strings (case sensitivity,
 * typos)").
 *
 * MENTOR NOTE — Open/Closed Principle:
 * Just like Role, categories are now data an admin can manage (add, rename,
 * deactivate) without a code change or redeploy. `active` lets an admin
 * retire a category ("Library" → merged into "Academics") without breaking
 * the historical complaints that still reference it — we never hard-delete
 * a category that has complaints attached.
 */
@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
