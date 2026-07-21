package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;

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

/**
 * Reflective accessor for Iris shader pack language maps and option value translations.
 */
public final class IrisShaderPackTranslations {
    private IrisShaderPackTranslations() {}

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");
    private static final Pattern NUMERIC_VALUE_PATTERN = Pattern.compile("[-+]?\\d+(\\.\\d+)?");

    private static Method getCurrentPackMethod = null;
    private static Method getCurrentPackNameMethod = null;
    private static Method getLanguageMapMethod = null;
    private static Method getTranslationsMethod = null;
    private static boolean irisReflectionFailed = false;

    private static boolean irisLanguageCodesFieldLookupFailed = false;
    private static Field irisLanguageCodesField = null;
    private static Object cachedIrisPackRef = null;

    // Per-language-code sorted translation snapshots for value.<optionId>.<suffix> prefix scans
    // keyed by language code (e.g. "en_us", "pl_pl"). Cleared whenever cachedIrisPackRef changes.
    private static final Map<String, NavigableMap<String, String>> irisValueTranslationsByCode = new ConcurrentHashMap<>();
    private static final NavigableMap<String, String> EMPTY_SORTED_MAP = new TreeMap<>();

    static {
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            Class<?> shaderPackClass = Class.forName("net.irisshaders.iris.shaderpack.ShaderPack");
            Class<?> languageMapClass = Class.forName("net.irisshaders.iris.shaderpack.LanguageMap");

            getCurrentPackMethod = irisClass.getMethod("getCurrentPack");
            getCurrentPackNameMethod = irisClass.getMethod("getCurrentPackName");
            getLanguageMapMethod = shaderPackClass.getMethod("getLanguageMap");
            getTranslationsMethod = languageMapClass.getMethod("getTranslations", String.class);

            debugLog("Iris LanguageMap reflection setup completed.");
        } catch (Throwable t) {
            irisReflectionFailed = true;
            debugLog("Iris LanguageMap reflection setup failed: " + t);
        }
    }

    /** Returns the name of the currently loaded shader pack name */
    public static String getCurrentPackName() {
        if (irisReflectionFailed) return null;
        try {
            Object result = getCurrentPackNameMethod.invoke(null);
            return result instanceof String ? (String) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Returns the color-stripped, lowercased en_us default translation for {@code key}, or "" if unavailable. */
    public static String getLowercaseDefaultTranslatedString(String key) {
        refreshIrisPackIfNeeded();
        Map<String, String> translations = getIrisValueTranslationsForCode("en_us");
        String value = translations.get(key);
        if (value == null) return "";
        String stripped = COLOR_CODE_PATTERN.matcher(value).replaceAll("");
        return stripped.toLowerCase(Locale.ROOT);
    }

    /** Checks if the given query matches any of the value translations for the specified option ID.
     * <code>value.optionId.suffix</code>
     */
    public static boolean matchesOptionValueTranslation(String optionId, String trimmedQuery) {
        String prefix = "value." + optionId + ".";
        refreshIrisPackIfNeeded();

        for (String code : getActiveIrisLanguageCodes()) {
            NavigableMap<String, String> map = getIrisValueTranslationsForCode(code);
            if (!map.isEmpty() && matchesPrefixRange(map, prefix, trimmedQuery)) return true;
        }
        return false;
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
        Object languageInstance = MinecraftLanguageAccess.getLanguageInstance();
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

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[IrisShaderPackTranslations] " + message);
    }
}
