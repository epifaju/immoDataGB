package com.immodata.gb.api;

import java.util.List;

public record PageResponse<T>(
        int page, int size, long totalElements, int totalPages, List<T> items) {

    public static <T> PageResponse<T> of(int page, int size, long totalElements, List<T> items) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil(totalElements / (double) size);
        return new PageResponse<>(page, size, totalElements, totalPages, items);
    }
}
