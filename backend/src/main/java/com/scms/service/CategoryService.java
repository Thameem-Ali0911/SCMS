package com.scms.service;

import com.scms.dto.CategoryDtos.CategoryRequest;
import com.scms.dto.CategoryDtos.CategoryResponse;
import com.scms.model.Category;
import com.scms.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * CategoryService — manages the Category reference table.
 *
 * MENTOR NOTE — replacing free-text categories (v1.3 Database Design finding):
 * Complaints used to store category as a raw VARCHAR the frontend hardcoded
 * a fixed list for. Now an admin can add ("Sports Facilities"), rename, or
 * deactivate a category from the Admin UI with zero code changes — the
 * Open/Closed Principle the v1.3 report praised Role for, now applied here
 * too. Categories already referenced by a complaint are never hard-deleted
 * (deactivate instead) so historical complaints never lose their category.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc()
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll()
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists");
        }
        Category category = Category.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .active(true)
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active) {
        Category category = findOrThrow(id);
        category.setActive(active);
        return toResponse(categoryRepository.save(category));
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private CategoryResponse toResponse(Category c) {
        return CategoryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .active(c.isActive())
                .build();
    }
}
