package com.edumentor.common.util;

import com.edumentor.common.response.PaginatedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageUtil {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final String DEFAULT_SORT = "createdAt";

    private PageUtil() {}

    public static Pageable of(int page, int size) {
        return of(page, size, DEFAULT_SORT, Sort.Direction.DESC);
    }

    public static Pageable of(int page, int size, String sortBy, Sort.Direction sortDir) {
        int safePage = Math.max(1, page) - 1;
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));
        String safeSortBy = (sortBy != null && !sortBy.isBlank()) ? sortBy : DEFAULT_SORT;
        Sort.Direction safeSortDir = sortDir != null ? sortDir : Sort.Direction.DESC;
        return PageRequest.of(safePage, safeSize, Sort.by(safeSortDir, safeSortBy));
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(1, page) - 1;
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));
        Sort safeSort = sort != null ? sort : Sort.by(Sort.Direction.DESC, DEFAULT_SORT);
        return PageRequest.of(safePage, safeSize, safeSort);
    }

    public static <T> PaginatedResponse<T> toPaginatedResponse(Page<T> page) {
        return PaginatedResponse.of(page);
    }
}
