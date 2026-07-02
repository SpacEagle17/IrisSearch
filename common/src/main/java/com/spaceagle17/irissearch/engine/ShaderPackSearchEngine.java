package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderPackSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern ZIP_EXTENSION_PATTERN = Pattern.compile("\\.zip$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+){0,3}");

    /** Computes the match tier for a given shader pack name and query. Higher is better; 0 means no match. */
    public static int computeMatchTier(String packName, String query) {
        try {
            if (packName == null || query == null) return 0;

            String trimmedQuery = query.toLowerCase(Locale.ROOT).trim();
            if (trimmedQuery.isEmpty()) return 0;

            String readableName = getReadableName(packName);
            if (readableName.isEmpty()) return 0;

            // 1 char ASCII query: only match if the name starts directly with the query.
            if (trimmedQuery.length() == 1 && isOnlyAscii(trimmedQuery)) {
                return readableName.startsWith(trimmedQuery) ? 1 : 0;
            }

            String escapedQuery = Pattern.quote(trimmedQuery);
            Pattern wholeWordPat = Pattern.compile(String.format(WHOLE_WORD_REGEX, escapedQuery));
            Pattern startsWithPat = Pattern.compile(String.format(STARTS_WITH_REGEX, escapedQuery));

            int score = 0;
            if (readableName.equals(trimmedQuery))           score |= (1 << 4);
            if (wholeWordPat.matcher(readableName).find())   score |= (1 << 3);
            if (startsWithPat.matcher(readableName).find())  score |= (1 << 2);
            if (readableName.contains(trimmedQuery))         score |= (1 << 1);

            return score;
        } catch (Exception e) {
            debugLog("computeMatchTier threw for query \"" + query + "\", treating as no match");
            return 0;
        }
    }

    // Strip color codes and the trailing .zip extension -- the name is otherwise already human-readable.
    public static String getReadableName(String packName) {
        if (packName == null) return "";
        String stripped = COLOR_CODE_PATTERN.matcher(packName).replaceAll("");
        stripped = ZIP_EXTENSION_PATTERN.matcher(stripped).replaceAll("");
        return stripped.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean isOnlyAscii(String string) {
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) > 127) return false;
        }
        return true;
    }

    public record ScoredPackElement(String packName, String readableName, int score, String query) implements Comparable<ScoredPackElement> {
        public ScoredPackElement(String packName, int score, String query) {
            this(packName, getReadableName(packName), score, query);
        }

        @Override
        public int compareTo(@NotNull ScoredPackElement other) {
            String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
            int result;
            if ((result = compareByMatchedWordLength(this, other, q)) != 0) return result;
            if ((result = compareByWordCount(this, other))            != 0) return result;
            if ((result = compareByFullScore(this, other))            != 0) return result;
            if ((result = compareByPrefixBoost(this, other, q))       != 0) return result;
            if ((result = compareByVersion(this, other))              != 0) return result;
            return compareByAlphabetical(this, other);
        }

        // 1. Shorter matched word = higher query coverage = more relevant.
        private static int compareByMatchedWordLength(ScoredPackElement a, ScoredPackElement b, String q) {
            if (q.isEmpty()) return 0;
            String aWord = findMatchingWord(a.readableName, q);
            String bWord = findMatchingWord(b.readableName, q);
            int aLen = aWord != null ? aWord.length() : Integer.MAX_VALUE;
            int bLen = bWord != null ? bWord.length() : Integer.MAX_VALUE;
            return Integer.compare(aLen, bLen);
        }

        // 2. Fewer words in the name = more precise match.
        private static int compareByWordCount(ScoredPackElement a, ScoredPackElement b) {
            return Integer.compare(countWords(a.readableName), countWords(b.readableName));
        }

        // 3. Full score (match tier bits).
        private static int compareByFullScore(ScoredPackElement a, ScoredPackElement b) {
            return Integer.compare(b.score, a.score);
        }

        // 4. Prefix boost: name starts with the exact query string.
        private static int compareByPrefixBoost(ScoredPackElement a, ScoredPackElement b, String q) {
            if (q.isEmpty()) return 0;
            boolean aPrefixes = a.readableName.startsWith(q);
            boolean bPrefixes = b.readableName.startsWith(q);
            if (aPrefixes == bPrefixes) return 0;
            return aPrefixes ? -1 : 1;
        }

        // 5. Same name minus a version number (e.g. "BSL v8" / "BSL v10") -- higher version wins.
        private static int compareByVersion(ScoredPackElement a, ScoredPackElement b) {
            VersionMatch av = extractVersion(a.readableName);
            VersionMatch bv = extractVersion(b.readableName);
            if (av == null || bv == null || !av.remainder.equals(bv.remainder)) return 0;
            return compareVersionNumbers(bv.version, av.version);
        }

        // 6. Alphabetical tie-breaker.
        private static int compareByAlphabetical(ScoredPackElement a, ScoredPackElement b) {
            if (a.packName != null && b.packName != null) return a.packName.compareToIgnoreCase(b.packName);
            return 0;
        }

        private static String findMatchingWord(String readableName, String query) {
            if (readableName == null || query.isEmpty()) return null;
            for (String word : readableName.split("[\\s\\-_]+")) {
                if (word.startsWith(query)) return word;
            }
            return null;
        }

        private static int countWords(String s) {
            if (s == null || s.isBlank()) return 0;
            return s.trim().split("\\s+").length;
        }
    }

    // A version-number candidate found in a name, plus the name with that number removed (for equality checks).
    private record VersionMatch(String remainder, int[] version) {}

    // Finds the first version-like number sequence (e.g. "8", "3.4", "10.2.1") in the name, if any.
    private static VersionMatch extractVersion(String readableName) {
        if (readableName == null) return null;
        Matcher m = VERSION_PATTERN.matcher(readableName);
        if (!m.find()) return null;

        String remainder = readableName.substring(0, m.start()) + readableName.substring(m.end());
        String[] parts = m.group().split("\\.");
        int[] version = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                version[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return new VersionMatch(remainder, version);
    }

    // Ascending compare, shorter array padded with 0s (so "1.2" == "1.2.0").
    private static int compareVersionNumbers(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            int cmp = Integer.compare(av, bv);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackSearchEngine] " + message);
    }
}
