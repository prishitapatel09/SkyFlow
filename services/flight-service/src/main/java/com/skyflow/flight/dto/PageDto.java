package com.skyflow.flight.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Paged payload in the shape the React client's {@code PaginatedResponse} already expects. */
public record PageDto<T>(List<T> items, Pagination pagination) {

    public record Pagination(int page, int size, long total, int totalPages) {
    }

    public static <E, T> PageDto<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageDto<>(
                page.getContent().stream().map(mapper).toList(),
                new Pagination(page.getNumber(), page.getSize(), page.getTotalElements(),
                        page.getTotalPages()));
    }
}
