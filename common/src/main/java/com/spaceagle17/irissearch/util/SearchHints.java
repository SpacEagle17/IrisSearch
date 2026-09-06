package com.spaceagle17.irissearch.util;

import java.util.List;

public final class SearchHints {
    private SearchHints() {}

    public static final List<String> ROTATING_TIP_KEYS = List.of(
            "iris_search.option_search.tip.menu",
            "iris_search.option_search.tip.changed",
            "iris_search.option_search.tip.jump"
    );

    public static final long ROTATE_INTERVAL_S = 3;

    public static final int HINT_LEFT_GAP = 3;
    public static final int HINT_ROW_OFFSET_Y = 16; // Callers pass y = headerRowTop - 16, we need to add 16 again

    /** Current tip which is shown. */
    public static String currentRotatingTipKey() {
        return currentRotatingTipKey(System.currentTimeMillis());
    }

    /** A  countdown {@code " §8(N)"} suffix until the tip swaps */
    public static String countdownSuffix() {
        return countdownSuffix(System.currentTimeMillis());
    }

    static String currentRotatingTipKey(long nowMs) { // package-private for tests
        if (ROTATING_TIP_KEYS.isEmpty()) {
            return null;
        }
        int index = (int) ((nowMs / (ROTATE_INTERVAL_S * 1000L)) % ROTATING_TIP_KEYS.size());
        return ROTATING_TIP_KEYS.get(index);
    }

    static String countdownSuffix(long nowMs) { // package-private for tests
        long periodSeconds = Math.max(1L, ROTATE_INTERVAL_S);
        long secondsLeft = periodSeconds - (nowMs / 1000L) % periodSeconds;
        return " §8(" + secondsLeft + ")";
    }
}
