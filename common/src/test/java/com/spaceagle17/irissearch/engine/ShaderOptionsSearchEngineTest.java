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

    private static boolean matches(String optionId, String query, String path) {
        ShaderOptionsSearchEngine.MatchTierResult result = ShaderOptionsSearchEngine.computeMatchTier(optionId, query, path);
        return result.score() > 0 || result.typo();
    }

    @Nested
    @DisplayName("Basic matching (rawId fallback, no translations configured)")
    class BasicMatching {
        @Test
        void nullInputsReturnNoMatch() {
            assertFalse(matches(null, "bloom", null));
            assertFalse(matches("BLOOM_STRENGTH", null, null));
        }

        @Test
        void emptyOrBlankQueryReturnsNoMatch() {
            assertFalse(matches("BLOOM_STRENGTH", "", null));
            assertFalse(matches("BLOOM_STRENGTH", "   ", null));
        }

        @Test
        void singleTokenMatchesRawId() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom", null));
        }

        @Test
        void unrelatedQueryDoesNotMatch() {
            assertFalse(matches("BLOOM_STRENGTH", "xyz123", null));
        }
    }

    @Nested
    @DisplayName("Multi-word tokenized matching")
    class TokenizedMatching {
        @Test
        void reorderedTokensBothMatch() {
            assertTrue(matches("BLOOM_STRENGTH", "strength bloom", null));
        }

        @Test
        void literalPhraseMatches() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom strength", null));
        }

        @Test
        @DisplayName("Every token must appear somewhere -- one missing token fails the whole query")
        void missingTokenFailsWholeQuery() {
            assertFalse(matches("BLOOM_STRENGTH", "bloom xyz123", null));
        }

        @Test
        @DisplayName("Regression: incremental typing through a multi-word query keeps matching at every step")
        void incrementalTypingKeepsMatching() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom", null));
            assertTrue(matches("BLOOM_STRENGTH", "bloom s", null));
            assertTrue(matches("BLOOM_STRENGTH", "bloom st", null));
            assertTrue(matches("BLOOM_STRENGTH", "bloom str", null));
            assertTrue(matches("BLOOM_STRENGTH", "bloom strength", null));
        }

        @Test
        @DisplayName("A 1-char second token doesn't wrongly require the whole option to start with it")
        void oneCharTokenDoesNotRequireWholeNameStart() {
            assertTrue(matches("BLOOM_STRENGTH", "bloom s", null));
            assertFalse(matches("BLOOM", "bloom s", null)); // BLOOM has no second token to satisfy "s"
        }
    }

    @Nested
    @DisplayName("Synonym / acronym dictionary")
    class Synonyms {
        @Test
        void abbreviationFindsOptionNamedWithTheLongForm() {
            assertTrue(matches("AMBIENTOCCLUSION_STRENGTH", "ao", null));
        }

        @Test
        void abbreviationDoesNotMatchUnrelatedOption() {
            assertFalse(matches("BLOOM_STRENGTH", "ao", null));
        }

        @Test
        void abbreviationWorksAsOneTokenOfAMultiWordAndQuery() {
            assertTrue(matches("AMBIENTOCCLUSION_STRENGTH", "ao strength", null));
        }

        @Test
        void literalFormStillMatchesDirectly() {
            assertTrue(matches("AMBIENTOCCLUSION_STRENGTH", "ambientocclusion", null));
        }

        @Test
        void unrelatedSingleCharQueryIsUnaffectedByTheDictionary() {
            assertFalse(matches("BLOOM_STRENGTH", "a", null));
        }

        @Test
        @DisplayName("Regression: a partial word (missing plural 's') still resolves through the synonym dictionary")
        void partialWordFuzzyMatchesADictionaryKey() {
            configureOption("OPT_LIGHTSHAFT", "Light Shafts", null, null, null);
            assertTrue(matches("OPT_LIGHTSHAFT", "godray", null), "\"godray\" should fuzzy-match the \"godrays\" dictionary key");
        }

        @Test
        @DisplayName("A typo'd dictionary key still resolves through fuzzy synonym matching")
        void typoedDictionaryKeyFuzzyMatches() {
            configureOption("OPT_LIGHTSHAFT", "Light Shafts", null, null, null);
            assertTrue(matches("OPT_LIGHTSHAFT", "godrasy", null), "a typo of \"godrays\" should still fuzzy-match the dictionary key");
        }

        @Test
        @DisplayName("Fuzzy synonym fallback doesn't fire for short queries or unrelated options")
        void fuzzySynonymFallbackIsScopedAppropriately() {
            configureOption("OPT_LIGHTSHAFT", "Light Shafts", null, null, null);
            assertFalse(matches("OPT_LIGHTSHAFT", "go", null), "too short to trigger fuzzy dictionary matching");
            assertFalse(matches("BLOOM_STRENGTH", "godray", null), "fuzzy synonym match shouldn't leak into unrelated options");
        }

        @Test
        @DisplayName("\"dh\" resolves to a Distant Horizons option")
        void dhAbbreviationResolvesToDistantHorizons() {
            configureOption("OPT_DH_QUALITY", "Distant Horizons Quality", null, null, null);
            assertTrue(matches("OPT_DH_QUALITY", "dh", null));
        }

        @Test
        @DisplayName("\"wind\" surfaces a waving-animation option")
        void windResolvesToTheWavingGroup() {
            configureOption("OPT_WAVE", "Waving Foliage", null, null, null);
            assertTrue(matches("OPT_WAVE", "wind", null));
        }

        @Test
        @DisplayName("\"vibrance\" and \"saturation\" are interchangeable")
        void vibranceResolvesToSaturation() {
            configureOption("OPT_SAT", "Saturation", null, null, null);
            assertTrue(matches("OPT_SAT", "vibrance", null));
        }
    }

    @Nested
    @DisplayName("Translated / default-name / comment / value-translation matching (via fake runtime)")
    class TranslationBackedMatching {
        @Test
        @DisplayName("Matches via the active game language's translated name, even when rawId doesn't contain the query")
        void matchesViaTranslatedName() {
            configureOption("OPT_A1B2", "Light Bloom Intensity", null, null, null);
            assertTrue(matches("OPT_A1B2", "intensity", null));
        }

        @Test
        @DisplayName("Matches via the shader pack's own en_us default name, even when rawId/translated name don't contain the query")
        void matchesViaDefaultName() {
            configureOption("OPT_C3D4", null, "Cascaded Shadow Radius", null, null);
            assertTrue(matches("OPT_C3D4", "cascaded", null));
        }

        @Test
        @DisplayName("Matches via the option's comment text")
        void matchesViaComment() {
            configureOption("OPT_E5F6", null, null, "Adjusts the volumetric fog density.", null);
            assertTrue(matches("OPT_E5F6", "volumetric", null));
        }

        @Test
        @DisplayName("Matches via a value.<id>.<suffix> value translation")
        void matchesViaValueTranslation() {
            configureOption("OPT_G7H8", null, null, null, Map.of("ULTRA", "Ultra Quality"));
            assertTrue(matches("OPT_G7H8", "ultra", null));
        }

        @Test
        @DisplayName("A translated-name match outranks a default-name-only match (translated bits sit one above default)")
        void translatedNameOutranksDefaultNameOnly() {
            configureOption("OPT_TRANSLATED", "Bloom Strength", null, null, null);
            configureOption("OPT_DEFAULT_ONLY", null, "Bloom Radius", null, null);

            int translatedScore = ShaderOptionsSearchEngine.computeMatchTier("OPT_TRANSLATED", "bloom", null).score();
            int defaultOnlyScore = ShaderOptionsSearchEngine.computeMatchTier("OPT_DEFAULT_ONLY", "bloom", null).score();

            assertTrue(translatedScore > defaultOnlyScore);
        }

        @Test
        @DisplayName("Typo tolerance works end-to-end against a real translated name")
        void typoToleranceMatchesRealTranslatedName() {
            configureOption("OPT_STRENGTH", "Bloom Strength", null, null, null);

            ShaderOptionsSearchEngine.MatchTierResult result = ShaderOptionsSearchEngine.computeMatchTier("OPT_STRENGTH", "strenght", null);

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

            assertTrue(matches("OPT_MULTILANG", "calidad", null));
        }
    }

    @Nested
    @DisplayName("Containing-submenu matching (query matches the submenu name, not the option itself)")
    class ContainingMenuMatching {
        @Test
        @DisplayName("A query matching the submenu name surfaces options inside it, even with no direct match")
        void queryMatchingMenuNameSurfacesOptionsInsideIt() {
            Language.addTranslations(Map.of("screen.REFLECTIONS_MENU", "Screen Space Reflections"));
            configureOption("OPT_QUALITY", "Quality", null, null, null);

            assertTrue(matches("OPT_QUALITY", "reflections", "root/REFLECTIONS_MENU"));
        }

        @Test
        @DisplayName("Without path context, the same query does not match")
        void sameQueryDoesNotMatchWithoutPathContext() {
            Language.addTranslations(Map.of("screen.REFLECTIONS_MENU", "Screen Space Reflections"));
            configureOption("OPT_QUALITY", "Quality", null, null, null);

            assertFalse(matches("OPT_QUALITY", "reflections", null));
        }

        @Test
        @DisplayName("Root-level options (no submenu) are unaffected by menu matching")
        void rootLevelOptionIsUnaffected() {
            Language.addTranslations(Map.of("screen.REFLECTIONS_MENU", "Screen Space Reflections"));
            configureOption("OPT_QUALITY", "Quality", null, null, null);

            assertFalse(matches("OPT_QUALITY", "reflections", "root"));
        }

        @Test
        @DisplayName("A direct option-name match still outranks a menu-only match")
        void directMatchOutranksMenuOnlyMatch() {
            Language.addTranslations(Map.of("screen.REFLECTIONS_MENU", "Screen Space Reflections"));
            configureOption("OPT_DIRECT", "Reflections Strength", null, null, null);
            configureOption("OPT_VIA_MENU", "Quality", null, null, null);

            int directScore = ShaderOptionsSearchEngine.computeMatchTier("OPT_DIRECT", "reflections", "root").score();
            int viaMenuScore = ShaderOptionsSearchEngine.computeMatchTier("OPT_VIA_MENU", "reflections", "root/REFLECTIONS_MENU").score();

            assertTrue(directScore > viaMenuScore);
        }

        @Test
        @DisplayName("Menu matching also works through the untranslated screen id when no translation exists")
        void matchesViaRawMenuIdWhenUntranslated() {
            configureOption("OPT_QUALITY", "Quality", null, null, null);

            assertTrue(matches("OPT_QUALITY", "reflections", "root/REFLECTIONS_MENU"));
        }

        @Test
        @DisplayName("Unscoped menu matching is loose: \"water\" also reaches a \"Watermark\" menu (the strict \"menu:\" scope does not - see MenuScopedQueries)")
        void unscopedMenuMatchingIsLoose() {
            Language.addTranslations(Map.of("screen.WATERMARK_MENU", "Watermark Settings"));
            configureOption("OPT_OPACITY", "Opacity", null, null, null);

            assertTrue(matches("OPT_OPACITY", "water", "root/WATERMARK_MENU"));
        }
    }

    @Nested
    @DisplayName("Menu-scoped queries (\"water: caustics\" restricts to the Water submenu)")
    class MenuScopedQueries {
        @Test
        void parsesAPlainQueryAsAllTermNoScope() {
            ShaderOptionsSearchEngine.MenuScopedQuery scoped = ShaderOptionsSearchEngine.parseMenuScope("bloom strength");
            assertFalse(scoped.hasScope());
            assertEquals("bloom strength", scoped.term());
        }

        @Test
        void parsesScopeAndTermAroundTheFirstColon() {
            ShaderOptionsSearchEngine.MenuScopedQuery scoped = ShaderOptionsSearchEngine.parseMenuScope("water: caustics");
            assertEquals(List.of("water"), scoped.menuScope());
            assertEquals("caustics", scoped.term());
        }

        @Test
        void parsesNestedScopeSeparatedBySlash() {
            ShaderOptionsSearchEngine.MenuScopedQuery scoped = ShaderOptionsSearchEngine.parseMenuScope("lighting/shadows: bias");
            assertEquals(List.of("lighting", "shadows"), scoped.menuScope());
            assertEquals("bias", scoped.term());
        }

        @Test
        void emptyScopeBeforeColonIsTreatedAsNoScope() {
            ShaderOptionsSearchEngine.MenuScopedQuery scoped = ShaderOptionsSearchEngine.parseMenuScope(":caustics");
            assertFalse(scoped.hasScope());
            assertEquals("caustics", scoped.term());
        }

        @Test
        @DisplayName("A term that matches, inside the scoped menu, is kept")
        void scopedTermInsideMatchingMenuMatches() {
            Language.addTranslations(Map.of("screen.WATER_MENU", "Water"));
            configureOption("OPT_CAUSTICS", "Caustics Strength", null, null, null);

            assertTrue(matches("OPT_CAUSTICS", "water: caustics", "root/WATER_MENU"));
        }

        @Test
        @DisplayName("A matching term outside the scoped menu is filtered out")
        void scopedTermOutsideMenuIsFilteredOut() {
            Language.addTranslations(Map.of("screen.WATER_MENU", "Water"));
            configureOption("OPT_CAUSTICS", "Caustics Strength", null, null, null);

            assertFalse(matches("OPT_CAUSTICS", "water: caustics", "root/LIGHTING_MENU"));
        }

        @Test
        @DisplayName("A non-matching term inside the scoped menu is still rejected")
        void scopedNonMatchingTermIsRejected() {
            Language.addTranslations(Map.of("screen.WATER_MENU", "Water"));
            configureOption("OPT_CAUSTICS", "Caustics Strength", null, null, null);

            assertFalse(matches("OPT_CAUSTICS", "water: bloom", "root/WATER_MENU"));
        }

        @Test
        @DisplayName("An empty term (\"water:\") surfaces every option in the menu and nothing outside it")
        void emptyTermSurfacesWholeMenu() {
            Language.addTranslations(Map.of("screen.WATER_MENU", "Water"));
            configureOption("OPT_IN", "Some Option", null, null, null);
            configureOption("OPT_OUT", "Some Option", null, null, null);

            assertTrue(matches("OPT_IN", "water:", "root/WATER_MENU"));
            assertFalse(matches("OPT_OUT", "water:", "root/SKY_MENU"));
        }

        @Test
        @DisplayName("Scope resolves against the raw screen id when it has no translation")
        void scopeMatchesRawScreenId() {
            configureOption("OPT_Q", "Quality", null, null, null);

            assertTrue(matches("OPT_Q", "reflections: quality", "root/REFLECTIONS_MENU"));
        }

        @Test
        @DisplayName("Scope tolerates separators (\"screen space\" ~ \"Screen Space Reflections\")")
        void scopeToleratesSeparators() {
            Language.addTranslations(Map.of("screen.SSR_MENU", "Screen Space Reflections"));
            configureOption("OPT_Q", "Quality", null, null, null);

            assertTrue(matches("OPT_Q", "screen space: quality", "root/SSR_MENU"));
        }

        @Test
        @DisplayName("Nested scope requires every part to appear somewhere in the path")
        void nestedScopeRequiresAllParts() {
            configureOption("OPT_BIAS", "Bias", null, null, null);

            assertTrue(matches("OPT_BIAS", "lighting/shadows: bias", "root/LIGHTING/SHADOWS"));
            assertFalse(matches("OPT_BIAS", "lighting/clouds: bias", "root/LIGHTING/SHADOWS"));
        }

        @Test
        @DisplayName("\"water:\" still reaches a \"Watermark\" menu (loose), but at a lower tier than a real \"Water\" menu")
        void looseMenuMatchRanksBelowExact() {
            Language.addTranslations(Map.of(
                    "screen.WATER_MENU", "Water",
                    "screen.WATERMARK_MENU", "Watermark Settings"));
            configureOption("OPT_A", "Size", null, null, null);

            assertTrue(matches("OPT_A", "water:", "root/WATERMARK_MENU"), "loose menu match still surfaces the option");

            int exactTier = ShaderOptionsSearchEngine.computeMenuScopeTier("water:", "root/WATER_MENU");
            int looseTier = ShaderOptionsSearchEngine.computeMenuScopeTier("water:", "root/WATERMARK_MENU");
            assertEquals(3, exactTier);
            assertEquals(1, looseTier);
            assertTrue(exactTier > looseTier);
        }

        @Test
        @DisplayName("A partial menu name matches (\"atmosphere co\" ~ \"Atmosphere Color\")")
        void partialMenuNameMatches() {
            Language.addTranslations(Map.of("screen.ATMO_COLOR", "Atmosphere Color"));
            configureOption("OPT_HUE", "Hue", null, null, null);

            assertTrue(matches("OPT_HUE", "atmosphere co: hue", "root/ATMO_COLOR"));
            assertTrue(matches("OPT_HUE", "atmosphere co:", "root/ATMO_COLOR"));
            // Fully typed scores a higher tier than the abbreviation.
            assertTrue(ShaderOptionsSearchEngine.computeMenuScopeTier("atmosphere color:", "root/ATMO_COLOR")
                    > ShaderOptionsSearchEngine.computeMenuScopeTier("atmosphere co:", "root/ATMO_COLOR"));
        }

        @Test
        @DisplayName("compareTo puts a better menu-scope tier first regardless of term-match strength")
        void comparatorHonoursMenuScopeTier() {
            var strongTermLooseMenu = new ShaderOptionsSearchEngine.ScoredOptionElement(
                    "A", "caustics", "", "root/WATERMARK", 1 << 14, false, "caustics", 1);
            var weakTermExactMenu = new ShaderOptionsSearchEngine.ScoredOptionElement(
                    "B", "water caustics detail", "", "root/WATER", 1 << 5, false, "caustics", 3);
            assertTrue(weakTermExactMenu.compareTo(strongTermLooseMenu) < 0);
        }

        @Test
        @DisplayName("Menu name resolves via the pack's en_us name when the active language lacks a translation for it")
        void scopeUsesEnglishFallbackForMenuName() {
            enUsAccumulator.put("screen.REFLECT_MENU", "Reflections");
            configureOption("OPT_Q", "Quality", null, null, null);

            assertTrue(matches("OPT_Q", "reflections: quality", "root/REFLECT_MENU"));
        }

        @Test
        @DisplayName("Scope works for languages without word spacing (CJK menu name)")
        void scopeWorksForSpacelessLanguages() {
            Language.addTranslations(Map.of("screen.WATER_MENU", "水面設定"));
            configureOption("OPT_Q", "Quality", null, null, null);

            assertTrue(matches("OPT_Q", "水面: quality", "root/WATER_MENU"));
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
            ShaderOptionsSearchEngine.MatchTierResult result = ShaderOptionsSearchEngine.computeMatchTier("BLOOM_STRENGTH", "blom", null);
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
