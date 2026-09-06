package com.spaceagle17.irissearch.util;

import java.util.List;

public final class SearchHints {
    private SearchHints() {}

    public static final List<String> ROTATING_TIP_KEYS = List.of(
            "iris_search.option_search.tip.jump",
            "iris_search.option_search.tip.changed",
            "iris_search.option_search.tip.menu"
    );

    public static final long ROTATE_INTERVAL_S = 3;

    public static final int HINT_LEFT_GAP = 3;
    public static final int HINT_ROW_OFFSET_Y = 16; // Callers pass y = headerRowTop - 16, we need to add 16 again

    /** Current tip which is shown. */
    public static String currentRotatingTipKey() {
        return currentRotatingTipKey(System.currentTimeMillis());
    }

    /** The current tip's position in the rotation, as a {@code " §8(i/n)"} suffix */
    public static String positionSuffix() {
        return positionSuffix(System.currentTimeMillis());
    }

    static int currentRotatingTipIndex(long nowMs) { // package-private for tests
        if (ROTATING_TIP_KEYS.isEmpty()) {
            return -1;
        }
        return (int) ((nowMs / (ROTATE_INTERVAL_S * 1000L)) % ROTATING_TIP_KEYS.size());
    }

    static String currentRotatingTipKey(long nowMs) { // package-private for tests
        int index = currentRotatingTipIndex(nowMs);
        return index < 0 ? null : ROTATING_TIP_KEYS.get(index);
    }

    static String positionSuffix(long nowMs) { // package-private for tests
        int index = currentRotatingTipIndex(nowMs);
        return index < 0 ? "" : " §8(" + (index + 1) + "/" + ROTATING_TIP_KEYS.size() + ")";
    }
}
