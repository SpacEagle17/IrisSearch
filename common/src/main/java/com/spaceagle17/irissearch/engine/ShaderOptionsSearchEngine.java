package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class ShaderOptionsSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile("[-+]?\\d+(\\.\\d+)?");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");

    // Cached Reflection Targets - Minecraft Language
    private static Object languageInstance = null;
    private static Method hasMethod = null;
    private static Method getOrDefaultMethod = null;
    private static boolean reflectionFailed = false;

    // Cached Reflection Targets - Iris LanguageMap (for en_us default translations)
    private static Method getCurrentPackMethod = null;
    private static Method getLanguageMapMethod = null;
    private static Method getTranslationsMethod = null;
    private static boolean irisReflectionFailed = false;

    // Cached Iris LanguageMap Reflection State
    private static boolean irisLanguageCodesFieldLookupFailed = false;
    private static Field irisLanguageCodesField = null;
    private static Object cachedIrisPackRef = null;

    // Per-language-code sorted translation snapshots for value.<optionId>.<suffix> prefix scans
    // keyed by language code (e.g. "en_us", "pl_pl"). Cleared whenever cachedIrisPackRef changes.
    private static final Map<String, NavigableMap<String, String>> irisValueTranslationsByCode = new ConcurrentHashMap<>();
    private static final NavigableMap<String, String> EMPTY_SORTED_MAP = new TreeMap<>();

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

            // 1 char Ascii query: only match if a readable name starts directly with the query
            // Only readableTranslatedName as that feels better
            if (trimmedQuery.length() == 1 && isOnlyAscii(trimmedQuery)) {
                return (!readableTranslatedName.isEmpty() && readableTranslatedName.startsWith(trimmedQuery)) ? 1 : 0;
            }

            String escapedQuery = Pattern.quote(trimmedQuery);
            Pattern wholeWordPat = Pattern.compile(String.format(WHOLE_WORD_REGEX, escapedQuery));
            Pattern startsWithPat = Pattern.compile(String.format(STARTS_WITH_REGEX, escapedQuery));

            String rawId = optionId.toLowerCase(Locale.ROOT);
            String commentText = getLowercaseTranslatedString("option." + optionId + ".comment");

            // Translated name bits interleaved with default name bits (translated always one bit higher).
            // Default name bits sit above rawId/comment so en_us matches outrank ID/comment matches.
            int score = 0;
            if (!readableTranslatedName.isEmpty() && readableTranslatedName.equals(trimmedQuery))           score |= (1 << 14);
            if (!readableDefaultName.isEmpty() && readableDefaultName.equals(trimmedQuery))                 score |= (1 << 13);
            if (!readableTranslatedName.isEmpty() && wholeWordPat.matcher(readableTranslatedName).find())   score |= (1 << 12);
            if (!readableDefaultName.isEmpty() && wholeWordPat.matcher(readableDefaultName).find())         score |= (1 << 11);
            if (!readableTranslatedName.isEmpty() && startsWithPat.matcher(readableTranslatedName).find())  score |= (1 << 10);
            if (!readableDefaultName.isEmpty() && startsWithPat.matcher(readableDefaultName).find())        score |= (1 << 9);
            if (wholeWordPat.matcher(rawId).find())                                                         score |= (1 << 8);
            if (!commentText.isEmpty() && wholeWordPat.matcher(commentText).find())                         score |= (1 << 7);
            if (startsWithPat.matcher(rawId).find())                                                        score |= (1 << 6);
            if (!commentText.isEmpty() && startsWithPat.matcher(commentText).find())                        score |= (1 << 5);
            if (!readableTranslatedName.isEmpty() && readableTranslatedName.contains(trimmedQuery))         score |= (1 << 4);
            if (!readableDefaultName.isEmpty() && readableDefaultName.contains(trimmedQuery))               score |= (1 << 3);
            if (rawId.contains(trimmedQuery))                                                               score |= (1 << 2);
            if (!commentText.isEmpty() && commentText.contains(trimmedQuery))                               score |= (1 << 1);
            if (matchesOptionValueTranslation(optionId, trimmedQuery)) /* value.<optionId>.<suffix> */      score |= (1);

            return score;
        } catch (Exception e) {
            debugLog("computeMatchTier threw for query \"" + query + "\", treating as no match");
            return 0;
        }
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

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderOptionsSearchEngine] " + message);
    }

    static {
        // Minecraft Language reflection
        try {
            // 1. Resolve Class
            Class<?> languageClass = null;
            for (String name : new String[]{"net.minecraft.locale.Language", "net.minecraft.class_2477", "net.minecraft.src.C_4907_"}) {
                try {
                    languageClass = Class.forName(name);
                    debugLog("Successfully resolved Language class: " + name);
                    break;
                } catch (ClassNotFoundException ignored) {
                    debugLog("Failed to find Language class with name \"" + name + "\"");
                }
            }

            if (languageClass != null) {
                // 2. Resolve Instance
                for (String name : new String[]{"getInstance", "method_10517", "m_128107_"}) {
                    try {
                        languageInstance = ReflectionUtils.invokeMethod(languageClass, name, new Class<?>[]{});
                        if (languageInstance != null) {
                            debugLog("Successfully retrieved Language instance via: " + name + "()");
                            break;
                        }
                    } catch (Throwable ignored) {
                        debugLog("Failed to find method \"" + name + "\" on Language class");
                    }
                }

                // 3. Resolve Methods
                if (languageInstance != null) {
                    for (String name : new String[]{"has", "method_4678", "m_6722_"}) {
                        try {
                            hasMethod = languageClass.getMethod(name, String.class);
                            debugLog("Successfully mapped hasMethod via: " + name + "(String)");
                            break;
                        } catch (NoSuchMethodException ignored) {
                            debugLog("Failed to find method \"" + name + "\" on Language class");
                        }
                    }
                    for (String name : new String[]{"getOrDefault", "method_48307", "method_4679", "m_6834_", "m_118919_"}) {
                        try {
                            getOrDefaultMethod = languageClass.getMethod(name, String.class);
                            debugLog("Successfully mapped getOrDefaultMethod via: " + name + "(String)");
                            break;
                        } catch (NoSuchMethodException ignored) {
                            debugLog("Failed to find method \"" + name + "\" on Language class");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            IrisSearchLogger.debugLog("Static reflection initialization failed: " + t);
        }

        if (languageInstance == null || hasMethod == null || getOrDefaultMethod == null) {
            reflectionFailed = true;
            IrisSearch.log(3, "Game translation mapping failed. Search fallback to raw IDs active.");
        } else {
            debugLog("Reflection setup completed successfully. All translation handles cached.");
        }

        // Iris LanguageMap reflection (for en_us default translations)
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Class<?> shaderPackClass = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack");
            Class<?> languageMapClass = Class.forName("net.irisshaders.iris.shaderpack.LanguageMap");

            getCurrentPackMethod = irisClass.getMethod("getCurrentPack");
            getLanguageMapMethod = shaderPackClass.getMethod("getLanguageMap");
            getTranslationsMethod = languageMapClass.getMethod("getTranslations", String.class);

            debugLog("Iris LanguageMap reflection setup completed.");
        } catch (Throwable t) {
            irisReflectionFailed = true;
            debugLog("Iris LanguageMap reflection setup failed: " + t);
        }
    }

    private static String getColorStrippedString(String key) {
        if (reflectionFailed) return "";
        try {
            boolean hasKey = (boolean) hasMethod.invoke(languageInstance, key);
            if (!hasKey) return "";
            Object result = getOrDefaultMethod.invoke(languageInstance, key);
            if (!(result instanceof String)) return "";
            return COLOR_CODE_PATTERN.matcher((String) result).replaceAll("");
        } catch (Throwable t) {
            return "";
        }
    }

    public static String getDisplaySettingsName(String screenId) {
        return getColorStrippedString("screen." + screenId);
    }

    private static String getLowercaseTranslatedString(String key) {
        if (reflectionFailed) return "";
        try {
            boolean hasKey = (boolean) hasMethod.invoke(languageInstance, key);
            if (!hasKey) return "";

            Object result = getOrDefaultMethod.invoke(languageInstance, key);
            if (!(result instanceof String)) return "";
            String stripped = COLOR_CODE_PATTERN.matcher((String) result).replaceAll("");
            return stripped.toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String getLowercaseDefaultTranslatedString(String key) {
        refreshIrisPackIfNeeded();
        Map<String, String> translations = getIrisValueTranslationsForCode("en_us");
        String value = translations.get(key);
        if (value == null) return "";
        String stripped = COLOR_CODE_PATTERN.matcher(value).replaceAll("");
        return stripped.toLowerCase(Locale.ROOT);
    }

    /**
     * Refreshes {@link #cachedIrisPackRef} and clears the per-language-code translation cache if the currently
     * loaded Iris shader pack has changed since the last call. Does nothing if the pack is unchanged
     */
    private static void refreshIrisPackIfNeeded() {
        if (irisReflectionFailed) return;
        try {
            @SuppressWarnings("unchecked")
            Optional<Object> optionalPack = (Optional<Object>) getCurrentPackMethod.invoke(null);
            Object pack = optionalPack.orElse(null);

            if (pack == cachedIrisPackRef) return;

            cachedIrisPackRef = pack;
            irisValueTranslationsByCode.clear();
        } catch (Throwable t) {
            cachedIrisPackRef = null;
            irisValueTranslationsByCode.clear();
        }
    }

    /**
     * Retrieves the list of currently active language codes from Iris's mixin-injected {@code languageCodes} field.
     *  Falls back to {@code List.of("en_us")} if the field is not present or cannot be read.
     */
    private static List<String> getActiveIrisLanguageCodes() {
        List<String> fallback = List.of("en_us");
        if (irisLanguageCodesFieldLookupFailed || languageInstance == null) return fallback;

        try {
            if (irisLanguageCodesField == null) {
                irisLanguageCodesField = ReflectionUtils.getField(languageInstance, "languageCodes");
                if (irisLanguageCodesField == null) {
                    irisLanguageCodesFieldLookupFailed = true;
                    debugLog("Failed to locate Iris's languageCodes field, falling back to " + fallback + " permanently.");
                    return fallback;
                }
                debugLog("Located Iris's mixin-injected \"languageCodes\" field on " + languageInstance.getClass().getName() + ".");
            }

            Object value = irisLanguageCodesField.get(null);
            if (value instanceof List<?> rawList && !rawList.isEmpty()) {
                List<String> codes = new ArrayList<>();
                for (Object o : rawList) {
                    if (o instanceof String s) codes.add(s);
                }
                if (!codes.isEmpty()) return codes;
            }
        } catch (Throwable t) {
            irisLanguageCodesFieldLookupFailed = true;
            debugLog("Failed to read Iris's languageCodes field, falling back to " + fallback + " permanently: " + t);
        }

        return fallback;
    }

    /**
     * Retrieves a sorted map of value translations for the given language code, or an empty map if the code cannot be resolved.
     * Caches the result per language code.
     */
    private static NavigableMap<String, String> getIrisValueTranslationsForCode(String code) {
        if (irisReflectionFailed || cachedIrisPackRef == null) return EMPTY_SORTED_MAP;

        NavigableMap<String, String> cached = irisValueTranslationsByCode.get(code);
        if (cached != null) return cached;

        NavigableMap<String, String> sorted = EMPTY_SORTED_MAP;
        try {
            Object languageMapObj = getLanguageMapMethod.invoke(cachedIrisPackRef);
            @SuppressWarnings("unchecked")
            Map<String, String> translations = (Map<String, String>) getTranslationsMethod.invoke(languageMapObj, code);
            if (translations != null) sorted = new TreeMap<>(translations);
        } catch (Throwable ignored) {
            // fall through - cache the empty sentinel below so this code isn't retried every call
        }

        irisValueTranslationsByCode.put(code, sorted);
        return sorted;
    }

    /**
     * Checks if the given query matches any of the value translations for the specified option ID.
     */
    private static boolean matchesOptionValueTranslation(String optionId, String trimmedQuery) {
        String prefix = "value." + optionId + ".";
        refreshIrisPackIfNeeded();

        for (String code : getActiveIrisLanguageCodes()) {
            NavigableMap<String, String> map = getIrisValueTranslationsForCode(code);
            if (!map.isEmpty() && matchesPrefixRange(map, prefix, trimmedQuery)) return true;
        }
        return false;
    }

    /**
     * Checks if any entries in the sorted map with keys starting with the given prefix contain the trimmed query.
     */
    private static boolean matchesPrefixRange(NavigableMap<String, String> sortedMap, String prefix, String trimmedQuery) {
        for (Map.Entry<String, String> entry : sortedMap.tailMap(prefix, true).entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) break;

            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.isEmpty()) continue;

            String stripped = COLOR_CODE_PATTERN.matcher(rawValue).replaceAll("").trim();
            if (stripped.isEmpty() || NUMERIC_VALUE_PATTERN.matcher(stripped).matches()) continue;

            if (stripped.toLowerCase(Locale.ROOT).contains(trimmedQuery)) return true;
        }
        return false;
    }

    private static boolean isOnlyAscii(String string) {
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) > 127) return false;
        }
        return true;
    }

    public static String getReadableTranslatedName(String optionId) {
        return getLowercaseTranslatedString("option." + optionId);
    }

    public static String getReadableDefaultName(String optionId) {
        return getLowercaseDefaultTranslatedString("option." + optionId);
    }
}
