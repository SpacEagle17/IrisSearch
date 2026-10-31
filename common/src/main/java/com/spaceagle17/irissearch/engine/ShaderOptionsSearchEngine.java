package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import com.spaceagle17.irissearch.engine.SearchCache.MenuText;
import com.spaceagle17.irissearch.engine.SearchCache.OptionText;
import com.spaceagle17.irissearch.engine.SearchCache.PreparedQuery;
import com.spaceagle17.irissearch.engine.SearchCache.Texts;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class ShaderOptionsSearchEngine {
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final int MIN_TYPO_WORD_LENGTH = 4;

    // Flags returned by matchFlags: how a needle occurs in a haystack.
    private static final int MATCH_CONTAINS = 1;
    private static final int MATCH_WORD_START = 2;
    private static final int MATCH_WHOLE_WORD = 4;

    /**
     * Result of {@link #computeMatchTier}, containing an exact/synonym match {@code score}
     * and a {@code typo} fallback flag. Valid if {@code score > 0} or {@code typo} is true.
     */
    public record MatchTierResult(int score, boolean typo) {}

    /**
     * A search query split into an optional menu scope and the remaining search term.
     * {@code "water: caustics"} -> scope {@code ["water"]}, term {@code "caustics"};
     */
    public record MenuScopedQuery(List<String> menuScope, String term) {
        public boolean hasScope() { return !menuScope.isEmpty(); }
    }

    private static final int MIN_SCOPE_PART_LENGTH = 2;

    /**
     * Splits a raw query on its first {@code ':'} into a menu scope (before) and a term (after).
     * The scope may itself be {@code '/'}-separated for nested menus. No colon -> the whole query
     * is the term. An empty scope (e.g. {@code ":foo"}) is treated as no scope.
     */
    public static MenuScopedQuery parseMenuScope(String query) {
        if (query == null) return new MenuScopedQuery(List.of(), "");

        int colon = query.indexOf(':');
        if (colon < 0) return new MenuScopedQuery(List.of(), query.trim());

        String scopeRaw = query.substring(0, colon).trim();
        String term = query.substring(colon + 1).trim();
        if (scopeRaw.isEmpty()) return new MenuScopedQuery(List.of(), term);

        List<String> parts = new ArrayList<>();
        for (String part : scopeRaw.split("/")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.length() >= MIN_SCOPE_PART_LENGTH) parts.add(trimmed);
        }
        return new MenuScopedQuery(parts, term);
    }

    /**
     * Scores path relevance against scope constraints
     * Tiers: 3 = exact, 2 = whole-word match, 1 = prefix/substring match. Higher tiers rank first.
     */
    static int menuScopeTier(String path, List<String> scopeParts) { // package-private for tests
        if (scopeParts.isEmpty() || path == null || path.isEmpty()) return 0;
        String[] segments = path.split("/");

        int weakest = Integer.MAX_VALUE;
        for (String part : scopeParts) {
            int bestForPart = 0;
            for (String segment : segments) {
                if (segment.isEmpty() || "root".equals(segment)) continue;
                bestForPart = Math.max(bestForPart, scopePartTierForSegment(segment, part));
            }
            if (bestForPart == 0) return 0; // a required part matched nothing
            weakest = Math.min(weakest, bestForPart);
        }
        return weakest == Integer.MAX_VALUE ? 0 : weakest;
    }

    public static int computeMenuScopeTier(String query, String path) {
        if (query == null) return 0;
        return SearchCache.prepare(query, SearchCache.texts().version).scopeTier(path);
    }

    private static int scopePartTierForSegment(String segmentId, String scopePart) {
        return Math.max(
                scopePartTier(segmentId.toLowerCase(Locale.ROOT), scopePart),
                scopePartTier(SearchCache.texts().menu(segmentId).translated(), scopePart));
    }

    private static int scopePartTier(String menuName, String scopePart) {
        if (menuName.isEmpty()) return 0;
        if (menuName.equals(scopePart)) return 3;
        if (allWordsMatch(scopePart, menuName, true)) return 2;
        if (allWordsMatch(scopePart, menuName, false)) return 1;
        return 0;
    }

    private static boolean allWordsMatch(String needle, String haystack, boolean wholeWord) {
        if (haystack.isEmpty()) return false;
        String[] words = WHITESPACE_PATTERN.split(needle.trim());
        if (words.length == 0) return false;
        for (String word : words) {
            if (word.isEmpty()) continue;
            // Loose mode accepts a word start or any substring, i.e. just "contains".
            boolean found = wholeWord ? (matchFlags(haystack, word) & MATCH_WHOLE_WORD) != 0 : haystack.contains(word);
            if (!found) return false;
        }
        return true;
    }

    public static String stripMenuScope(String query) {
        return parseMenuScope(query).term();
    }

    /** Changed when starts with a {@code '*'} */
    public static boolean isChangedOnlyQuery(String query) {
        return query != null && query.trim().startsWith("*");
    }

    /** The query with any leading {@code '*'} changed-only markers and surrounding whitespace removed. */
    public static String stripChangedOnlyMarker(String query) {
        if (query == null) return "";
        String trimmed = query.trim();
        int i = 0;
        while (i < trimmed.length() && trimmed.charAt(i) == '*') i++;
        return trimmed.substring(i).trim();
    }

    /**
     * Drops everything {@link SearchCache} holds. The caches follow shader pack and language changes
     */
    static void invalidateCaches() { // package-private for tests
        SearchCache.invalidate();
    }

    /**
     * Computes the match tier for an option ID against a query.
     *
     * @param optionId Option ID to search.
     * @param query Search string. A leading {@code "menu:"} restricts matches to that submenu (see {@link #parseMenuScope}).
     * @param path The option's "root/.../screenId" menu path
     * @return Match result with score and typo status.
     */
    public static MatchTierResult computeMatchTier(String optionId, String query, String path) {
        try {
            if (optionId == null || query == null) return new MatchTierResult(0, false);

            Texts texts = SearchCache.texts();
            PreparedQuery prepared = SearchCache.prepare(query, texts.version);
            if (prepared.scoped.hasScope() && prepared.scopeTier(path) == 0) {
                return new MatchTierResult(0, false);
            }

            String trimmedQuery = prepared.term;
            if (trimmedQuery.isEmpty()) {
                // "water:" with no term: every option appears
                return new MatchTierResult(prepared.scoped.hasScope() ? 1 : 0, false);
            }

            OptionText option = texts.option(optionId);
            MenuText menu = texts.menu(getLastPathSegment(path));

            String[] tokens = prepared.tokens;
            if (tokens.length <= 1) {
                int best = computeQueryStringTier(trimmedQuery, option, menu);
                for (String synonym : prepared.termSynonyms) {
                    int synonymScore = scanQueryStringTier(synonym, option, menu);
                    if (synonymScore > best) best = synonymScore;
                }
                if (best > 0) return new MatchTierResult(best, false);

                // Typo tolerance: last resort once literal and synonym matching both came up empty.
                return new MatchTierResult(0, typoMatchesOption(trimmedQuery, option, menu));
            }

            // Multi-word query: ALL tokens must match (AND-combined via bitwise AND).
            // OR'd with literal-phrase tier so full contiguous matches outrank scattered tokens.
            // Typo-only tokens contribute no bits to score, but satisfy the token match requirement.
            int andScore = -1;
            boolean anyTokenTypo = false;
            for (int i = 0; i < tokens.length; i++) {
                int tokenScore = scanQueryStringTier(tokens[i], option, menu);
                for (String synonym : prepared.tokenSynonyms[i]) {
                    int synonymScore = scanQueryStringTier(synonym, option, menu);
                    if (synonymScore > tokenScore) tokenScore = synonymScore;
                }
                if (tokenScore == 0) {
                    if (typoMatchesOption(tokens[i], option, menu)) {
                        anyTokenTypo = true;
                        continue;
                    }
                    return new MatchTierResult(0, false);
                }
                andScore &= tokenScore;
            }
            int realAndScore = andScore == -1 ? 0 : andScore; // -1 means no token ever folded in (all were typo-only)
            int phraseScore = computeQueryStringTier(trimmedQuery, option, menu);
            return new MatchTierResult(phraseScore | realAndScore, anyTokenTypo);
        } catch (Exception e) {
            debugLog("computeMatchTier threw for query \"" + query + "\", treating as no match");
            return new MatchTierResult(0, false);
        }
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /**
     * How {@code needle} occurs in {@code haystack}, as {@code MATCH_*} flags: anywhere, starting a word, or as a
     * whole word, where word boundaries are any characters outside {@code [a-zA-Z0-9]}. Every occurrence is
     * checked, so one that sits mid-word at first but starts or ends a word later still counts.
     */
    private static int matchFlags(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        if (idx < 0) return 0;

        int flags = MATCH_CONTAINS;
        int needleLength = needle.length();
        while (idx >= 0) {
            if (idx == 0 || !isAsciiAlnum(haystack.charAt(idx - 1))) {
                flags |= MATCH_WORD_START;
                int end = idx + needleLength;
                if (end == haystack.length() || !isAsciiAlnum(haystack.charAt(end))) return flags | MATCH_WHOLE_WORD;
            }
            idx = haystack.indexOf(needle, idx + 1);
        }
        return flags;
    }

    private static int scanQueryStringTier(String singleQuery, OptionText option, MenuText menu) {
        String translated = option.translated();
        String defaultName = option.defaultName();
        String comment = option.comment();

        // Translated name bits interleaved with default name bits (translated always one bit higher).
        // Default name bits sit above rawId/comment so en_us matches outrank ID/comment matches.
        // The containing-submenu bits sit at the very bottom: a menu-name match is the weakest signal,
        // only meant to surface options an on-topic match doesn't already cover.
        int score = 0;

        if (!translated.isEmpty()) {
            if (translated.equals(singleQuery)) score |= (1 << 16);
            int flags = matchFlags(translated, singleQuery);
            if ((flags & MATCH_WHOLE_WORD) != 0) score |= (1 << 14);
            if ((flags & MATCH_WORD_START) != 0) score |= (1 << 12);
            if ((flags & MATCH_CONTAINS) != 0)   score |= (1 << 6);
        }
        if (!defaultName.isEmpty()) {
            if (defaultName.equals(singleQuery)) score |= (1 << 15);
            int flags = matchFlags(defaultName, singleQuery);
            if ((flags & MATCH_WHOLE_WORD) != 0) score |= (1 << 13);
            if ((flags & MATCH_WORD_START) != 0) score |= (1 << 11);
            if ((flags & MATCH_CONTAINS) != 0)   score |= (1 << 5);
        }

        int rawFlags = matchFlags(option.rawId(), singleQuery);
        if ((rawFlags & MATCH_WHOLE_WORD) != 0) score |= (1 << 10);
        if ((rawFlags & MATCH_WORD_START) != 0) score |= (1 << 8);
        if ((rawFlags & MATCH_CONTAINS) != 0)   score |= (1 << 4);

        if (!comment.isEmpty()) {
            int flags = matchFlags(comment, singleQuery);
            if ((flags & MATCH_WHOLE_WORD) != 0) score |= (1 << 9);
            if ((flags & MATCH_WORD_START) != 0) score |= (1 << 7);
            if ((flags & MATCH_CONTAINS) != 0)   score |= (1 << 3);
        }

        for (String valueText : option.valueTexts()) { // value.<optionId>.<suffix>
            if (valueText.contains(singleQuery)) {
                score |= (1 << 2);
                break;
            }
        }

        int menuFlags = 0;
        if (!menu.translated().isEmpty()) menuFlags |= matchFlags(menu.translated(), singleQuery);
        if (!menu.rawId().isEmpty())      menuFlags |= matchFlags(menu.rawId(), singleQuery);
        if ((menuFlags & MATCH_WHOLE_WORD) != 0) score |= (1 << 1);
        if (menuFlags != 0)                      score |= 1; // starts a word in, or merely appears within, the submenu

        return score;
    }

    /** Computes the match tier of a single query string (one standalone token, or a full phrase) against an option's fields. */
    private static int computeQueryStringTier(String singleQuery, OptionText option, MenuText menu) {
        // 1 char Ascii query: only match if a readable name starts directly with the query
        // Only readableTranslatedName as that feels better
        if (singleQuery.length() == 1 && isOnlyAscii(singleQuery)) {
            return (!option.translated().isEmpty() && option.translated().startsWith(singleQuery)) ? 1 : 0;
        }

        return scanQueryStringTier(singleQuery, option, menu);
    }

    /** Whether the query is a close-enough typo of any word in the option's names or its submenu's name. */
    private static boolean typoMatchesOption(String query, OptionText option, MenuText menu) {
        return typoMatchesAnyWord(query, option.translatedWords()) || typoMatchesAnyWord(query, option.defaultWords())
                || typoMatchesAnyWord(query, menu.translatedWords());
    }

    /** Whether any whitespace-separated word in {@code readableName} is a close-enough typo of {@code query}. */
    static boolean typoMatchesAnyWord(String query, String readableName) { // package-private for tests
        if (readableName == null || readableName.isEmpty()) return false;
        return typoMatchesAnyWord(query, WHITESPACE_PATTERN.split(readableName));
    }

    private static boolean typoMatchesAnyWord(String query, String[] words) {
        for (String word : words) {
            if (isTypoMatch(query, word)) return true;
        }
        return false;
    }

    /**
     * Checks if {@code query} matches {@code word} within an allowed Levenshtein distance
     * (1 for words <= 6 chars, 2 for longer). Enforces {@link #MIN_TYPO_WORD_LENGTH}.
     */
    static boolean isTypoMatch(String query, String word) { // package-private for tests
        int queryLength = query.length();
        int wordLength = word.length();
        if (queryLength < MIN_TYPO_WORD_LENGTH || wordLength < MIN_TYPO_WORD_LENGTH) return false;

        int maxDistance = queryLength <= 6 ? 1 : 2;
        if (Math.abs(queryLength - wordLength) > maxDistance) return false; // cheap reject before the DP below

        return levenshteinDistance(query, word) <= maxDistance;
    }

    /** Classic Levenshtein edit distance (insertions/deletions/substitutions) between two strings. */
    static int levenshteinDistance(String a, String b) { // package-private for tests
        int[] previousRow = new int[b.length() + 1];
        int[] currentRow = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previousRow[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            currentRow[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitutionCost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                currentRow[j] = Math.min(
                        Math.min(currentRow[j - 1] + 1, previousRow[j] + 1),
                        previousRow[j - 1] + substitutionCost
                );
            }
            int[] swap = previousRow;
            previousRow = currentRow;
            currentRow = swap;
        }

        return previousRow[b.length()];
    }

    /**
     * @return A flattened list of unique option IDs, preserving the order of first occurrence.
     */
    public static List<String> getAllOptionsFlattened(List<String> optionIds) {
        if (optionIds == null) return new ArrayList<>();

        Set<String> unique = new LinkedHashSet<>();
        for (String optionId : optionIds) {
            if (optionId != null) unique.add(optionId);
        }
        return new ArrayList<>(unique);
    }

    // Bitmask of score bits that come from either readable name (translated bits 16,14,12,6 and default bits 15,13,11,5).
    // Used to separate "how well does the readable name match" from comment/rawId/menu/value noise.
    private static final int READABLE_NAME_BITS = (1 << 16) | (1 << 15) | (1 << 14) | (1 << 13) | (1 << 12) | (1 << 11) | (1 << 6) | (1 << 5);

    public record ScoredOptionElement(String optionId, String readableTranslatedName, String readableDefaultName, String path, int score, boolean typo, String query, int menuScopeTier) implements Comparable<ScoredOptionElement> {
        /** Overload for callers/tests that don't use "menu:" scoping. */
        public ScoredOptionElement(String optionId, String readableTranslatedName, String readableDefaultName, String path, int score, boolean typo, String query) {
            this(optionId, readableTranslatedName, readableDefaultName, path, score, typo, query, 0);
        }

        @Override
        public int compareTo(@NotNull ScoredOptionElement other) {
            String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
            int result;
            if ((result = compareByMenuScopeTier(this, other))        != 0) return result;
            if ((result = compareByTypoPenalty(this, other))          != 0) return result;
            if ((result = compareByReadableNameQuality(this, other))  != 0) return result;
            if ((result = compareByMatchedWordLength(this, other, q)) != 0) return result;
            if ((result = compareByWordCount(this, other))            != 0) return result;
            if ((result = compareByFullScore(this, other))            != 0) return result;
            if ((result = compareByPrefixBoost(this, other, q))      != 0) return result;
            if ((result = compareByPathDepth(this, other))           != 0) return result;
            return compareByAlphabetical(this, other);
        }

        // 0. With a "menu:" scope active, options in a better-matching submenu come first
        // Always 0 for unscoped queries, not affecting anything below
        private static int compareByMenuScopeTier(ScoredOptionElement a, ScoredOptionElement b) {
            return Integer.compare(b.menuScopeTier, a.menuScopeTier);
        }

        // 1. Typo-tolerant matches always rank below any real match.
        private static int compareByTypoPenalty(ScoredOptionElement a, ScoredOptionElement b) {
            if (a.typo == b.typo) return 0;
            return a.typo ? 1 : -1;
        }

        // 2. Readable-name match quality (translated + default name bits only).
        private static int compareByReadableNameQuality(ScoredOptionElement a, ScoredOptionElement b) {
            int aReadable = a.score & READABLE_NAME_BITS;
            int bReadable = b.score & READABLE_NAME_BITS;
            return Integer.compare(bReadable, aReadable);
        }

        // 3. Sort by matched-word length (shorter word = higher query coverage = more relevant).
        // Prefer translated name; fall back to default name if no translated match.
        private static int compareByMatchedWordLength(ScoredOptionElement a, ScoredOptionElement b, String q) {
            if (q.isEmpty()) return 0;
            return Integer.compare(matchedWordLength(a, q), matchedWordLength(b, q));
        }

        private static int matchedWordLength(ScoredOptionElement element, String q) {
            int length = findMatchingWordLength(element.readableTranslatedName, q);
            if (length < 0) length = findMatchingWordLength(element.readableDefaultName, q);
            return length >= 0 ? length : Integer.MAX_VALUE;
        }

        // 4. Fewer words in readable name = more precise match.
        // "Bloom" beats "Bloom Strength" when matched word length ties.
        // Use translated name if available, otherwise fall back to default.
        private static int compareByWordCount(ScoredOptionElement a, ScoredOptionElement b) {
            String aEffective = !a.readableTranslatedName.isEmpty() ? a.readableTranslatedName : a.readableDefaultName;
            String bEffective = !b.readableTranslatedName.isEmpty() ? b.readableTranslatedName : b.readableDefaultName;
            return Integer.compare(countWords(aEffective), countWords(bEffective));
        }

        // 5. Full score (comment/rawId matches as secondary signal).
        private static int compareByFullScore(ScoredOptionElement a, ScoredOptionElement b) {
            return Integer.compare(b.score, a.score);
        }

        // 6. Prefix boost: either readable name starts with the exact query string.
        private static int compareByPrefixBoost(ScoredOptionElement a, ScoredOptionElement b, String q) {
            if (q.isEmpty()) return 0;
            boolean aPrefixes = (a.readableTranslatedName != null && a.readableTranslatedName.startsWith(q))
                    || (a.readableDefaultName    != null && a.readableDefaultName.startsWith(q));
            boolean bPrefixes = (b.readableTranslatedName != null && b.readableTranslatedName.startsWith(q))
                    || (b.readableDefaultName    != null && b.readableDefaultName.startsWith(q));
            if (aPrefixes == bPrefixes) return 0;
            return aPrefixes ? -1 : 1;
        }

        // 7. Path depth: fewer slashes (shallower) wins.
        private static int compareByPathDepth(ScoredOptionElement a, ScoredOptionElement b) {
            return Integer.compare(countSlashes(a.path), countSlashes(b.path));
        }

        // 8. Alphabetical tie-breaker.
        private static int compareByAlphabetical(ScoredOptionElement a, ScoredOptionElement b) {
            if (a.optionId != null && b.optionId != null) return a.optionId.compareTo(b.optionId);
            return 0;
        }

        // Runs inside the sort comparator, so it walks the string instead of splitting it.
        // Whitespace is what the "\\s" regex class means: space, \t, \n, \u000B, \f, \r.
        private static boolean isRegexWhitespace(char c) {
            return c == ' ' || (c >= '\t' && c <= '\r');
        }

        /** Length of the first whitespace-separated word starting with {@code query}, or -1 if there is none. */
        private static int findMatchingWordLength(String readableName, String query) {
            if (readableName == null || query.isEmpty()) return -1;
            int length = readableName.length();
            int i = 0;
            while (i < length) {
                while (i < length && isRegexWhitespace(readableName.charAt(i))) i++;
                int start = i;
                while (i < length && !isRegexWhitespace(readableName.charAt(i))) i++;
                if (i - start >= query.length() && readableName.startsWith(query, start)) return i - start;
            }
            return -1;
        }

        private static int countWords(String s) {
            if (s == null || s.isBlank()) return 0;
            int count = 0;
            int length = s.length();
            int i = 0;
            while (i < length) {
                while (i < length && isRegexWhitespace(s.charAt(i))) i++;
                if (i == length) break;
                count++;
                while (i < length && !isRegexWhitespace(s.charAt(i))) i++;
            }
            return count;
        }

        private static int countSlashes(String path) {
            if (path == null) return 0;
            int count = 0;
            for (int i = 0; i < path.length(); i++) {
                if (path.charAt(i) == '/') count++;
            }
            return count;
        }
    }

    private static boolean isOnlyAscii(String string) {
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) > 127) return false;
        }
        return true;
    }

    /** Delegates to {@link MinecraftLanguageAccess} for the currently active game language's translation. */
    public static String getReadableTranslatedName(String optionId) {
        if (optionId == null) return MinecraftLanguageAccess.getLowercaseString("option." + optionId);
        return SearchCache.texts().option(optionId).translated();
    }

    /** Delegates to {@link IrisShaderPackTranslations} for the shader pack's own en_us default translation. */
    public static String getReadableDefaultName(String optionId) {
        if (optionId == null) return IrisShaderPackTranslations.getLowercaseDefaultTranslatedString("option." + optionId);
        return SearchCache.texts().option(optionId).defaultName();
    }

    /** Delegates to {@link MinecraftLanguageAccess} for a "screen.xxx" translation, e.g. for breadcrumb labels. */
    public static String getDisplaySettingsName(String screenId) {
        return MinecraftLanguageAccess.getColorStrippedString("screen." + screenId);
    }

    /**
     * Returns the last real segment of a "root/.../screenId" option path - the option's immediate containing submenu
     * or null for root-level options / an unknown path.
     */
    private static String getLastPathSegment(String path) {
        if (path == null || path.isEmpty()) return null;
        String[] segments = path.split("/");
        for (int i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isEmpty() && !"root".equals(segments[i])) return segments[i];
        }
        return null;
    }

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderOptionsSearchEngine] " + message);
    }
}
