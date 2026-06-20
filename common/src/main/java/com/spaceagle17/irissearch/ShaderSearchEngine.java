package com.spaceagle17.irissearch;

import com.spaceagle17.irissearch.logging.IrisSearchLogger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class ShaderSearchEngine {
    private static final String WHOLE_WORD_REGEX = "(?<=^|[^a-zA-Z0-9])%s(?=$|[^a-zA-Z0-9])";
    private static final String STARTS_WITH_REGEX = "(?<=^|[^a-zA-Z0-9])%s";
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");

    // Cached Reflection Targets
    private static Object languageInstance = null;
    private static Method hasMethod = null;
    private static Method getOrDefaultMethod = null;
    private static boolean reflectionFailed = false;

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderSearchEngine] " + message);
    }

    static {
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

    private static boolean isOnlyAscii(String string) {
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) > 127) return false;
        }
        return true;
    }

    public static String getReadableName(String optionId) {
        return getLowercaseTranslatedString("option." + optionId);
    }

    public static int computeMatchTier(String optionId, String query) {
        try {
            if (optionId == null || query == null) return 0;

            String trimmedQuery = query.toLowerCase(Locale.ROOT).trim();
            if (trimmedQuery.isEmpty()) return 0;

            String readableName = getLowercaseTranslatedString("option." + optionId);

            // 1 char Ascii query: only match if the readable name starts directly with the query
            if (trimmedQuery.length() == 1 && isOnlyAscii(trimmedQuery)) {
                return (!readableName.isEmpty() && readableName.startsWith(trimmedQuery)) ? 1 : 0;
            }

            String escapedQuery = Pattern.quote(trimmedQuery);
            Pattern wholeWordPat = Pattern.compile(String.format(WHOLE_WORD_REGEX, escapedQuery));
            Pattern startsWithPat = Pattern.compile(String.format(STARTS_WITH_REGEX, escapedQuery));

            String rawId = optionId.toLowerCase(Locale.ROOT);
            String commentText = getLowercaseTranslatedString("option." + optionId + ".comment");

            int score = 0;
            if (!readableName.isEmpty() && wholeWordPat.matcher(readableName).find())   score |= (1 << 8);
            if (wholeWordPat.matcher(rawId).find())                                     score |= (1 << 7);
            if (!commentText.isEmpty() && wholeWordPat.matcher(commentText).find())     score |= (1 << 6);
            if (!readableName.isEmpty() && startsWithPat.matcher(readableName).find())  score |= (1 << 5);
            if (startsWithPat.matcher(rawId).find())                                    score |= (1 << 4);
            if (!commentText.isEmpty() && startsWithPat.matcher(commentText).find())    score |= (1 << 3);
            if (!readableName.isEmpty() && readableName.contains(trimmedQuery))         score |= (1 << 2);
            if (rawId.contains(trimmedQuery))                                           score |= (1 << 1);
            if (!commentText.isEmpty() && commentText.contains(trimmedQuery))           score |= (1);

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

    public record ScoredOptionElement(String optionId, String readableName, String path, int score, String query) implements Comparable<ScoredOptionElement> {
        @Override
        public int compareTo(ScoredOptionElement other) {
            // 1. Higher bitmask score wins (descending)
            if (this.score != other.score) {
                return Integer.compare(other.score, this.score);
            }
            // 2. Absolute prefix boost: readableName starts with the query
            String q = this.query != null ? this.query.toLowerCase(Locale.ROOT).trim() : "";
            boolean thisPrefixes = !q.isEmpty() && this.readableName != null && this.readableName.startsWith(q);
            boolean otherPrefixes = !q.isEmpty() && other.readableName != null && other.readableName.startsWith(q);
            if (thisPrefixes != otherPrefixes) {
                return thisPrefixes ? -1 : 1;
            }
            // 3. Path depth: fewer slashes (shallower) wins
            int thisDepth = countSlashes(this.path);
            int otherDepth = countSlashes(other.path);
            if (thisDepth != otherDepth) {
                return Integer.compare(thisDepth, otherDepth);
            }
            // 4. Alphabetical tie-breaker
            if (this.optionId != null && other.optionId != null) {
                return this.optionId.compareTo(other.optionId);
            }
            return 0;
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
