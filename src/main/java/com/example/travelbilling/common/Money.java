package com.example.travelbilling.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money is BigDecimal with scale 2, stored as DECIMAL(19,2). Inputs with more precision are rejected, never rounded. */
public final class Money {

    public static final int SCALE = 2;
    private static final int MAX_INTEGER_DIGITS = 17;

    private Money() {
    }

    /** Returns a problem message, or null when the amount is a valid non-negative money value. */
    public static String problemWith(BigDecimal amount) {
        if (amount == null) {
            return "is required";
        }
        if (amount.signum() < 0) {
            return "must not be negative";
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > SCALE) {
            return "must have at most " + SCALE + " decimal places";
        }
        if (stripped.precision() - stripped.scale() > MAX_INTEGER_DIGITS) {
            return "is too large";
        }
        return null;
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE);
    }
}
