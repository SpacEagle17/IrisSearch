package com.spaceagle17.irissearch.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static reference data consulted by the search engines -- well-known shader pack names and
 * shader-jargon synonym groups. Kept separate from {@link ShaderPackSearchEngine} and
 * {@link ShaderOptionsSearchEngine} so their match-tier scoring logic isn't buried under lookup
 * tables that need to be extended over time as new packs/jargon show up.
 */
public final class SearchDictionaries {
    private SearchDictionaries() {}

    // Well-known shader packs, normalized. See ShaderPackSearchEngine.computeIsPopular.
    public static final Set<String> POPULAR_PACK_KEYWORDS = Set.of(
            "complementaryreimagined", "complementaryunbound", "bliss", "bsl", "solas", "kappa", "spooklementary",
            "chocapic", "photon", "rethinkingvoxels", "euphoriapatches", "superdupervanilla", "astralex", "sildurs",
            "nostalgia", "seus", "makeup", "insanity", "lux", "mellow"
    );

    // Equivalent shader terms (e.g., "ao" and "ambient occlusion") expanded per query token.
    // Multi-word keys only match full tokenized terms. Must be lowercase.
    private static final List<Set<String>> SYNONYM_GROUPS = List.of(
        // Anti-Aliasing & Upscaling
        Set.of("aa", "antialiasing", "anti-aliasing"),
        Set.of("taa", "temporal aa", "temporal antialiasing", "temporal anti-aliasing"),
        Set.of("fxaa", "fast approximate antialiasing"),
        Set.of("smaa", "subpixel morphological antialiasing"),
        Set.of("msaa", "multisample anti-aliasing", "multi-sample anti-aliasing", "multisample antialiasing"),
        Set.of("csaa", "coverage sampling anti-aliasing", "eqaa", "enhanced quality anti-aliasing"),
        Set.of("dlss", "deep learning super sampling"),
        Set.of("fsr", "fidelityfx super resolution", "fidelity fx super resolution"),
        Set.of("xess", "intel xess", "xe super sampling"),
        Set.of("taau", "temporal anti-aliasing upsampling", "temporal upsampling"),
        Set.of("ao", "ambient occlusion", "ambientocclusion", "edge shadow"),
        Set.of("rtao", "ray traced ambient occlusion", "ray-traced ambient occlusion"),
        Set.of("ssao", "screen space ambient occlusion"),
        Set.of("hbao", "horizon based ambient occlusion", "horizon-based ambient occlusion", "hbao+"),
        Set.of("gtao", "ground truth ambient occlusion", "ground-truth ambient occlusion"),
        Set.of("gi", "global illumination"),
        Set.of("ssgi", "screen space global illumination"),
        Set.of("rtgi", "ray traced global illumination", "raytraced global illumination"),
        Set.of("vxgi", "voxel global illumination", "voxel grid global illumination"),
        Set.of("vl", "volumetric lighting", "volumetric light", "godrays", "god rays", "crepuscular rays"),
        Set.of("ssr", "screen space reflections", "screen space reflection", "reflections", "reflection"),
        Set.of("ssrr", "screen space refraction", "screen space refractions", "refraction"),
        Set.of("wsr", "world space reflections", "world-space reflections"),
        Set.of("rtx", "ray tracing", "ray-traced", "ray-tracing", "ray traced", "path tracing", "path traced", "path-tracing", "path-traced"),
        Set.of("sspt", "screen space path tracing"),
        Set.of("pom", "parallax occlusion mapping", "parallax"),
        Set.of("tessellation", "tess", "displacement mapping", "displacement"),
        Set.of("sss", "subsurface scattering", "sub surface scattering"),
        Set.of("pbr", "physically based rendering"),
        Set.of("csm", "cascaded shadow maps", "cascaded shadow mapping", "cascade shadows"),
        Set.of("pcss", "percentage closer soft shadows", "percentage-closer soft shadows"),
        Set.of("rtsm", "ray traced shadows", "ray-traced shadows"),
        Set.of("dof", "depth of field"),
        Set.of("hdr", "high dynamic range"),
        Set.of("lut", "look up table", "lookup table", "color lookup table"),
        Set.of("ca", "chromatic aberration"),
        Set.of("coc", "circle of confusion"),
        Set.of("act", "advanced color tracing"),
        Set.of("mb", "motion blur", "motionblur", "camera blur")
    );

    private static final Map<String, Set<String>> SYNONYM_LOOKUP = buildSynonymLookup();

    private static Map<String, Set<String>> buildSynonymLookup() {
        Map<String, Set<String>> lookup = new HashMap<>();
        for (Set<String> group : SYNONYM_GROUPS) {
            for (String member : group) {
                Set<String> others = new HashSet<>(group);
                others.remove(member);
                lookup.put(member, Set.copyOf(others));
            }
        }
        return Map.copyOf(lookup);
    }

    /** Returns the known shader-jargon synonyms for a query string (e.g. "ao" -> {"ambient occlusion", ...}), or empty. */
    public static Set<String> getSynonyms(String queryString) {
        return SYNONYM_LOOKUP.getOrDefault(queryString, Set.of());
    }
}
