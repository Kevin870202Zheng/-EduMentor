package com.edumentor.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasMore;
    private String timestamp;

    public static <T> PaginatedResponse<T> of(Page<T> pageResult) {
        return PaginatedResponse.<T>builder()
            .items(pageResult.getContent())
            .total(pageResult.getTotalElements())
            .page(pageResult.getNumber() + 1)
            .size(pageResult.getSize())
            .totalPages(pageResult.getTotalPages())
            .hasMore(pageResult.hasNext())
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public static <T> PaginatedResponse<T> of(List<T> items, long total, int page, int size) {
        int totalPages = (size > 0) ? (int) Math.ceil((double) total / size) : 0;
        return PaginatedResponse.<T>builder()
            .items(items)
            .total(total)
            .page(page)
            .size(size)
            .totalPages(totalPages)
            .hasMore(page < totalPages)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public ApiResponse<PaginatedResponse<T>> toApiResponse() {
        return ApiResponse.success(this);
    }
}
