package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Cross-version reflective access to vanilla Minecraft's {@code Language} class.
 */
public final class MinecraftLanguageAccess {
    private MinecraftLanguageAccess() {}

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§.");

    private static Object languageInstance = null;
    private static Method hasMethod = null;
    private static Method getOrDefaultMethod = null;
    private static boolean reflectionFailed = false;

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

    /** Whether the vanilla Language reflection handles resolved successfully. */
    public static boolean isAvailable() {
        return !reflectionFailed;
    }

    /**
     * Returns the raw (mixin-injected fields readable) Language instance, or null if reflection failed.
     */
    static Object getLanguageInstance() {
        return languageInstance;
    }

    /** Returns the color-stripped translation for {@code key}, in its original case, or "" if missing/unavailable. */
    public static String getColorStrippedString(String key) {
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

    /** Returns the color-stripped, lowercased translation for {@code key}, or "" if missing/unavailable. */
    public static String getLowercaseString(String key) {
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

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[MinecraftLanguageAccess] " + message);
    }
}
