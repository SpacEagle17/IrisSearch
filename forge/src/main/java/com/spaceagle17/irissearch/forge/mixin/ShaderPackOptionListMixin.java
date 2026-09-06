package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.forge.ISearchableOptionContainer;
import com.spaceagle17.irissearch.forge.ISearchableOptionList;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderPackOptionList.class)
public abstract class ShaderPackOptionListMixin implements ISearchableOptionList {

    @Shadow private OptionMenuContainer container;
    @Final
    @Shadow private NavigationController navigation;
    @Shadow public abstract void rebuild();

    @Unique private boolean irisSearch$searchModeActive = false;
    @Unique private String irisSearch$typedSearchQuery = "";
    @Unique private int irisSearch$savedCursorPosition = 0;
    @Unique private int irisSearch$reservedLeftWidth = 48;
    @Unique private int irisSearch$listLeft = 0;
    @Unique private int irisSearch$listTop = 0;
    @Unique private int irisSearch$listWidth = 0;

    @Unique private boolean irisSearch$headerBoundsValid = false;
    @Unique private int irisSearch$headerX = 0;
    @Unique private int irisSearch$headerY = 0;
    @Unique private int irisSearch$headerWidth = 0;
    @Unique private int irisSearch$headerHeight = 0;
    @Unique private boolean irisSearch$headerUsesGetterShape = false;

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackOptionListMixin] " + message);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void irisSearch$captureListBounds(ShaderPackScreen screen, NavigationController navigation, ShaderPack pack,
                                              @Coerce Object client, int width, int height, int top, int bottom,
                                              int left, int right, CallbackInfo ci) {
        this.irisSearch$listLeft = left;
        this.irisSearch$listTop = top;
        this.irisSearch$listWidth = right - left;
        irisSearch$debugLog("Captured list bounds: left=" + left + " top=" + top + " width=" + this.irisSearch$listWidth);
    }

    @Override
    public void irisSearch$setListBounds(int left, int top, int width) {
        this.irisSearch$listLeft = left;
        this.irisSearch$listTop = top;
        this.irisSearch$listWidth = width;
    }

    @Override public int irisSearch$getListLeft() { return this.irisSearch$listLeft; }
    @Override public int irisSearch$getListTop() { return this.irisSearch$listTop; }
    @Override public int irisSearch$getListWidth() { return this.irisSearch$listWidth; }

    @Override
    public void irisSearch$publishHeaderRowBounds(int x, int y, int width, int height, boolean usesGetterShape) {
        this.irisSearch$headerX = x;
        this.irisSearch$headerY = y;
        this.irisSearch$headerWidth = width;
        this.irisSearch$headerHeight = height;
        this.irisSearch$headerUsesGetterShape = usesGetterShape;
        this.irisSearch$headerBoundsValid = true;
    }

    @Override public boolean irisSearch$headerRowUsesGetterShape() { return this.irisSearch$headerUsesGetterShape; }

    @Override
    public boolean irisSearch$isOnSubScreen() {
        try {
            return navigation != null && navigation.getCurrentScreen() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public boolean irisSearch$hasHeaderRowBounds() { return this.irisSearch$headerBoundsValid; }
    @Override public int irisSearch$getHeaderRowX() { return this.irisSearch$headerX; }
    @Override public int irisSearch$getHeaderRowY() { return this.irisSearch$headerY; }
    @Override public int irisSearch$getHeaderRowWidth() { return this.irisSearch$headerWidth; }
    @Override public int irisSearch$getHeaderRowHeight() { return this.irisSearch$headerHeight; }

    @Override public boolean irisSearch$isSearchModeActive() { return this.irisSearch$searchModeActive; }
    @Override public String irisSearch$getTypedSearchQuery() { return this.irisSearch$typedSearchQuery; }
    @Override public void irisSearch$setTypedSearchQuery(String query) { this.irisSearch$typedSearchQuery = query != null ? query : ""; }
    @Override public int irisSearch$getSavedCursorPosition() { return this.irisSearch$savedCursorPosition; }
    @Override public void irisSearch$setSavedCursorPosition(int pos) { this.irisSearch$savedCursorPosition = Math.max(0, pos); }
    @Override public int irisSearch$getReservedLeftWidth() { return this.irisSearch$reservedLeftWidth; }
    @Override public void irisSearch$setReservedLeftWidth(int width) { this.irisSearch$reservedLeftWidth = Math.max(0, width); }

    @Override
    public void irisSearch$updateSearchQuery(String query) {
        try {
            this.irisSearch$typedSearchQuery = query != null ? query : "";
            if (this.container != null) {
                ((ISearchableOptionContainer) this.container).irisSearch$setSearchQuery(query);
            }
            this.rebuild();
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't update the search results." + e);
            irisSearch$debugLog("Failed to update search query \"" + query + "\": " + e);
        }
    }

    @Override
    public void irisSearch$disableSearchMode() {
        try {
            this.irisSearch$searchModeActive = false;
            this.irisSearch$typedSearchQuery = "";
            this.irisSearch$savedCursorPosition = 0;
            if (this.container != null) {
                ((ISearchableOptionContainer) this.container).irisSearch$setSearchQuery(null);
            }
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't fully exit search mode." + e);
            irisSearch$debugLog("Failed to disable search mode: " + e);
        }
    }

    @Override
    public void irisSearch$disableSearchModeAndRebuild() {
        this.irisSearch$disableSearchMode();
        try {
            this.rebuild();
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't refresh the option list after closing search." + e);
            irisSearch$debugLog("Failed to rebuild after disabling search mode: " + e);
        }
    }

    @Override
    public void irisSearch$enableSearchModeAndRebuild() {
        try {
            this.irisSearch$searchModeActive = true;
            this.rebuild();
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't enable search mode." + e);
            irisSearch$debugLog("Failed to enable search mode: " + e);
        }
    }

    @Override
    public void irisSearch$restoreSearchState(boolean active, String query, int cursor) {
        this.irisSearch$searchModeActive = active;
        this.irisSearch$typedSearchQuery = query != null ? query : "";
        this.irisSearch$savedCursorPosition = Math.max(0, cursor);
        if (active && this.container != null) {
            try {
                ((ISearchableOptionContainer) this.container).irisSearch$setSearchQuery(this.irisSearch$typedSearchQuery);
            } catch (Exception e) {
                irisSearch$debugLog("Failed to apply restored query to container: " + e);
            }
        }
        try {
            this.rebuild();
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't rebuild after restoring search state." + e);
            irisSearch$debugLog("Failed to rebuild after restoring search state: " + e);
        }
    }

    // After a Ctrl+Click jump, the first back returns the search again with the same query as before

    @Unique private boolean irisSearch$searchReturnArmed = false;
    @Unique private String irisSearch$searchReturnQuery = "";
    @Unique private int irisSearch$searchReturnCursor = 0;

    @Override
    public void irisSearch$armSearchReturn(String query, int cursor) {
        this.irisSearch$searchReturnQuery = query != null ? query : "";
        this.irisSearch$searchReturnCursor = Math.max(0, cursor);
        this.irisSearch$searchReturnArmed = !this.irisSearch$searchReturnQuery.isEmpty();
    }

    @Override
    public boolean irisSearch$isSearchReturnArmed() {
        return this.irisSearch$searchReturnArmed;
    }

    @Override
    public void irisSearch$consumeSearchReturn() {
        if (!this.irisSearch$searchReturnArmed) return;
        String query = this.irisSearch$searchReturnQuery;
        int cursor = this.irisSearch$searchReturnCursor;
        irisSearch$clearSearchReturn();
        irisSearch$restoreSearchState(true, query, cursor);
    }

    @Override
    public void irisSearch$clearSearchReturn() {
        this.irisSearch$searchReturnArmed = false;
        this.irisSearch$searchReturnQuery = "";
        this.irisSearch$searchReturnCursor = 0;
    }
}
