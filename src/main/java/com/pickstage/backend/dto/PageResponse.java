package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.Builder;
import org.springframework.data.domain.Page;
import java.util.List;

@Getter
@Builder
public class PageResponse<T> {

    private List<T> content;
    private int totalElements;
    private int totalPages;
    private int page;

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .totalElements((int) page.getTotalElements())
                .totalPages(page.getTotalPages())
                .page(page.getNumber())
                .build();
    }
}