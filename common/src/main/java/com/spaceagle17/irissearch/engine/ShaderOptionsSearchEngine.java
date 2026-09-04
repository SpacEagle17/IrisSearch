package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class ShaderOptionsSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private static final int MIN_TYPO_WORD_LENGTH = 4;
    private static final int MIN_FUZZY_SYNONYM_KEY_LENGTH = 3;

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

    private static boolean pathMatchesMenuScope(String path, List<String> scopeParts) {
        return menuScopeTier(path, scopeParts) > 0;
    }

    public static int computeMenuScopeTier(String query, String path) {
        MenuScopedQuery scoped = parseMenuScope(query);
        return scoped.hasScope() ? menuScopeTier(path, scoped.menuScope()) : 0;
    }

    private static int scopePartTierForSegment(String segmentId, String scopePart) {
        return Math.max(
                scopePartTier(segmentId.toLowerCase(Locale.ROOT), scopePart),
                scopePartTier(resolveMenuName(segmentId), scopePart));
    }

    private static int scopePartTier(String menuName, String scopePart) {
        if (menuName.isEmpty()) return 0;
        if (menuName.equals(scopePart)) return 3;
        if (allWordsMatch(scopePart, menuName, true)) return 2;
        if (allWordsMatch(scopePart, menuName, false)) return 1;
        return 0;
    }

    private static String resolveMenuName(String screenId) {
        String active = getReadableMenuName(screenId);
        if (!active.isEmpty()) return active;
        if (screenId == null || screenId.isEmpty()) return "";
        return IrisShaderPackTranslations.getLowercaseDefaultTranslatedString("screen." + screenId).replaceAll("\\s+>", "");
    }

    private static boolean allWordsMatch(String needle, String haystack, boolean wholeWord) {
        if (haystack.isEmpty()) return false;
        String[] words = WHITESPACE_PATTERN.split(needle.trim());
        if (words.length == 0) return false;
        for (String word : words) {
            if (word.isEmpty()) continue;
            String format = wholeWord ? WHOLE_WORD_REGEX : STARTS_WITH_REGEX;
            Pattern pattern = Pattern.compile(String.format(format, Pattern.quote(word)));
            if (!pattern.matcher(haystack).find() && !(!wholeWord && haystack.contains(word))) return false;
        }
        return true;
    }

    public static String stripMenuScope(String query) {
        return parseMenuScope(query).term();
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

            MenuScopedQuery scoped = parseMenuScope(query);
            if (scoped.hasScope() && !pathMatchesMenuScope(path, scoped.menuScope())) {
                return new MatchTierResult(0, false);
            }

            String trimmedQuery = scoped.term().toLowerCase(Locale.ROOT).trim();
            if (trimmedQuery.isEmpty()) {
                // "water:" with no term: every option appears
                return new MatchTierResult(scoped.hasScope() ? 1 : 0, false);
            }

            String readableTranslatedName = getReadableTranslatedName(optionId);
            String readableDefaultName = getReadableDefaultName(optionId);
            String rawId = optionId.toLowerCase(Locale.ROOT);
            String commentText = MinecraftLanguageAccess.getLowercaseString("option." + optionId + ".comment");

            String menuId = getLastPathSegment(path);
            String menuTranslatedName = resolveMenuName(menuId);
            String menuRawId = menuId != null ? menuId.toLowerCase(Locale.ROOT) : "";

            String[] tokens = WHITESPACE_PATTERN.split(trimmedQuery);
            if (tokens.length <= 1) {
                int best = computeQueryStringTier(optionId, trimmedQuery, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
                for (String synonym : resolveSynonyms(trimmedQuery)) {
                    int synonymScore = computeTokenTier(optionId, synonym, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
                    if (synonymScore > best) best = synonymScore;
                }
                if (best > 0) return new MatchTierResult(best, false);

                // Typo tolerance: last resort once literal and synonym matching both came up empty.
                boolean typo = typoMatchesAnyWord(trimmedQuery, readableTranslatedName) || typoMatchesAnyWord(trimmedQuery, readableDefaultName)
                        || typoMatchesAnyWord(trimmedQuery, menuTranslatedName);
                return new MatchTierResult(0, typo);
            }

            // Multi-word query: ALL tokens must match (AND-combined via bitwise AND).
            // OR'd with literal-phrase tier so full contiguous matches outrank scattered tokens.
            // Typo-only tokens contribute no bits to score, but satisfy the token match requirement.
            int andScore = -1;
            boolean anyTokenTypo = false;
            for (String token : tokens) {
                int tokenScore = computeTokenTier(optionId, token, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
                for (String synonym : resolveSynonyms(token)) {
                    int synonymScore = computeTokenTier(optionId, synonym, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
                    if (synonymScore > tokenScore) tokenScore = synonymScore;
                }
                if (tokenScore == 0) {
                    if (typoMatchesAnyWord(token, readableTranslatedName) || typoMatchesAnyWord(token, readableDefaultName)
                            || typoMatchesAnyWord(token, menuTranslatedName)) {
                        anyTokenTypo = true;
                        continue;
                    }
                    return new MatchTierResult(0, false);
                }
                andScore &= tokenScore;
            }
            int realAndScore = andScore == -1 ? 0 : andScore; // -1 means no token ever folded in (all were typo-only)
            int phraseScore = computeQueryStringTier(optionId, trimmedQuery, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
            return new MatchTierResult(phraseScore | realAndScore, anyTokenTypo);
        } catch (Exception e) {
            debugLog("computeMatchTier threw for query \"" + query + "\", treating as no match");
            return new MatchTierResult(0, false);
        }
    }

    /**
     * Resolves synonyms for a query token via exact or fuzzy key matching.
     * Allows partial words (e.g., "godray" for "godrays") and typos to expand to synonym groups.
     */
    private static Set<String> resolveSynonyms(String token) {
        Set<String> exact = SearchDictionaries.getSynonyms(token);
        if (!exact.isEmpty() || token.length() < MIN_FUZZY_SYNONYM_KEY_LENGTH) return exact;

        Set<String> fuzzy = new HashSet<>();
        for (String key : SearchDictionaries.getSynonymKeys()) {
            if (key.indexOf(' ') >= 0 || key.equals(token)) continue; // multi-word keys can't match a single token here
            if (key.startsWith(token) || isTypoMatch(token, key)) {
                fuzzy.add(key);
                fuzzy.addAll(SearchDictionaries.getSynonyms(key));
            }
        }
        return fuzzy;
    }

    private static int scanQueryStringTier(String optionId, String singleQuery, String readableTranslatedName,
                                            String readableDefaultName, String rawId, String commentText,
                                            String menuTranslatedName, String menuRawId) {
        String escapedQuery = Pattern.quote(singleQuery);
        Pattern wholeWordPat = Pattern.compile(String.format(WHOLE_WORD_REGEX, escapedQuery));
        Pattern startsWithPat = Pattern.compile(String.format(STARTS_WITH_REGEX, escapedQuery));

        // Translated name bits interleaved with default name bits (translated always one bit higher).
        // Default name bits sit above rawId/comment so en_us matches outrank ID/comment matches.
        // The containing-submenu bits sit at the very bottom: a menu-name match is the weakest signal,
        // only meant to surface options an on-topic match doesn't already cover.
        int score = 0;
        if (!readableTranslatedName.isEmpty() && readableTranslatedName.equals(singleQuery))           score |= (1 << 16);
        if (!readableDefaultName.isEmpty() && readableDefaultName.equals(singleQuery))                 score |= (1 << 15);
        if (!readableTranslatedName.isEmpty() && wholeWordPat.matcher(readableTranslatedName).find())  score |= (1 << 14);
        if (!readableDefaultName.isEmpty() && wholeWordPat.matcher(readableDefaultName).find())        score |= (1 << 13);
        if (!readableTranslatedName.isEmpty() && startsWithPat.matcher(readableTranslatedName).find()) score |= (1 << 12);
        if (!readableDefaultName.isEmpty() && startsWithPat.matcher(readableDefaultName).find())       score |= (1 << 11);
        if (wholeWordPat.matcher(rawId).find())                                                        score |= (1 << 10);
        if (!commentText.isEmpty() && wholeWordPat.matcher(commentText).find())                        score |= (1 << 9);
        if (startsWithPat.matcher(rawId).find())                                                       score |= (1 << 8);
        if (!commentText.isEmpty() && startsWithPat.matcher(commentText).find())                       score |= (1 << 7);
        if (!readableTranslatedName.isEmpty() && readableTranslatedName.contains(singleQuery))         score |= (1 << 6);
        if (!readableDefaultName.isEmpty() && readableDefaultName.contains(singleQuery))               score |= (1 << 5);
        if (rawId.contains(singleQuery))                                                               score |= (1 << 4);
        if (!commentText.isEmpty() && commentText.contains(singleQuery))                               score |= (1 << 3);
        if (IrisShaderPackTranslations.matchesOptionValueTranslation(optionId, singleQuery))           score |= (1 << 2); // value.<optionId>.<suffix>
        if (menuWholeWordMatches(menuTranslatedName, menuRawId, wholeWordPat))                         score |= (1 << 1);
        if (menuLooseMatches(menuTranslatedName, menuRawId, startsWithPat, singleQuery))               score |= 1;

        return score;
    }

    /** Whether the query is a whole word within the containing submenu's translated name or raw screen id. */
    private static boolean menuWholeWordMatches(String menuTranslatedName, String menuRawId, Pattern wholeWordPat) {
        return (!menuTranslatedName.isEmpty() && wholeWordPat.matcher(menuTranslatedName).find())
                || (!menuRawId.isEmpty() && wholeWordPat.matcher(menuRawId).find());
    }

    /** Whether the query starts a word in, or merely appears within, the containing submenu's translated name or raw screen id. */
    private static boolean menuLooseMatches(String menuTranslatedName, String menuRawId, Pattern startsWithPat, String singleQuery) {
        return (!menuTranslatedName.isEmpty() && (startsWithPat.matcher(menuTranslatedName).find() || menuTranslatedName.contains(singleQuery)))
                || (!menuRawId.isEmpty() && (startsWithPat.matcher(menuRawId).find() || menuRawId.contains(singleQuery)));
    }

    /** Computes the match tier of a single query string (one standalone token, or a full phrase) against an option's fields. */
    private static int computeQueryStringTier(String optionId, String singleQuery, String readableTranslatedName,
                                               String readableDefaultName, String rawId, String commentText,
                                               String menuTranslatedName, String menuRawId) {
        // 1 char Ascii query: only match if a readable name starts directly with the query
        // Only readableTranslatedName as that feels better
        if (singleQuery.length() == 1 && isOnlyAscii(singleQuery)) {
            return (!readableTranslatedName.isEmpty() && readableTranslatedName.startsWith(singleQuery)) ? 1 : 0;
        }

        return scanQueryStringTier(optionId, singleQuery, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
    }

    /**
     * Computes the match tier for a single token in a multi-word AND query.
     * Unlike {@link #computeQueryStringTier}, this bypasses the 1-character prefix restriction
     * so partial multi-word queries (e.g., "bloom s") match on word boundaries normally.
     */
    private static int computeTokenTier(String optionId, String token, String readableTranslatedName,
                                         String readableDefaultName, String rawId, String commentText,
                                         String menuTranslatedName, String menuRawId) {
        return scanQueryStringTier(optionId, token, readableTranslatedName, readableDefaultName, rawId, commentText, menuTranslatedName, menuRawId);
    }

    /** Whether any whitespace-separated word in {@code readableName} is a close-enough typo of {@code query}. */
    static boolean typoMatchesAnyWord(String query, String readableName) { // package-private for tests
        if (readableName == null || readableName.isEmpty()) return false;
        for (String word : readableName.split("\\s+")) {
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
        List<String> flatList = new ArrayList<>();
        if (optionIds == null) return flatList;

        for (String optionId : optionIds) {
            if (optionId != null && !flatList.contains(optionId)) {
                flatList.add(optionId);
            }
        }
        return flatList;
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
            String aWord = findMatchingWord(a.readableTranslatedName, q);
            if (aWord == null) aWord = findMatchingWord(a.readableDefaultName, q);
            String bWord = findMatchingWord(b.readableTranslatedName, q);
            if (bWord == null) bWord = findMatchingWord(b.readableDefaultName, q);
            int aLen = aWord != null ? aWord.length() : Integer.MAX_VALUE;
            int bLen = bWord != null ? bWord.length() : Integer.MAX_VALUE;
            return Integer.compare(aLen, bLen);
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

        private static String findMatchingWord(String readableName, String query) {
            if (readableName == null || query.isEmpty()) return null;
            for (String word : readableName.split("\\s+")) {
                if (word.startsWith(query)) return word;
            }
            return null;
        }

        private static int countWords(String s) {
            if (s == null || s.isBlank()) return 0;
            return s.trim().split("\\s+").length;
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
        return MinecraftLanguageAccess.getLowercaseString("option." + optionId);
    }

    /** Delegates to {@link IrisShaderPackTranslations} for the shader pack's own en_us default translation. */
    public static String getReadableDefaultName(String optionId) {
        return IrisShaderPackTranslations.getLowercaseDefaultTranslatedString("option." + optionId);
    }

    /** Delegates to {@link MinecraftLanguageAccess} for a "screen.xxx" translation, e.g. for breadcrumb labels. */
    public static String getDisplaySettingsName(String screenId) {
        return MinecraftLanguageAccess.getColorStrippedString("screen." + screenId);
    }

    /** Lowercase, search-ready counterpart of {@link #getDisplaySettingsName}, for matching a submenu name against a query. */
    private static String getReadableMenuName(String screenId) {
        if (screenId == null || screenId.isEmpty()) return "";
        return MinecraftLanguageAccess.getLowercaseString("screen." + screenId).replaceAll("\\s+>", "");
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
