package com.spaceagle17.irissearch.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.config.ConfigHandler;
import com.spaceagle17.irissearch.engine.ShaderPackSearchEngine;
import com.spaceagle17.irissearch.fabric.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.fabric.ISearchablePackList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Filters via iterator redirect, not post-hoc removal—entries store their construction-time index,
 * so post-hoc removal breaks that. Redirect also lets names.isEmpty() checks see true pack count.
 */
@Mixin(ShaderPackSelectionList.class)
public abstract class ShaderPackSelectionListMixin implements ISearchablePackList {

    @Shadow public abstract void refresh();

    /** Extra vertical space reserved above the list's rows for the always-visible pack search box. */
    @Unique private static final int RESERVED_HEADER_HEIGHT = 20;

    @Unique private String irisSearch$typedSearchQuery = "";
    @Unique private int irisSearch$savedCursorPosition = 0;
    @Unique private boolean irisSearch$lastFilterHadNoMatches = false;

    @Unique private int irisSearch$listLeft = 0;
    @Unique private int irisSearch$listTop = 0;
    @Unique private int irisSearch$listWidth = 0;
    @Unique private int irisSearch$listBottom = 0;
    @Unique private int irisSearch$rowWidth = 0;
    @Unique private boolean irisSearch$isInitialized = false;
    @Unique private int irisSearch$packCount = 0;

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackSelectionListMixin] " + message);
    }

    /**
     * Inject at INVOKE of refresh() (not TAIL of constructor—more reliable) and use @Local for bounds.
     * Compute rowWidth directly (vanilla override intermediary ids drift across MC generations).
     */
    @Inject(
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gui/element/ShaderPackSelectionList;refresh()V"
            )
    )
    private void irisSearch$captureConstructorBounds(CallbackInfo ci,
                                                       @Local(ordinal = 0) int width,
                                                       @Local(ordinal = 1) int height,
                                                       @Local(ordinal = 2) int top,
                                                       @Local(ordinal = 3) int bottom,
                                                       @Local(ordinal = 4) int left,
                                                       @Local(ordinal = 5) int right) {
        if (!this.irisSearch$isInitialized) {
            this.irisSearch$isInitialized = true;

            this.irisSearch$listLeft = left;
            this.irisSearch$listTop = top + 4;
            this.irisSearch$listWidth = right - left;
            this.irisSearch$listBottom = bottom;
            this.irisSearch$rowWidth = Math.min(308, width - 50);

            debugLog("Captured list bounds via Locals: left=" + left
                    + " top=" + this.irisSearch$listTop
                    + " width=" + this.irisSearch$listWidth
                    + " bottom=" + this.irisSearch$listBottom
                    + " rowWidth=" + this.irisSearch$rowWidth);
        }
    }

    @Override public String irisSearch$getTypedSearchQuery() { return this.irisSearch$typedSearchQuery; }
    @Override public void irisSearch$setTypedSearchQuery(String query) { this.irisSearch$typedSearchQuery = query != null ? query : ""; }
    @Override public int irisSearch$getSavedCursorPosition() { return this.irisSearch$savedCursorPosition; }
    @Override public void irisSearch$setSavedCursorPosition(int pos) { this.irisSearch$savedCursorPosition = Math.max(0, pos); }
    @Override public int irisSearch$getReservedHeaderHeight() { return RESERVED_HEADER_HEIGHT; }
    @Override public int irisSearch$getListLeft() { return this.irisSearch$listLeft; }
    @Override public int irisSearch$getListTop() { return this.irisSearch$listTop; }
    @Override public int irisSearch$getListWidth() { return this.irisSearch$listWidth; }
    @Override public int irisSearch$getListBottom() { return this.irisSearch$listBottom; }
    @Override public int irisSearch$getRowWidth() { return this.irisSearch$rowWidth; }
    @Override public boolean irisSearch$shouldShowSearchBar() { return this.irisSearch$packCount >= ConfigHandler.minPacksForSelectionSearch; }

    @Override
    public void irisSearch$updateSearchQuery(String query) {
        try {
            this.irisSearch$typedSearchQuery = query != null ? query : "";
            this.refresh();
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't update the shader pack search results." + e);
            debugLog("Failed to update search query \"" + query + "\": " + e);
        }
    }

    @Redirect(method = "refresh", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"), require = 0, remap = false)
    private Iterator<String> irisSearch$filterNamesForIteration(List<String> names) {
        try {
            this.irisSearch$packCount = names.size();

            String query = this.irisSearch$typedSearchQuery.trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                this.irisSearch$lastFilterHadNoMatches = false;
                return names.iterator();
            }

            debugLog("Filtering pack list with query \"" + query + "\" (original size: " + names.size() + ")");

            List<ShaderPackSearchEngine.ScoredPackElement> scored = new ArrayList<>();
            for (String name : names) {
                int score = ShaderPackSearchEngine.computeMatchTier(name, query);
                if (score > 0) {
                    scored.add(new ShaderPackSearchEngine.ScoredPackElement(name, score, query));
                }
            }
            scored.sort(null);

            List<String> filtered = new ArrayList<>();
            for (ShaderPackSearchEngine.ScoredPackElement el : scored) {
                filtered.add(el.packName());
            }

            this.irisSearch$lastFilterHadNoMatches = filtered.isEmpty() && !names.isEmpty();
            debugLog("Applied pack search filter \"" + query + "\", " + filtered.size() + " match(es)");
            return filtered.iterator();
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't filter the shader pack list." + e);
            debugLog("Failed to apply search filter: " + e);
            return names.iterator();
        }
    }

    @Inject(method = "refresh", at = @At("TAIL"), require = 0, remap = false)
    private void irisSearch$appendNoResultsLabel(CallbackInfo ci) {
        if (this.irisSearch$lastFilterHadNoMatches) {
            irisSearch$addNoResultsLabel();
        }
    }

    @Unique
    private void irisSearch$addNoResultsLabel() {
        try {
            Class<?> componentClass = MinecraftBridge.resolveClass("net.minecraft.network.chat.Component", "net.minecraft.class_2561");
            Object label = MinecraftBridge.createTranslatableComponent("iris_search.search.no_results");
            if (componentClass == null || label == null) {
                return;
            }
            Object componentArray = Array.newInstance(componentClass, 1);
            Array.set(componentArray, 0, label);

            for (Method m : this.getClass().getDeclaredMethods()) {
                if (m.getName().equals("addLabelEntries") && m.getParameterCount() == 1) {
                    m.setAccessible(true);
                    m.invoke(this, (Object) componentArray);
                    return;
                }
            }
        } catch (Throwable t) {
            debugLog("Failed to add no-results label: " + t);
        }
    }
}
