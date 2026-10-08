package com.example.travelbilling.common;

import java.util.List;

/** One page of a keyset-paginated list. next_cursor is absent on the last page. */
public record Page<T>(List<T> items, int limit, String nextCursor) {
}
