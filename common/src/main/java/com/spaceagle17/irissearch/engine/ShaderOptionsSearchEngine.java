package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ShaderOptionsSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    /** Computes the match tier for a given option ID and query.
     * @param optionId The ID of the option to search for.
     * @param query The search query.
     * @return The match tier (higher is better).
     */
    public static int computeMatchTier(String optionId, String query) {
        try {
            if (optionId == null || query == null) return 0;

            String trimmedQuery = query.toLowerCase(Locale.ROOT).trim();
            if (trimmedQuery.isEmpty()) return 0;

            String readableTranslatedName = getReadableTranslatedName(optionId);
            String readableDefaultName = getReadableDefaultName(optionId);
            String rawId = optionId.toLowerCase(Locale.ROOT);
            String commentText = MinecraftLanguageAccess.getLowercaseString("option." + optionId + ".comment");

            String[] tokens = WHITESPACE_PATTERN.split(trimmedQuery);
            if (tokens.length <= 1) {
                int best = computeQueryStringTier(optionId, trimmedQuery, readableTranslatedName, readableDefaultName, rawId, commentText);
                for (String synonym : SearchDictionaries.getSynonyms(trimmedQuery)) {
                    int synonymScore = computeTokenTier(optionId, synonym, readableTranslatedName, readableDefaultName, rawId, commentText);
                    if (synonymScore > best) best = synonymScore;
                }
                return best;
            }

            // Multi-word query: every token must appear somewhere (AND), scored via bitwise-AND of each
            // token's tier so a bit only survives if *all* tokens satisfy that criterion. OR'd with the
            // literal-phrase tier so a contiguous match (e.g. "bloom strength") still outranks scattered tokens.
            int andScore = -1;
            for (String token : tokens) {
                int tokenScore = computeTokenTier(optionId, token, readableTranslatedName, readableDefaultName, rawId, commentText);
                for (String synonym : SearchDictionaries.getSynonyms(token)) {
                    int synonymScore = computeTokenTier(optionId, synonym, readableTranslatedName, readableDefaultName, rawId, commentText);
                    if (synonymScore > tokenScore) tokenScore = synonymScore;
                }
                if (tokenScore == 0) return 0;
                andScore &= tokenScore;
            }
            int phraseScore = computeQueryStringTier(optionId, trimmedQuery, readableTranslatedName, readableDefaultName, rawId, commentText);
            return phraseScore | andScore;
        } catch (Exception e) {
            debugLog("computeMatchTier threw for query \"" + query + "\", treating as no match");
            return 0;
        }
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

    public record ScoredOptionElement(String optionId, String readableTranslatedName, String readableDefaultName, String path, int score, String query) implements Comparable<ScoredOptionElement> {
        @Override
        public int compareTo(@NotNull ScoredOptionElement other) {
            String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
            int result;
            if ((result = compareByReadableNameQuality(this, other))  != 0) return result;
            if ((result = compareByMatchedWordLength(this, other, q)) != 0) return result;
            if ((result = compareByWordCount(this, other))            != 0) return result;
            if ((result = compareByFullScore(this, other))            != 0) return result;
            if ((result = compareByPrefixBoost(this, other, q))      != 0) return result;
            if ((result = compareByPathDepth(this, other))           != 0) return result;
            return compareByAlphabetical(this, other);
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
