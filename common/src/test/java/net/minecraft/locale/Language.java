package net.minecraft.locale;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test-only stand-in for vanilla Minecraft's {@code net.minecraft.locale.Language} class.
 * {@code MinecraftLanguageAccess} resolves this by fully-qualified name via reflection
 * ({@code Class.forName}), so this fake only needs to match the shapes it looks for
 * (static {@code getInstance()}, instance {@code has(String)}/{@code getOrDefault(String)},
 * and a static {@code languageCodes} field mirroring Iris's real mixin-injected one) --
 * it never needs to behave like the real class beyond that.
 *
 * <p>MUST stay under {@code src/test/java}. If this ever ended up in {@code src/main/java} it
 * would compile into the mod jar and shadow the real Minecraft class at runtime in-game.
 */
public class Language {
    private static final Language INSTANCE = new Language();
    private static final Map<String, String> TRANSLATIONS = new HashMap<>();

    // Read by IrisShaderPackTranslations via reflection to determine active languages.
    public static List<String> languageCodes = List.of("en_us");

    public static Language getInstance() {
        return INSTANCE;
    }

    public boolean has(String key) {
        return TRANSLATIONS.containsKey(key);
    }

    public String getOrDefault(String key) {
        return TRANSLATIONS.getOrDefault(key, key);
    }

    /** Test hook: merges entries into the active language's translation table. */
    public static void addTranslations(Map<String, String> translations) {
        TRANSLATIONS.putAll(translations);
    }

    /** Test hook: clears state back to defaults between tests. */
    public static void reset() {
        TRANSLATIONS.clear();
        languageCodes = List.of("en_us");
    }
}
