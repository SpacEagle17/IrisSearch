package com.spaceagle17.irissearch;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class ShaderSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
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

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderSearchEngine] " + message);
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
        if (irisReflectionFailed) return "";
        try {
            @SuppressWarnings("unchecked")
            Optional<Object> optionalPack = (Optional<Object>) getCurrentPackMethod.invoke(null);
            if (optionalPack.isEmpty()) return "";
            Object pack = optionalPack.get();
            Object languageMap = getLanguageMapMethod.invoke(pack);
            @SuppressWarnings("unchecked")
            Map<String, String> translations = (Map<String, String>) getTranslationsMethod.invoke(languageMap, "en_us");
            if (translations == null) return "";
            String value = translations.get(key);
            if (value == null) return "";
            String stripped = COLOR_CODE_PATTERN.matcher(value).replaceAll("");
            return stripped.toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return "";
        }
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
            if (!readableTranslatedName.isEmpty() && readableTranslatedName.equals(trimmedQuery))           score |= (1 << 13);
            if (!readableDefaultName.isEmpty() && readableDefaultName.equals(trimmedQuery))                 score |= (1 << 12);
            if (!readableTranslatedName.isEmpty() && wholeWordPat.matcher(readableTranslatedName).find())   score |= (1 << 11);
            if (!readableDefaultName.isEmpty() && wholeWordPat.matcher(readableDefaultName).find())         score |= (1 << 10);
            if (!readableTranslatedName.isEmpty() && startsWithPat.matcher(readableTranslatedName).find())  score |= (1 << 9);
            if (!readableDefaultName.isEmpty() && startsWithPat.matcher(readableDefaultName).find())        score |= (1 << 8);
            if (wholeWordPat.matcher(rawId).find())                                                         score |= (1 << 7);
            if (!commentText.isEmpty() && wholeWordPat.matcher(commentText).find())                         score |= (1 << 6);
            if (startsWithPat.matcher(rawId).find())                                                        score |= (1 << 5);
            if (!commentText.isEmpty() && startsWithPat.matcher(commentText).find())                        score |= (1 << 4);
            if (!readableTranslatedName.isEmpty() && readableTranslatedName.contains(trimmedQuery))         score |= (1 << 3);
            if (!readableDefaultName.isEmpty() && readableDefaultName.contains(trimmedQuery))               score |= (1 << 2);
            if (rawId.contains(trimmedQuery))                                                               score |= (1 << 1);
            if (!commentText.isEmpty() && commentText.contains(trimmedQuery))                               score |= (1);

            return score;
        } catch (Exception e) {
            debugLog("computeMatchTier threw for query \"" + query + "\", treating as no match");
            return 0;
        }
    }

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

    // Bitmask of score bits that come from either readable name (translated bits 13,11,9,3 and default bits 12,10,8,2).
    // Used to separate "how well does the readable name match" from comment/rawId noise.
    private static final int READABLE_NAME_BITS = (1 << 13) | (1 << 12) | (1 << 11) | (1 << 10) | (1 << 9) | (1 << 8) | (1 << 3) | (1 << 2);

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
}
