package com.scms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public class CategoryDtos {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class CategoryRequest {
        @NotBlank(message = "Category name is required")
        @Size(max = 100)
        private String name;

        @Size(max = 255)
        private String description;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CategoryResponse {
        private Long    id;
        private String  name;
        private String  description;
        private boolean active;
    }
}
