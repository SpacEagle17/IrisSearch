package com.spaceagle17.irissearch.engine;

import com.spaceagle17.irissearch.ModLoaderSpecifics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ShaderPackSearchEngine}
 */
class ShaderPackSearchEngineTest {

    @BeforeAll
    static void setUpModLoader() {
        // ShaderPackSearchEngine's debugLog path can reach IrisSearch, whose static init requires
        // ModLoaderSpecifics to already have an instance -- provide a minimal stand-in.
        ModLoaderSpecifics.setInstance(new ModLoaderSpecifics() {
            public String getInstanceName() { return "Fabric"; }
            public Path getConfigDirectory() { return Paths.get("."); }
            public boolean serverCheck() { return false; }
        });
    }

    @Nested
    @DisplayName("Basic matching")
    class BasicMatching {
        @Test
        void nullInputsReturnZero() {
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier(null, "bliss"));
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier("Bliss.zip", null));
        }

        @Test
        void emptyOrBlankQueryReturnsZero() {
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier("Complementary Reimagined r5.zip", ""));
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier("Complementary Reimagined r5.zip", "   "));
        }

        @Test
        void singleWordSubstringMatches() {
            assertTrue(ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", "bliss") > 0);
        }

        @Test
        void unrelatedQueryDoesNotMatch() {
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", "xyz123"));
        }

        @Test
        void matchingIsCaseInsensitive() {
            assertTrue(ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", "BLISS") > 0);
        }

        @Test
        @DisplayName("A standalone 1-char query only matches if the name starts with it")
        void standaloneOneCharQueryIsStrict() {
            assertTrue(ShaderPackSearchEngine.computeMatchTier("Sildurs Vibrant Shaders.zip", "s") > 0);
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier("Bliss Shaders v1.zip", "s"));
        }
    }

    @Nested
    @DisplayName("Multi-word tokenized matching")
    class TokenizedMatching {
        @Test
        void reorderedTokensBothMatch() {
            assertTrue(ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", "shaders bliss") > 0);
        }

        @Test
        void literalPhraseMatches() {
            assertTrue(ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", "bliss shaders") > 0);
        }

        @Test
        @DisplayName("Every token must appear somewhere -- one missing token fails the whole query")
        void missingTokenFailsWholeQuery() {
            assertEquals(0, ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", "bliss xyz123"));
        }

        @Test
        void literalPhraseRanksAtLeastAsHighAsScatteredTokens() {
            String query = "bliss shaders";
            int phrase = ShaderPackSearchEngine.computeMatchTier("BSL Bliss Shaders v9.zip", query);
            int scattered = ShaderPackSearchEngine.computeMatchTier("Bliss Amazing Shaders Pack.zip", query);
            assertTrue(phrase >= scattered);
        }

        @Test
        @DisplayName("Regression: a 1-char token inside a multi-word query matches on word boundaries, not the strict standalone rule")
        void oneCharTokenInMultiWordQueryStillMatches() {
            // "bliss s" while the user is still typing "bliss shaders" -- the second token is 1 char.
            assertTrue(ShaderPackSearchEngine.computeMatchTier("Bliss Shaders v1.zip", "bliss s") > 0);
        }
    }

    @Nested
    @DisplayName("Ranking / ScoredPackElement ordering")
    class Ranking {
        @Test
        @DisplayName("A well-known pack outranks a lesser-known one at the same match tier")
        void popularPacksRankAboveObscureOnesAtEqualTier() {
            String query = "shaders";
            var popular = ShaderPackSearchEngine.ScoredPackElement.of("BSL Shaders v9.zip",
                    ShaderPackSearchEngine.computeMatchTier("BSL Shaders v9.zip", query), query);
            var obscure = ShaderPackSearchEngine.ScoredPackElement.of("SomeRandomPack Shaders.zip",
                    ShaderPackSearchEngine.computeMatchTier("SomeRandomPack Shaders.zip", query), query);

            assertTrue(popular.compareTo(obscure) < 0, "expected the popular pack to sort first");
        }

        @Test
        @DisplayName("Higher version number ranks above lower, given an identical name prefix")
        void higherVersionRanksAboveLower() {
            String query = "complementary";
            var older = ShaderPackSearchEngine.ScoredPackElement.of("Complementary Reimagined r5.1.zip",
                    ShaderPackSearchEngine.computeMatchTier("Complementary Reimagined r5.1.zip", query), query);
            var newer = ShaderPackSearchEngine.ScoredPackElement.of("Complementary Reimagined r5.2.zip",
                    ShaderPackSearchEngine.computeMatchTier("Complementary Reimagined r5.2.zip", query), query);

            assertTrue(newer.compareTo(older) < 0, "expected the newer version to sort first");
        }
    }

    @Nested
    @DisplayName("getReadableName")
    class ReadableName {
        @Test
        void stripsZipExtensionAndLowercases() {
            assertEquals("bsl bliss shaders v9", ShaderPackSearchEngine.getReadableName("BSL Bliss Shaders v9.zip"));
        }

        @Test
        void nullInputReturnsEmptyString() {
            assertEquals("", ShaderPackSearchEngine.getReadableName(null));
        }
    }
}
