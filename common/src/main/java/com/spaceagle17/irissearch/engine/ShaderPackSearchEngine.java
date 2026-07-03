package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderPackSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern ZIP_EXTENSION_PATTERN = Pattern.compile("\\.zip$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+){0,3}");

    // Well-known shader packs, normalized.
    private static final Set<String> POPULAR_PACK_KEYWORDS = Set.of(
            "complementaryreimagined", "complementaryunbound", "bliss", "bsl", "solas", "kappa", "spooklementary",
            "chocapic", "photon", "rethinkingvoxels", "euphoriapatches", "superdupervanilla", "astralex", "sildurs",
            "nostalgia"
    );

    private static final Pattern COMPLEMENTARY_REIMAGINED_PATTERN = Pattern.compile("complementaryreimagined");
    private static final Pattern COMPLEMENTARY_UNBOUND_PATTERN = Pattern.compile("complementaryunbound");

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

    private static boolean isPopular(String readableName) {
        if (readableName == null) return false;
        String normalized = readableName.replaceAll("[^a-z0-9]", "");
        for (String keyword : POPULAR_PACK_KEYWORDS) {
            if (normalized.contains(keyword)) return true;
        }
        return false;
    }

    // Collapses Reimagined/Unbound to a shared token so compareByVersion treats them as the same shader.
    private static String normalizeVersionFamily(String readableName) {
        if (readableName == null) return null;
        String normalized = COMPLEMENTARY_REIMAGINED_PATTERN.matcher(readableName).replaceAll("complementary");
        return COMPLEMENTARY_UNBOUND_PATTERN.matcher(normalized).replaceAll("complementary");
    }

    public record ScoredPackElement(String packName, String readableName, int score, String query) implements Comparable<ScoredPackElement> {
        public ScoredPackElement(String packName, int score, String query) {
            this(packName, getReadableName(packName), score, query);
        }

        @Override
        public int compareTo(@NotNull ScoredPackElement other) {
            String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
            int result;
            if ((result = compareByFullScore(this, other))            != 0) return result;
            if ((result = compareByPrefixBoost(this, other, q))       != 0) return result;
            if ((result = compareByPopularity(this, other))           != 0) return result;
            if ((result = compareByVersion(this, other))              != 0) return result;
            if ((result = compareByMatchedWordLength(this, other, q)) != 0) return result;
            return compareByAlphabetical(this, other);
        }

        // 1. Full score (match tier bits).
        private static int compareByFullScore(ScoredPackElement a, ScoredPackElement b) {
            return Integer.compare(b.score, a.score);
        }

        // 2. Prefix boost: name starts with the exact query string.
        private static int compareByPrefixBoost(ScoredPackElement a, ScoredPackElement b, String q) {
            if (q.isEmpty()) return 0;
            boolean aPrefixes = a.readableName.startsWith(q);
            boolean bPrefixes = b.readableName.startsWith(q);
            if (aPrefixes == bPrefixes) return 0;
            return aPrefixes ? -1 : 1;
        }

        // 3. Well-known packs (see POPULAR_PACK_KEYWORDS) outrank lesser-known ones at the same match tier.
        private static int compareByPopularity(ScoredPackElement a, ScoredPackElement b) {
            boolean aPopular = isPopular(a.readableName);
            boolean bPopular = isPopular(b.readableName);
            if (aPopular == bPopular) return 0;
            return aPopular ? -1 : 1;
        }

        // 4. Version number comparison: higher version numbers outrank lower ones, but only if the prefix text is identical.
        private static int compareByVersion(ScoredPackElement a, ScoredPackElement b) {
            VersionMatch av = extractVersions(normalizeVersionFamily(a.readableName));
            VersionMatch bv = extractVersions(normalizeVersionFamily(b.readableName));
            if (av == null || bv == null || !av.prefix.equals(bv.prefix)) return 0;

            int len = Math.min(av.versions.size(), bv.versions.size());
            for (int i = 0; i < len; i++) {
                int cmp = compareVersionNumbers(bv.versions.get(i), av.versions.get(i));
                if (cmp != 0) return cmp;
            }
            return 0;
        }

        // 5. Shorter matched word = higher query coverage = more relevant.
        private static int compareByMatchedWordLength(ScoredPackElement a, ScoredPackElement b, String q) {
            if (q.isEmpty()) return 0;
            String aWord = findMatchingWord(a.readableName, q);
            String bWord = findMatchingWord(b.readableName, q);
            int aLen = aWord != null ? aWord.length() : Integer.MAX_VALUE;
            int bLen = bWord != null ? bWord.length() : Integer.MAX_VALUE;
            return Integer.compare(aLen, bLen);
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
    }

    private record VersionMatch(String prefix, List<int[]> versions) {}

    // Finds every version-like number sequence (e.g. "8", "3.4", "10.2.1") in the name, in order.
    private static VersionMatch extractVersions(String readableName) {
        if (readableName == null) return null;
        Matcher m = VERSION_PATTERN.matcher(readableName);
        if (!m.find()) return null;

        String prefix = readableName.substring(0, m.start());
        List<int[]> versions = new ArrayList<>();
        do {
            String[] parts = m.group().split("\\.");
            int[] version = new int[parts.length];
            boolean valid = true;
            for (int i = 0; i < parts.length; i++) {
                try {
                    version[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    valid = false;
                    break;
                }
            }
            if (valid) versions.add(version);
        } while (m.find());

        if (versions.isEmpty()) return null;
        return new VersionMatch(prefix, versions);
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
