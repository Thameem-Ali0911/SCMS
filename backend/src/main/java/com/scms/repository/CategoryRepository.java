package com.scms.repository;

import com.scms.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByNameIgnoreCase(String name);
    List<Category> findAllByActiveTrueOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}
