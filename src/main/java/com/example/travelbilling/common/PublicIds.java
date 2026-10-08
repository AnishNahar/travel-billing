package com.example.travelbilling.common;

import java.util.Optional;

/** Public ids are a type prefix plus the database id, e.g. "txn_42" or "inv_7". */
public final class PublicIds {

    private PublicIds() {
    }

    public static Optional<Long> parse(String prefix, String raw) {
        if (raw == null || !raw.startsWith(prefix)) {
            return Optional.empty();
        }
        String digits = raw.substring(prefix.length());
        if (digits.isEmpty() || digits.length() > 18 || !digits.chars().allMatch(Character::isDigit)) {
            return Optional.empty();
        }
        long value = Long.parseLong(digits);
        return value > 0 ? Optional.of(value) : Optional.empty();
    }
}
