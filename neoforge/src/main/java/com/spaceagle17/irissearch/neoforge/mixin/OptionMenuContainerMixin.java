package com.spaceagle17.irissearch.neoforge.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ShaderSearchEngine;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.neoforge.ISearchableOptionContainer;
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

    @Unique private final Map<String, String> irisSearch$cachedOptionPaths = new HashMap<>();


    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[OptionMenuContainerMixin] " + message);
    }

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

            irisSearch$debugLog("Captured " + irisSearch$originalMainElements.size() + " original main-screen element(s)");

        } catch (Exception e) {
            IrisSearch.log(3, "Failed to capture original main screen layout." + e);
            irisSearch$debugLog("captureOriginalLayout threw: " + e);
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
                irisSearch$debugLog("Cleared search query, restored original layout");
                return;
            }

            String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();

            Map<String, OptionMenuOptionElement> elementById = new LinkedHashMap<>();
            for (OptionMenuOptionElement el : this.usedOptionElements) {
                if (el == null) continue;
                String id = el.optionId != null ? el.optionId : el.toString();
                elementById.putIfAbsent(id, el);
            }

            List<String> allFlatOptionIds = ShaderSearchEngine.getAllOptionsFlattened(new ArrayList<>(elementById.keySet()));
            List<ShaderSearchEngine.ScoredOptionElement> scoredResults = new ArrayList<>();

            String path;
            for (String optionId : allFlatOptionIds) {
                path = irisSearch$getOptionPath(optionId);
                int score = ShaderSearchEngine.computeMatchTier(optionId, normalizedQuery);
                if (score > 0) {
                    scoredResults.add(new ShaderSearchEngine.ScoredOptionElement(optionId, ShaderSearchEngine.getReadableName(optionId), path, score, normalizedQuery));
                }
            }

            scoredResults.sort(null);

            irisSearch$applyFilteredLayout(scoredResults, elementById);
            irisSearch$debugLog("Search query \"" + query + "\" -> " + scoredResults.size() + " match(es)");
        } catch (Exception e) {
            IrisSearch.log(3, "Failed to apply search query." + e);
            irisSearch$debugLog("setSearchQuery failed for query \"" + query + "\": " + e);
        }
    }

    @Unique
    private void irisSearch$applyFilteredLayout(List<ShaderSearchEngine.ScoredOptionElement> sortedElements, Map<String, OptionMenuOptionElement> elementById) {
        this.mainScreen.elements.clear();
        for (ShaderSearchEngine.ScoredOptionElement scored : sortedElements) {
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
