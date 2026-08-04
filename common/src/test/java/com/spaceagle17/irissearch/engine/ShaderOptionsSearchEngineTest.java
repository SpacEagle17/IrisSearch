package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.LanguageMap;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.minecraft.locale.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ShaderOptionsSearchEngine} using mock Minecraft/Iris classes in this test source set
 * to satisfy reflection calls without full runtime dependencies.
 * Tests without {@link #configureOption} exercise the raw-ID-only fallback path.
 */
class ShaderOptionsSearchEngineTest {

    @BeforeAll
    static void setUpModLoader() {
        // IrisSearch's static init requires ModLoaderSpecifics to already have an instance;
        // reached if the fake translation classes are ever missing/mismatched and a real failure
        // gets logged.
        ModLoaderSpecifics.setInstance(new ModLoaderSpecifics() {
            public String getInstanceName() { return "Fabric"; }
            public Path getConfigDirectory() { return Paths.get("."); }
            public boolean serverCheck() { return false; }
        });
    }

    private final Map<String, String> enUsAccumulator = new HashMap<>();

    @BeforeEach
    void resetFakeRuntime() {
        Language.reset();
        Iris.reset();
        enUsAccumulator.clear();
    }

    /**
     * Configures mock vanilla and Iris translations for an option.
     * Null parameters are skipped; calls accumulate so options can be tested together.
     */
    private void configureOption(String optionId, String translatedName, String defaultName,
                                  String comment, Map<String, String> valueTranslations) {
        Map<String, String> vanilla = new HashMap<>();
        if (translatedName != null) vanilla.put("option." + optionId, translatedName);
        if (comment != null) vanilla.put("option." + optionId + ".comment", comment);
        Language.addTranslations(vanilla);

        if (defaultName != null) enUsAccumulator.put("option." + optionId, defaultName);
        if (valueTranslations != null) {
            valueTranslations.forEach((suffix, value) -> enUsAccumulator.put("value." + optionId + "." + suffix, value));
        }
        LanguageMap languageMap = new LanguageMap();
        languageMap.put("en_us", new HashMap<>(enUsAccumulator));
        Iris.setCurrentPack(new ShaderPack(languageMap));
    }

    // Mirrors the loader mixins' own match condition: found if there's real score evidence
    // OR the result was only reached via typo-tolerant fallback.
    private static boolean matches(String optionId, String query) {
        ShaderOptionsSearchEngine.MatchTierResult result = ShaderOptionsSearchEngine.computeMatchTier(optionId, query);
        return result.score() > 0 || result.typo();
    }

    @Nested
    @DisplayName("Basic matching (rawId fallback, no translations configured)")
    class BasicMatching {
        @Test
        void nullInputsReturnNoMatch() {
            assertFalse(matches(null, "bloom"));
            assertFalse(matches("BLOOM_STRENGTH", null));
        }

        @Test
        void emptyOrBlankQueryReturnsNoMatch() {
            assertFalse(matches("BLOOM_STRENGTH", ""));
            assertFalse(matches("BLOOM_STRENGTH", "   "));
        }

        @Test
        void singleTokenMatchesRawId() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom"));
        }

        @Test
        void unrelatedQueryDoesNotMatch() {
            assertFalse(matches("BLOOM_STRENGTH", "xyz123"));
        }
    }

    @Nested
    @DisplayName("Multi-word tokenized matching")
    class TokenizedMatching {
        @Test
        void reorderedTokensBothMatch() {
            assertTrue(matches("BLOOM_STRENGTH", "strength bloom"));
        }

        @Test
        void literalPhraseMatches() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom strength"));
        }

        @Test
        @DisplayName("Every token must appear somewhere -- one missing token fails the whole query")
        void missingTokenFailsWholeQuery() {
            assertFalse(matches("BLOOM_STRENGTH", "bloom xyz123"));
        }

        @Test
        @DisplayName("Regression: incremental typing through a multi-word query keeps matching at every step")
        void incrementalTypingKeepsMatching() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom"));
            assertTrue(matches("BLOOM_STRENGTH", "bloom s"));
            assertTrue(matches("BLOOM_STRENGTH", "bloom st"));
            assertTrue(matches("BLOOM_STRENGTH", "bloom str"));
            assertTrue(matches("BLOOM_STRENGTH", "bloom strength"));
        }

        @Test
        @DisplayName("A 1-char second token doesn't wrongly require the whole option to start with it")
        void oneCharTokenDoesNotRequireWholeNameStart() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom s"));
            assertFalse(matches("BLOOM", "bloom s")); // BLOOM has no second token to satisfy "s"
        }
    }

    @Nested
    @DisplayName("Synonym / acronym dictionary")
    class Synonyms {
        @Test
        void abbreviationFindsOptionNamedWithTheLongForm() {
            assertTrue(matches("AMBIENTOCCLUSION_STRENGTH", "ao"));
        }

        @Test
        void abbreviationDoesNotMatchUnrelatedOption() {
            assertFalse(matches("BLOOM_STRENGTH", "ao"));
        }

        @Test
        void abbreviationWorksAsOneTokenOfAMultiWordAndQuery() {
            assertTrue(matches("AMBIENTOCCLUSION_STRENGTH", "ao strength"));
        }

        @Test
        void literalFormStillMatchesDirectly() {
            assertTrue(matches("AMBIENTOCCLUSION_STRENGTH", "ambientocclusion"));
        }

        @Test
        void unrelatedSingleCharQueryIsUnaffectedByTheDictionary() {
            assertFalse(matches("BLOOM_STRENGTH", "a"));
        }

        @Test
        @DisplayName("Regression: a partial word (missing plural 's') still resolves through the synonym dictionary")
        void partialWordFuzzyMatchesADictionaryKey() {
            configureOption("OPT_LIGHTSHAFT", "Light Shafts", null, null, null);
            assertTrue(matches("OPT_LIGHTSHAFT", "godray"), "\"godray\" should fuzzy-match the \"godrays\" dictionary key");
        }

        @Test
        @DisplayName("A typo'd dictionary key still resolves through fuzzy synonym matching")
        void typoedDictionaryKeyFuzzyMatches() {
            configureOption("OPT_LIGHTSHAFT", "Light Shafts", null, null, null);
            assertTrue(matches("OPT_LIGHTSHAFT", "godrasy"), "a typo of \"godrays\" should still fuzzy-match the dictionary key");
        }

        @Test
        @DisplayName("Fuzzy synonym fallback doesn't fire for short queries or unrelated options")
        void fuzzySynonymFallbackIsScopedAppropriately() {
            configureOption("OPT_LIGHTSHAFT", "Light Shafts", null, null, null);
            assertFalse(matches("OPT_LIGHTSHAFT", "go"), "too short to trigger fuzzy dictionary matching");
            assertFalse(matches("BLOOM_STRENGTH", "godray"), "fuzzy synonym match shouldn't leak into unrelated options");
        }
    }

    @Nested
    @DisplayName("Translated / default-name / comment / value-translation matching (via fake runtime)")
    class TranslationBackedMatching {
        @Test
        @DisplayName("Matches via the active game language's translated name, even when rawId doesn't contain the query")
        void matchesViaTranslatedName() {
            configureOption("OPT_A1B2", "Light Bloom Intensity", null, null, null);
            assertTrue(matches("OPT_A1B2", "intensity"));
        }

        @Test
        @DisplayName("Matches via the shader pack's own en_us default name, even when rawId/translated name don't contain the query")
        void matchesViaDefaultName() {
            configureOption("OPT_C3D4", null, "Cascaded Shadow Radius", null, null);
            assertTrue(matches("OPT_C3D4", "cascaded"));
        }

        @Test
        @DisplayName("Matches via the option's comment text")
        void matchesViaComment() {
            configureOption("OPT_E5F6", null, null, "Adjusts the volumetric fog density.", null);
            assertTrue(matches("OPT_E5F6", "volumetric"));
        }

        @Test
        @DisplayName("Matches via a value.<id>.<suffix> value translation")
        void matchesViaValueTranslation() {
            configureOption("OPT_G7H8", null, null, null, Map.of("ULTRA", "Ultra Quality"));
            assertTrue(matches("OPT_G7H8", "ultra"));
        }

        @Test
        @DisplayName("A translated-name match outranks a default-name-only match (translated bits sit one above default)")
        void translatedNameOutranksDefaultNameOnly() {
            configureOption("OPT_TRANSLATED", "Bloom Strength", null, null, null);
            configureOption("OPT_DEFAULT_ONLY", null, "Bloom Radius", null, null);

            int translatedScore = ShaderOptionsSearchEngine.computeMatchTier("OPT_TRANSLATED", "bloom").score();
            int defaultOnlyScore = ShaderOptionsSearchEngine.computeMatchTier("OPT_DEFAULT_ONLY", "bloom").score();

            assertTrue(translatedScore > defaultOnlyScore);
        }

        @Test
        @DisplayName("Typo tolerance works end-to-end against a real translated name")
        void typoToleranceMatchesRealTranslatedName() {
            configureOption("OPT_STRENGTH", "Bloom Strength", null, null, null);

            ShaderOptionsSearchEngine.MatchTierResult result = ShaderOptionsSearchEngine.computeMatchTier("OPT_STRENGTH", "strenght");

            assertEquals(0, result.score(), "a typo-only match should carry no real score evidence");
            assertTrue(result.typo());
        }

        @Test
        @DisplayName("Value translations are checked across every active language code, not just en_us")
        void valueTranslationMatchesAcrossActiveLanguageCodes() {
            Language.languageCodes = List.of("en_us", "es_es");

            configureOption("OPT_MULTILANG", null, null, null, Map.of("ULTRA", "Ultra Quality"));
            // Add a Spanish-only value translation directly (configureOption only seeds en_us).
            LanguageMap languageMap = new LanguageMap();
            languageMap.put("en_us", new HashMap<>(enUsAccumulator));
            languageMap.put("es_es", Map.of("value.OPT_MULTILANG.ULTRA", "Calidad Ultra"));
            Iris.setCurrentPack(new ShaderPack(languageMap));

            assertTrue(matches("OPT_MULTILANG", "calidad"));
        }
    }

    @Nested
    @DisplayName("Typo tolerance (algorithm-level, no translations required)")
    class TypoTolerance {
        @Test
        void levenshteinDistanceOfASingleDeletion() {
            assertEquals(1, ShaderOptionsSearchEngine.levenshteinDistance("bloom", "blom"));
        }

        @Test
        void levenshteinDistanceOfATransposition() {
            // Adjacent-char swaps cost 2 under plain Levenshtein (2 substitutions), not 1.
            assertEquals(2, ShaderOptionsSearchEngine.levenshteinDistance("strenght", "strength"));
        }

        @Test
        void oneEditTypoOnAShortWordIsAccepted() {
            assertTrue(ShaderOptionsSearchEngine.isTypoMatch("blom", "bloom")); // len 4, maxDistance 1
        }

        @Test
        void twoEditTypoOnALongerWordIsAccepted() {
            assertTrue(ShaderOptionsSearchEngine.isTypoMatch("strenght", "strength")); // len 8, maxDistance 2
        }

        @Test
        void exactMatchHasZeroDistance() {
            assertTrue(ShaderOptionsSearchEngine.isTypoMatch("bloom", "bloom"));
        }

        @Test
        @DisplayName("Words below MIN_TYPO_WORD_LENGTH never typo-match, to avoid nonsense short-string hits")
        void wordsBelowMinimumLengthAreRejected() {
            assertFalse(ShaderOptionsSearchEngine.isTypoMatch("cat", "bloom"));
        }

        @Test
        void wordsTooFarApartAreRejected() {
            assertFalse(ShaderOptionsSearchEngine.isTypoMatch("xyzabc", "bloom"));
        }

        @Test
        void matchesAnyWordInAMultiWordName() {
            assertTrue(ShaderOptionsSearchEngine.typoMatchesAnyWord("strenght", "bloom strength"));
            assertFalse(ShaderOptionsSearchEngine.typoMatchesAnyWord("strenght", "sky color"));
        }

        @Test
        @DisplayName("A weak real match always outranks a typo-only match in the comparator")
        void realMatchOutranksTypoOnlyMatch() {
            var real = new ShaderOptionsSearchEngine.ScoredOptionElement("REAL_OPT", "bloom", "", "root", 2, false, "xyz");
            var typo = new ShaderOptionsSearchEngine.ScoredOptionElement("TYPO_OPT", "bloom", "", "root", 0, true, "xyz");

            assertTrue(real.compareTo(typo) < 0, "expected the real match to sort first regardless of score magnitude");
        }

        @Test
        @DisplayName("MatchTierResult keeps score and typo as genuinely separate signals for a clean non-match")
        void matchTierResultSeparatesScoreFromTypoFlag() {
            // No translations configured and "blom" isn't close to the rawId "bloom_strength" as a whole
            // (typo matching only ever checks translated/default names), so this is a clean non-match.
            ShaderOptionsSearchEngine.MatchTierResult result = ShaderOptionsSearchEngine.computeMatchTier("BLOOM_STRENGTH", "blom");
            assertEquals(0, result.score());
            assertFalse(result.typo());
        }
    }

    @Nested
    @DisplayName("getAllOptionsFlattened")
    class Flattening {
        @Test
        void deduplicatesPreservingFirstOccurrenceOrder() {
            var flattened = ShaderOptionsSearchEngine.getAllOptionsFlattened(
                    List.of("BLOOM", "SHADOW", "BLOOM", "WATER"));
            assertEquals(List.of("BLOOM", "SHADOW", "WATER"), flattened);
        }

        @Test
        void nullInputReturnsEmptyList() {
            assertTrue(ShaderOptionsSearchEngine.getAllOptionsFlattened(null).isEmpty());
        }
    }
}
