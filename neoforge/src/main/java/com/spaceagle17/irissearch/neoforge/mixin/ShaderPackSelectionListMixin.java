package com.spaceagle17.irissearch.neoforge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.engine.ShaderPackSearchEngine;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.neoforge.ISearchablePackList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Filters via iterator redirect, not post-hoc removal—entries store construction-time index.
 * Redirect also lets names.isEmpty() checks see true pack count.
 */
@Mixin(ShaderPackSelectionList.class)
public abstract class ShaderPackSelectionListMixin implements ISearchablePackList {

    @Shadow public abstract void refresh();

    @Shadow public abstract void addLabelEntries(Component... lines);

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

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackSelectionListMixin] " + message);
    }

    // Inject at INVOKE of refresh() (not TAIL—proved unreliable), use @Local for bounds, guard on isInitialized.
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

    // Redirect iterator, not the names list itself—Iris's isEmpty() check runs after the loop.
    @Redirect(method = "refresh", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"), require = 0, remap = false)
    private Iterator<String> irisSearch$filterNamesForIteration(List<String> names) {
        try {
            String query = this.irisSearch$typedSearchQuery.trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                this.irisSearch$lastFilterHadNoMatches = false;
                return names.iterator();
            }

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
            try {
                this.addLabelEntries(Component.translatable("iris_search.search.no_results"));
            } catch (Throwable t) {
                debugLog("Failed to add no-results label: " + t);
            }
        }
    }
}
