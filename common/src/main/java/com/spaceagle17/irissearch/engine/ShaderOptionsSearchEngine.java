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
     * Computes the match tier for an option ID against a query.
     *
     * @param optionId Option ID to search.
     * @param query Search string.
     * @return Match result with score and typo status.
     */
    public static MatchTierResult computeMatchTier(String optionId, String query) {
        try {
            if (optionId == null || query == null) return new MatchTierResult(0, false);

            String trimmedQuery = query.toLowerCase(Locale.ROOT).trim();
            if (trimmedQuery.isEmpty()) return new MatchTierResult(0, false);

            String readableTranslatedName = getReadableTranslatedName(optionId);
            String readableDefaultName = getReadableDefaultName(optionId);
            String rawId = optionId.toLowerCase(Locale.ROOT);
            String commentText = MinecraftLanguageAccess.getLowercaseString("option." + optionId + ".comment");

            String[] tokens = WHITESPACE_PATTERN.split(trimmedQuery);
            if (tokens.length <= 1) {
                int best = computeQueryStringTier(optionId, trimmedQuery, readableTranslatedName, readableDefaultName, rawId, commentText);
                for (String synonym : resolveSynonyms(trimmedQuery)) {
                    int synonymScore = computeTokenTier(optionId, synonym, readableTranslatedName, readableDefaultName, rawId, commentText);
                    if (synonymScore > best) best = synonymScore;
                }
                if (best > 0) return new MatchTierResult(best, false);

                // Typo tolerance: last resort once literal and synonym matching both came up empty.
                boolean typo = typoMatchesAnyWord(trimmedQuery, readableTranslatedName) || typoMatchesAnyWord(trimmedQuery, readableDefaultName);
                return new MatchTierResult(0, typo);
            }

            // Multi-word query: ALL tokens must match (AND-combined via bitwise AND).
            // OR'd with literal-phrase tier so full contiguous matches outrank scattered tokens.
            // Typo-only tokens contribute no bits to score, but satisfy the token match requirement.
            int andScore = -1;
            boolean anyTokenTypo = false;
            for (String token : tokens) {
                int tokenScore = computeTokenTier(optionId, token, readableTranslatedName, readableDefaultName, rawId, commentText);
                for (String synonym : resolveSynonyms(token)) {
                    int synonymScore = computeTokenTier(optionId, synonym, readableTranslatedName, readableDefaultName, rawId, commentText);
                    if (synonymScore > tokenScore) tokenScore = synonymScore;
                }
                if (tokenScore == 0) {
                    if (typoMatchesAnyWord(token, readableTranslatedName) || typoMatchesAnyWord(token, readableDefaultName)) {
                        anyTokenTypo = true;
                        continue;
                    }
                    return new MatchTierResult(0, false);
                }
                andScore &= tokenScore;
            }
            int realAndScore = andScore == -1 ? 0 : andScore; // -1 means no token ever folded in (all were typo-only)
            int phraseScore = computeQueryStringTier(optionId, trimmedQuery, readableTranslatedName, readableDefaultName, rawId, commentText);
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
                                            String readableDefaultName, String rawId, String commentText) {
        String escapedQuery = Pattern.quote(singleQuery);
        Pattern wholeWordPat = Pattern.compile(String.format(WHOLE_WORD_REGEX, escapedQuery));
        Pattern startsWithPat = Pattern.compile(String.format(STARTS_WITH_REGEX, escapedQuery));

        // Translated name bits interleaved with default name bits (translated always one bit higher).
        // Default name bits sit above rawId/comment so en_us matches outrank ID/comment matches.
        int score = 0;
        if (!readableTranslatedName.isEmpty() && readableTranslatedName.equals(singleQuery))           score |= (1 << 14);
        if (!readableDefaultName.isEmpty() && readableDefaultName.equals(singleQuery))                 score |= (1 << 13);
        if (!readableTranslatedName.isEmpty() && wholeWordPat.matcher(readableTranslatedName).find())  score |= (1 << 12);
        if (!readableDefaultName.isEmpty() && wholeWordPat.matcher(readableDefaultName).find())        score |= (1 << 11);
        if (!readableTranslatedName.isEmpty() && startsWithPat.matcher(readableTranslatedName).find()) score |= (1 << 10);
        if (!readableDefaultName.isEmpty() && startsWithPat.matcher(readableDefaultName).find())       score |= (1 << 9);
        if (wholeWordPat.matcher(rawId).find())                                                        score |= (1 << 8);
        if (!commentText.isEmpty() && wholeWordPat.matcher(commentText).find())                        score |= (1 << 7);
        if (startsWithPat.matcher(rawId).find())                                                       score |= (1 << 6);
        if (!commentText.isEmpty() && startsWithPat.matcher(commentText).find())                       score |= (1 << 5);
        if (!readableTranslatedName.isEmpty() && readableTranslatedName.contains(singleQuery))         score |= (1 << 4);
        if (!readableDefaultName.isEmpty() && readableDefaultName.contains(singleQuery))               score |= (1 << 3);
        if (rawId.contains(singleQuery))                                                               score |= (1 << 2);
        if (!commentText.isEmpty() && commentText.contains(singleQuery))                               score |= (1 << 1);
        if (IrisShaderPackTranslations.matchesOptionValueTranslation(optionId, singleQuery))           score |= (1); // value.<optionId>.<suffix>

        return score;
    }

    /** Computes the match tier of a single query string (one standalone token, or a full phrase) against an option's fields. */
    private static int computeQueryStringTier(String optionId, String singleQuery, String readableTranslatedName,
                                               String readableDefaultName, String rawId, String commentText) {
        // 1 char Ascii query: only match if a readable name starts directly with the query
        // Only readableTranslatedName as that feels better
        if (singleQuery.length() == 1 && isOnlyAscii(singleQuery)) {
            return (!readableTranslatedName.isEmpty() && readableTranslatedName.startsWith(singleQuery)) ? 1 : 0;
        }

        return scanQueryStringTier(optionId, singleQuery, readableTranslatedName, readableDefaultName, rawId, commentText);
    }

    /**
     * Computes the match tier for a single token in a multi-word AND query.
     * Unlike {@link #computeQueryStringTier}, this bypasses the 1-character prefix restriction
     * so partial multi-word queries (e.g., "bloom s") match on word boundaries normally.
     */
    private static int computeTokenTier(String optionId, String token, String readableTranslatedName,
                                         String readableDefaultName, String rawId, String commentText) {
        return scanQueryStringTier(optionId, token, readableTranslatedName, readableDefaultName, rawId, commentText);
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

    // Bitmask of score bits that come from either readable name (translated bits 14,12,10,4 and default bits 13,11,9,3).
    // Used to separate "how well does the readable name match" from comment/rawId/value noise.
    private static final int READABLE_NAME_BITS = (1 << 14) | (1 << 13) | (1 << 12) | (1 << 11) | (1 << 10) | (1 << 9) | (1 << 4) | (1 << 3);

    public record ScoredOptionElement(String optionId, String readableTranslatedName, String readableDefaultName, String path, int score, boolean typo, String query) implements Comparable<ScoredOptionElement> {
        @Override
        public int compareTo(@NotNull ScoredOptionElement other) {
            String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
            int result;
            if ((result = compareByTypoPenalty(this, other))          != 0) return result;
            if ((result = compareByReadableNameQuality(this, other))  != 0) return result;
            if ((result = compareByMatchedWordLength(this, other, q)) != 0) return result;
            if ((result = compareByWordCount(this, other))            != 0) return result;
            if ((result = compareByFullScore(this, other))            != 0) return result;
            if ((result = compareByPrefixBoost(this, other, q))      != 0) return result;
            if ((result = compareByPathDepth(this, other))           != 0) return result;
            return compareByAlphabetical(this, other);
        }

        // 0. Typo-tolerant matches always rank below any real match.
        private static int compareByTypoPenalty(ScoredOptionElement a, ScoredOptionElement b) {
            if (a.typo == b.typo) return 0;
            return a.typo ? 1 : -1;
        }

        // 1. Readable-name match quality (translated + default name bits only).
        private static int compareByReadableNameQuality(ScoredOptionElement a, ScoredOptionElement b) {
            int aReadable = a.score & READABLE_NAME_BITS;
            int bReadable = b.score & READABLE_NAME_BITS;
            return Integer.compare(bReadable, aReadable);
        }

        // 2. Sort by matched-word length (shorter word = higher query coverage = more relevant).
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

        // 3. Fewer words in readable name = more precise match.
        // "Bloom" beats "Bloom Strength" when matched word length ties.
        // Use translated name if available, otherwise fall back to default.
        private static int compareByWordCount(ScoredOptionElement a, ScoredOptionElement b) {
            String aEffective = !a.readableTranslatedName.isEmpty() ? a.readableTranslatedName : a.readableDefaultName;
            String bEffective = !b.readableTranslatedName.isEmpty() ? b.readableTranslatedName : b.readableDefaultName;
            return Integer.compare(countWords(aEffective), countWords(bEffective));
        }

        // 4. Full score (comment/rawId matches as secondary signal).
        private static int compareByFullScore(ScoredOptionElement a, ScoredOptionElement b) {
            return Integer.compare(b.score, a.score);
        }

        // 5. Prefix boost: either readable name starts with the exact query string.
        private static int compareByPrefixBoost(ScoredOptionElement a, ScoredOptionElement b, String q) {
            if (q.isEmpty()) return 0;
            boolean aPrefixes = (a.readableTranslatedName != null && a.readableTranslatedName.startsWith(q))
                    || (a.readableDefaultName    != null && a.readableDefaultName.startsWith(q));
            boolean bPrefixes = (b.readableTranslatedName != null && b.readableTranslatedName.startsWith(q))
                    || (b.readableDefaultName    != null && b.readableDefaultName.startsWith(q));
            if (aPrefixes == bPrefixes) return 0;
            return aPrefixes ? -1 : 1;
        }

        // 6. Path depth: fewer slashes (shallower) wins.
        private static int compareByPathDepth(ScoredOptionElement a, ScoredOptionElement b) {
            return Integer.compare(countSlashes(a.path), countSlashes(b.path));
        }

        // 7. Alphabetical tie-breaker.
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

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderOptionsSearchEngine] " + message);
    }
}
