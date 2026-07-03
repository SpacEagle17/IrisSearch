package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.config.ConfigHandler;
import com.spaceagle17.irissearch.engine.ShaderPackSearchEngine;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.forge.ISearchablePackList;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

@Mixin(ShaderPackSelectionList.class)
public abstract class ShaderPackSelectionListMixin implements ISearchablePackList {

    @Shadow public abstract void refresh();

    @Shadow public abstract void addLabelEntries(Component... lines);

    /** Extra vertical space reserved above the list's rows for the always-visible pack search box. */
    @Unique private static final int RESERVED_HEADER_HEIGHT = 20;

    @Unique private String irisSearch$typedSearchQuery = "";
    @Unique private int irisSearch$savedCursorPosition = 0;
    @Unique private boolean irisSearch$lastFilterHadNoMatches = false;
    @Unique private int irisSearch$packCount = 0;

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackSelectionListMixin] " + message);
    }

    @Unique
    private static int irisSearch$getIntField(Object target, String... candidateNames) {
        for (String name : candidateNames) {
            Object value = ReflectionUtils.getFieldValue(target, name);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        }
        debugLog("Failed to read any candidate field of " + Arrays.toString(candidateNames));
        return 0;
    }

    @Unique
    private static boolean irisSearch$setIntField(Object target, int value, String... candidateNames) {
        for (String name : candidateNames) {
            if (ReflectionUtils.setFieldValue(target, name, value)) {
                return true;
            }
        }
        return false;
    }

    @Override public String irisSearch$getTypedSearchQuery() { return this.irisSearch$typedSearchQuery; }
    @Override public void irisSearch$setTypedSearchQuery(String query) { this.irisSearch$typedSearchQuery = query != null ? query : ""; }
    @Override public int irisSearch$getSavedCursorPosition() { return this.irisSearch$savedCursorPosition; }
    @Override public void irisSearch$setSavedCursorPosition(int pos) { this.irisSearch$savedCursorPosition = Math.max(0, pos); }
    @Override public int irisSearch$getReservedHeaderHeight() { return RESERVED_HEADER_HEIGHT; }
    @Override public int irisSearch$getListLeft() { return irisSearch$getIntField(this, "x0", "f_93393_"); }
    @Override public int irisSearch$getListTop() { return irisSearch$getIntField(this, "y0", "f_93390_"); }
    @Override public int irisSearch$getListWidth() { return irisSearch$getIntField(this, "x1", "f_93392_") - irisSearch$getIntField(this, "x0", "f_93393_"); }
    @Override public int irisSearch$getRowWidth() { return Math.min(308, irisSearch$getIntField(this, "width", "f_93388_") - 50); }
    @Override public boolean irisSearch$shouldShowSearchBar() { return this.irisSearch$packCount >= ConfigHandler.minPacksForSelectionSearch; }

    @Override
    public void irisSearch$setListTop(int top) {
        boolean applied = irisSearch$setIntField(this, top, "y0", "f_93390_");
        debugLog("Field write for list top (y0/f_93390_ -> " + top + "): applied=" + applied);
    }

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

    // Redirect iterator, not the names list itself—Iris's isEmpty() check runs after the loop,
    // and we must not defeat that by filtering the input before the loop sees it.
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
            try {
                this.addLabelEntries(Component.translatable("iris_search.search.no_results"));
            } catch (Throwable t) {
                debugLog("Failed to add no-results label: " + t);
            }
        }
    }
}
