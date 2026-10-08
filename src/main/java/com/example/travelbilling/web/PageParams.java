package com.example.travelbilling.web;

import com.example.travelbilling.common.Page;
import com.example.travelbilling.common.PageRequest;
import com.example.travelbilling.common.ValidationException;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** limit defaults to 50; anything outside 1..100 is a 400 (never silently an unbounded list). */
final class PageParams {

    private PageParams() {
    }

    static PageRequest parse(Integer limit, String cursor, Function<String, Optional<Long>> cursorParser) {
        int size = limit == null ? PageRequest.DEFAULT_LIMIT : limit;
        if (size < 1 || size > PageRequest.MAX_LIMIT) {
            throw ValidationException.of("limit", "must be between 1 and " + PageRequest.MAX_LIMIT);
        }
        Long beforeId = null;
        if (cursor != null) {
            beforeId = cursorParser.apply(cursor)
                    .orElseThrow(() -> ValidationException.of("cursor", "is not a valid cursor"));
        }
        return new PageRequest(size, beforeId);
    }

    /** A full page means there may be more; the next page starts below the last id we returned. */
    static <T> Page<T> page(List<T> items, PageRequest request, Function<T, String> idOf) {
        String next = items.size() == request.limit() ? idOf.apply(items.getLast()) : null;
        return new Page<>(items, request.limit(), next);
    }
}
