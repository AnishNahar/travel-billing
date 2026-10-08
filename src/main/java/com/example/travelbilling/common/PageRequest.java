package com.example.travelbilling.common;

/** Keyset page request: rows with id strictly below {@code beforeId} (newest first), at most {@code limit}. */
public record PageRequest(int limit, Long beforeId) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;
}
