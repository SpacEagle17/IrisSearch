package com.spaceagle17.irissearch.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHintsTest {

    private static final long PERIOD_S = SearchHints.ROTATE_INTERVAL_S;
    private static final long INTERVAL_MS = PERIOD_S * 1000L;
    private static final int COUNT = SearchHints.ROTATING_TIP_KEYS.size();

    @Test
    void cyclesThroughEveryTipInOrderThenWrapsAround() {
        for (int i = 0; i < COUNT * 2; i++) {
            long now = i * INTERVAL_MS + 10; // 10ms into slot i
            String expected = SearchHints.ROTATING_TIP_KEYS.get(i % COUNT);
            assertEquals(expected, SearchHints.currentRotatingTipKey(now), "slot " + i);
        }
    }

    @Test
    void tipHoldsForTheWholeInterval() {
        String first = SearchHints.currentRotatingTipKey(0);
        assertEquals(first, SearchHints.currentRotatingTipKey(INTERVAL_MS - 1));
        assertEquals(SearchHints.ROTATING_TIP_KEYS.get(1 % COUNT), SearchHints.currentRotatingTipKey(INTERVAL_MS));
    }

    @Test
    void positionSuffixShowsCurrentTipOutOfTotal() {
        for (int i = 0; i < COUNT * 2; i++) {
            long now = i * INTERVAL_MS + 10; // 10ms into slot i
            String expected = " §8(" + (i % COUNT + 1) + "/" + COUNT + ")";
            assertEquals(expected, SearchHints.positionSuffix(now), "slot " + i);
        }
        // Holds for the whole interval, then advances.
        assertEquals(" §8(1/" + COUNT + ")", SearchHints.positionSuffix(INTERVAL_MS - 1));
        assertEquals(" §8(2/" + COUNT + ")", SearchHints.positionSuffix(INTERVAL_MS));
    }

    @Test
    void publicNoArgAccessorsReturnAValidKeyAndSuffix() {
        assertTrue(SearchHints.ROTATING_TIP_KEYS.contains(SearchHints.currentRotatingTipKey()));
        assertTrue(SearchHints.positionSuffix().matches(" §8\\(\\d+/" + COUNT + "\\)"));
    }
}
