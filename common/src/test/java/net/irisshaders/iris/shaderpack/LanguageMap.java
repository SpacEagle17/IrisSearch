package net.irisshaders.iris.shaderpack;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-only stand-in for Iris's {@code net.irisshaders.iris.shaderpack.LanguageMap} class.
 * {@code IrisShaderPackTranslations} resolves this by fully-qualified name via reflection, so this
 * fake only needs an instance {@code getTranslations(String languageCode)}. MUST stay under
 * {@code src/test/java}.
 */
public class LanguageMap {
    private final Map<String, Map<String, String>> translationsByCode = new HashMap<>();

    /** Test hook: seeds the translation table for one language code (e.g. "en_us"). */
    public void put(String languageCode, Map<String, String> translations) {
        translationsByCode.put(languageCode, translations);
    }

    public Map<String, String> getTranslations(String languageCode) {
        return translationsByCode.getOrDefault(languageCode, Map.of());
    }
}
