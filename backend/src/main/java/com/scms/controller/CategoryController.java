package com.scms.controller;

import com.scms.dto.CategoryDtos.CategoryResponse;
import com.scms.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CategoryController — read-only category listing for any authenticated
 * user, used to populate the "New Complaint" category dropdown.
 *
 * Category management (create/rename/deactivate) is admin-only and lives
 * under AdminController (/api/admin/categories) — see SecurityConfig for
 * the role split.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listActive() {
        return ResponseEntity.ok(categoryService.listActive());
    }
}
