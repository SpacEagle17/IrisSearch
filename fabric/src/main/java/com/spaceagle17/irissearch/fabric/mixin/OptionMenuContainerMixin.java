package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.engine.ShaderOptionsSearchEngine;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.fabric.ISearchableOptionContainer;
import net.irisshaders.iris.shaderpack.option.ProfileSet;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.menu.*;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(OptionMenuContainer.class)
public class OptionMenuContainerMixin implements ISearchableOptionContainer {

    @Shadow
    @Final
    public OptionMenuElementScreen mainScreen;

    @Shadow
    @Final
    private List<OptionMenuOptionElement> usedOptionElements;

    @Shadow
    @Final
    public Map<String, OptionMenuElementScreen> subScreens;
    @Unique
    private final List<OptionMenuElement> irisSearch$originalMainElements = new ArrayList<>();

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[OptionMenuContainerMixin] " + message);
    }

    @Unique private final Map<String, String> irisSearch$cachedOptionPaths = new HashMap<>();

    /**
     * Runs once, right after the vanilla constructor finishes building mainScreen/subScreens
     * and dumping unused options. Snapshots the freshly-built layout so it can be restored
     * later when the search query is cleared.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void irisSearch$captureOriginalLayout(ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, ProfileSet profiles, CallbackInfo ci) {
        try {
            irisSearch$originalMainElements.clear();
            irisSearch$originalMainElements.addAll(this.mainScreen.elements);

            // 1. Walk the GUI tree top-down and map all full paths
            irisSearch$generateAllPaths();

            debugLog("Captured " + irisSearch$originalMainElements.size() + " original main-screen element(s)");

        } catch (Exception e) {
            IrisSearch.log(3, "Failed to capture original main screen layout." + e);
            debugLog("captureOriginalLayout threw: " + e);
        }
    }

    @Unique
    private void irisSearch$generateAllPaths() {
        irisSearch$cachedOptionPaths.clear();
        Set<String> visitedScreens = new HashSet<>();

        // Start recursive traversal from the actual visual root
        irisSearch$traverseScreen(this.mainScreen, "root", visitedScreens);
    }

    @Unique
    private void irisSearch$traverseScreen(OptionMenuElementScreen screen, String currentPath, Set<String> visitedScreens) {
        if (screen == null || screen.elements == null) return;

        for (OptionMenuElement element : screen.elements) {
            if (element == null) continue;

            // If it's an option, map its path string
            if (element instanceof OptionMenuOptionElement optionEl && optionEl.optionId != null) {
                irisSearch$cachedOptionPaths.putIfAbsent(optionEl.optionId, currentPath);
            }
            // If it's a sub-screen link, step into it recursively
            else if (element instanceof OptionMenuLinkElement link && link.targetScreenId != null) {
                String targetScreenId = link.targetScreenId;

                if (!visitedScreens.contains(targetScreenId)) {
                    visitedScreens.add(targetScreenId);

                    OptionMenuElementScreen nextScreen = this.subScreens.get(targetScreenId);
                    if (nextScreen != null) {
                        irisSearch$traverseScreen(nextScreen, currentPath + "/" + targetScreenId, visitedScreens);
                    }

                    visitedScreens.remove(targetScreenId);
                }
            }
        }
    }

    @Override
    public String irisSearch$getOptionPath(String targetOptionId) {
        if (targetOptionId == null) return "unknown";
        return irisSearch$cachedOptionPaths.getOrDefault(targetOptionId, "root");
    }


    @Override
    public void irisSearch$setSearchQuery(String query) {
        try {
            if (query == null || query.trim().isEmpty()) {
                irisSearch$restoreOriginalLayout();
                debugLog("Cleared search query, restored original layout");
                return;
            }

            // Leading "*" restricts results to only changed options (e.g. "*emissive").
            boolean changedOnly = ShaderOptionsSearchEngine.isChangedOnlyQuery(query);
            String normalizedQuery = ShaderOptionsSearchEngine.stripChangedOnlyMarker(query).toLowerCase(Locale.ROOT);
            // Term with any "menu:" scope prefix stripped
            String searchQuery = ShaderOptionsSearchEngine.stripMenuScope(normalizedQuery);

            // null when this isn't a "*" query, or when option values can't be read (degrade to a normal search).
            Set<String> changedIds = changedOnly ? irisSearch$collectChangedOptionIds() : null;

            if (normalizedQuery.isEmpty() && changedIds == null) {
                irisSearch$restoreOriginalLayout();
                debugLog("Query was only markers, restored original layout");
                return;
            }

            Map<String, OptionMenuOptionElement> elementById = new LinkedHashMap<>();
            for (OptionMenuOptionElement el : this.usedOptionElements) {
                if (el == null) continue;
                String id = el.optionId != null ? el.optionId : el.toString();
                elementById.putIfAbsent(id, el);
            }

            List<String> allFlatOptionIds = ShaderOptionsSearchEngine.getAllOptionsFlattened(new ArrayList<>(elementById.keySet()));
            List<ShaderOptionsSearchEngine.ScoredOptionElement> scoredResults = new ArrayList<>();

            for (String optionId : allFlatOptionIds) {
                if (changedIds != null && !changedIds.contains(optionId)) continue;
                String path = irisSearch$getOptionPath(optionId);

                if (normalizedQuery.isEmpty()) { // bare "*": list every changed option, unscored
                    scoredResults.add(irisSearch$scored(optionId, path, 1, false, "", 0));
                    continue;
                }

                ShaderOptionsSearchEngine.MatchTierResult match = ShaderOptionsSearchEngine.computeMatchTier(optionId, normalizedQuery, path);
                if (match.score() > 0 || match.typo()) {
                    scoredResults.add(irisSearch$scored(optionId, path, match.score(), match.typo(),
                            searchQuery, ShaderOptionsSearchEngine.computeMenuScopeTier(normalizedQuery, path)));
                }
            }

            scoredResults.sort(null);

            irisSearch$applyFilteredLayout(scoredResults, elementById);
            debugLog("Search query \"" + query + "\" -> " + scoredResults.size() + " match(es)" + (changedIds != null ? " [changed only]" : ""));
        } catch (Exception e) {
            IrisSearch.log(3, "Failed to apply search query.: " + e);
            debugLog("setSearchQuery failed for query \"" + query + "\": " + e);
        }
    }

    @Unique
    private ShaderOptionsSearchEngine.ScoredOptionElement irisSearch$scored(String optionId, String path, int score, boolean typo, String query, int menuScopeTier) {
        return new ShaderOptionsSearchEngine.ScoredOptionElement(optionId,
                ShaderOptionsSearchEngine.getReadableTranslatedName(optionId),
                ShaderOptionsSearchEngine.getReadableDefaultName(optionId),
                path, score, typo, query, menuScopeTier);
    }

    // Returns option IDs that have been changed or are pending or {@code null} on failure.
    @Unique
    private Set<String> irisSearch$collectChangedOptionIds() {
        try {
            Object values = null;
            for (OptionMenuOptionElement el : this.usedOptionElements) {
                if (el != null && (values = ReflectionUtils.invokeMethod(el, "getPendingOptionValues", new Class<?>[]{})) != null) break;
            }
            if (values == null) return null;

            Set<String> changed = new HashSet<>();
            for (String getter : new String[]{"getBooleanValues", "getStringValues"}) {
                if (ReflectionUtils.invokeMethod(values, getter, new Class<?>[]{}) instanceof Map<?, ?> map) {
                    map.keySet().forEach(k -> changed.add(String.valueOf(k)));
                }
            }
            return changed;
        } catch (Throwable t) {
            debugLog("'*' changed-only filter unavailable: " + t);
            return null;
        }
    }

    @Unique
    private void irisSearch$applyFilteredLayout(List<ShaderOptionsSearchEngine.ScoredOptionElement> sortedElements, Map<String, OptionMenuOptionElement> elementById) {
        this.mainScreen.elements.clear();
        for (ShaderOptionsSearchEngine.ScoredOptionElement scored : sortedElements) {
            OptionMenuOptionElement el = elementById.get(scored.optionId());
            if (el != null) this.mainScreen.elements.add(el);
        }
    }

    @Unique
    private void irisSearch$restoreOriginalLayout() {
        this.mainScreen.elements.clear();
        this.mainScreen.elements.addAll(irisSearch$originalMainElements);
    }
}
