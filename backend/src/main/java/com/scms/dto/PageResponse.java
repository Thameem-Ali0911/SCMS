package com.scms.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * PageResponse — a stable, frontend-friendly envelope for paginated results.
 *
 * MENTOR NOTE — fixing the v1.3 "no pagination" finding:
 * v1.3's GET /api/complaints and GET /api/admin/users returned a bare JSON
 * array of EVERY row in the table. The report's exact words: "GET
 * /api/complaints will return 50,000 rows as a JSON array... the browser
 * will freeze." Every list endpoint in v2.0 now returns this envelope
 * instead of a raw array, with `page`/`size`/`sort` query parameters
 * controlling the underlying Spring Data Pageable.
 *
 * We deliberately don't expose Spring's own PageImpl directly in the API —
 * that would leak a Spring-specific JSON shape (and its quirky field names)
 * into the public contract. This DTO is small, stable, and ours.
 */
@Data
@Builder
public class PageResponse<T> {
    private List<T> content;
    private int      page;
    private int      size;
    private long     totalElements;
    private int      totalPages;
    private boolean  last;

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return PageResponse.<T>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
