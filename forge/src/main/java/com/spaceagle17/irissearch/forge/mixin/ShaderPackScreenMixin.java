package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.engine.IrisShaderPackTranslations;
import com.spaceagle17.irissearch.forge.ISearchableOptionList;
import com.spaceagle17.irissearch.forge.ISearchablePackList;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.util.PreservedSearchState;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(ShaderPackScreen.class)
public abstract class ShaderPackScreenMixin {

    @Shadow
    private ShaderPackOptionList shaderOptionList;

    @Shadow
    private ShaderPackSelectionList shaderPackList;

    @Shadow
    private boolean optionMenuOpen;

    @Shadow
    private boolean guiHidden;

    @Unique
    private EditBox irisSearch$searchBox;

    @Unique
    private EditBox irisSearch$packSearchBox;

    @Unique
    private ScreenAccessor irisSearch$accessor() {
        return (ScreenAccessor) this;
    }



    // Y coordinate to hide search box off-screen when list is scrolled.
    @Unique
    private static final int OFFSCREEN_Y = -10000;

    // Preserves the search across same-instance flips between the pack-list and options-list views.
    @Unique
    private final PreservedSearchState irisSearch$preserved = new PreservedSearchState();

    // Prevent pack list (always inactive) from overwriting preserved active state from options list.
    @Unique
    private boolean irisSearch$optionsViewWasActive = false;

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackScreenMixin] " + message);
    }

    @Unique
    private ContainerEventHandler irisSearch$focusHandler() {
        return (ContainerEventHandler) this;
    }

    @Inject(method = "m_7856_", at = @At("HEAD"), require = 0, remap = false)
    private void irisSearch$onInitHead(CallbackInfo ci) {
        try {
            if (this.irisSearch$optionsViewWasActive && this.shaderOptionList != null) {
                ISearchableOptionList old = (ISearchableOptionList) this.shaderOptionList;
                irisSearch$preserved.captureFrom(old.irisSearch$getTypedSearchQuery(), old.irisSearch$isSearchModeActive(),
                        old.irisSearch$getSavedCursorPosition(), IrisShaderPackTranslations.getCurrentPackName());
                irisSearch$debugLog("Preserved from options view: active=" + irisSearch$preserved.active + " query=\"" + irisSearch$preserved.query + "\"");
            } else if (this.shaderOptionList == null && PreservedSearchState.pending.active) {
                irisSearch$preserved.copyFrom(PreservedSearchState.pending);
                irisSearch$debugLog("Loaded from static (new instance): query=\"" + irisSearch$preserved.query + "\"");
            } else {
                irisSearch$debugLog("Skipped save (optionsViewWasActive=" + this.irisSearch$optionsViewWasActive + " listNull=" + (this.shaderOptionList == null) + " pending=" + PreservedSearchState.pending.active + ")");
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to preserve search state: " + t);
        }
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), require = 0, remap = false)
    private void irisSearch$onInit(CallbackInfo ci) {
        try {
            this.irisSearch$searchBox = null;

            if (irisSearch$preserved.active && this.optionMenuOpen && this.shaderOptionList != null) {
                if (irisSearch$preserved.matchesCurrentPack()) {
                    try {
                        ((ISearchableOptionList) this.shaderOptionList).irisSearch$restoreSearchState(
                                true, irisSearch$preserved.query, irisSearch$preserved.cursor);
                        irisSearch$debugLog("Restored search state: query=\"" + irisSearch$preserved.query + "\"");
                    } catch (Throwable t) {
                        irisSearch$debugLog("Failed to restore search state: " + t);
                    }
                } else {
                    irisSearch$debugLog("Discarded preserved search state: shader pack changed since it was captured");
                    irisSearch$preserved.clear();
                }
            }

            if (!this.guiHidden && this.optionMenuOpen && this.shaderOptionList != null) {
                this.irisSearch$searchBox = irisSearch$createSearchBox();

                if (this.irisSearch$searchBox != null) {
                    ScreenAccessor sa = irisSearch$accessor();
                    sa.irisSearch$getRenderables().add(this.irisSearch$searchBox);
                    sa.irisSearch$getChildren().add(this.irisSearch$searchBox);
                    sa.irisSearch$getNarratables().add(this.irisSearch$searchBox);
                    irisSearch$debugLog("Search box created during init() and added to renderables");
                } else {
                    irisSearch$debugLog("Search box creation failed during init()");
                }
            }

            this.irisSearch$optionsViewWasActive = this.optionMenuOpen;
            irisSearch$debugLog("optionsViewWasActive=" + this.irisSearch$optionsViewWasActive);
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to add search box during init: " + t);
        }

        try {
            this.irisSearch$packSearchBox = null;

            if (!this.guiHidden && !this.optionMenuOpen && this.shaderPackList != null
                    && ((ISearchablePackList) this.shaderPackList).irisSearch$shouldShowSearchBar()) {
                irisSearch$reserveHeaderSpaceForPackList();
                this.irisSearch$packSearchBox = irisSearch$createPackSearchBox();

                if (this.irisSearch$packSearchBox != null) {
                    ScreenAccessor sa = irisSearch$accessor();
                    sa.irisSearch$getRenderables().add(this.irisSearch$packSearchBox);
                    sa.irisSearch$getChildren().add(this.irisSearch$packSearchBox);
                    sa.irisSearch$getNarratables().add(this.irisSearch$packSearchBox);
                    irisSearch$debugLog("Pack search box created during init() and added to renderables");
                } else {
                    irisSearch$debugLog("Pack search box creation failed during init()");
                }
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to add pack search box during init: " + t);
        }
    }

    @Inject(method = "m_7379_", at = @At("HEAD"), require = 0, remap = false)
    private void irisSearch$onCloseDisableSearch(CallbackInfo ci) {
        try {
            if (this.optionMenuOpen && this.shaderOptionList != null) {
                ISearchableOptionList s = (ISearchableOptionList) this.shaderOptionList;
                PreservedSearchState.pending.captureFrom(s.irisSearch$getTypedSearchQuery(), s.irisSearch$isSearchModeActive(),
                        s.irisSearch$getSavedCursorPosition(), IrisShaderPackTranslations.getCurrentPackName());
                irisSearch$debugLog("onClose: saved pending state active=" + PreservedSearchState.pending.active + " query=\"" + PreservedSearchState.pending.query + "\" pack=\"" + PreservedSearchState.pending.packName + "\"");
            } else if (irisSearch$preserved.active) {
                // Not currently on the options view (e.g. flipped back to the pack list before closing), but
                // irisSearch$preserved still holds the last active search captured when we left it.
                PreservedSearchState.pending.copyFrom(irisSearch$preserved);
                irisSearch$debugLog("onClose: saved pending state from preserved options-view state, query=\"" + PreservedSearchState.pending.query + "\" pack=\"" + PreservedSearchState.pending.packName + "\"");
            } else {
                PreservedSearchState.pending.clear();
                irisSearch$debugLog("onClose: cleared pending state (optionMenuOpen=" + this.optionMenuOpen + ")");
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to save search state on close: " + t);
        }
    }

    @Inject(method = "refreshForChangedPack", at = @At("TAIL"), require = 0, remap = false)
    private void irisSearch$onRefreshForChangedPack(CallbackInfo ci) {
        try {
            if (this.shaderOptionList != null) {
                ISearchableOptionList list = (ISearchableOptionList) this.shaderOptionList;
                if (list.irisSearch$isSearchModeActive()) {
                    String query = list.irisSearch$getTypedSearchQuery();
                    if (!query.isEmpty()) {
                        list.irisSearch$updateSearchQuery(query);
                        irisSearch$debugLog("Re-applied search filter after pack refresh: \"" + query + "\"");
                    }
                }
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to re-apply search after pack refresh: " + t);
        }
    }

    @Unique
    private EditBox irisSearch$createSearchBox() {
        if (this.shaderOptionList == null) {
            return null;
        }

        try {
            EditBox box = new EditBox(irisSearch$accessor().irisSearch$getFont(), 0, 0, 10, 16, Component.translatable("iris_search.option_search.hint"));
            box.setMaxLength(64);
            box.setBordered(true);
            box.setHint(Component.translatable("iris_search.option_search.hint")
                    .withStyle(Style.EMPTY.applyFormats(ChatFormatting.GRAY, ChatFormatting.ITALIC)));

            irisSearch$positionSearchBox(box);

            ISearchableOptionList searchable = (ISearchableOptionList) this.shaderOptionList;
            String savedQuery = searchable.irisSearch$getTypedSearchQuery();
            box.setValue(savedQuery);
            box.setCursorPosition(Math.min(searchable.irisSearch$getSavedCursorPosition(), savedQuery.length()));

            box.setResponder(text -> {
                try {
                    if (this.shaderOptionList == null) {
                        return;
                    }
                    ISearchableOptionList s = (ISearchableOptionList) this.shaderOptionList;
                    s.irisSearch$setTypedSearchQuery(text);
                    s.irisSearch$setSavedCursorPosition(box.getCursorPosition());
                    s.irisSearch$updateSearchQuery(text);
                } catch (Throwable t) {
                    irisSearch$debugLog("Search box responder failed: " + t);
                }
            });

            boolean active = searchable.irisSearch$isSearchModeActive();
            box.setVisible(active);
            if (active) {
                irisSearch$focusSearchBox(box);
            }

            irisSearch$debugLog("Search box created (active=" + active + ")");
            return box;
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to create search box: " + t);
            return null;
        }
    }

    @Unique
    private EditBox irisSearch$createPackSearchBox() {
        if (this.shaderPackList == null) {
            return null;
        }

        try {
            EditBox box = new EditBox(irisSearch$accessor().irisSearch$getFont(), 0, 0, 10, 14, Component.translatable("iris_search.pack_search.hint"));
            box.setMaxLength(64);
            box.setBordered(true);
            box.setHint(Component.translatable("iris_search.pack_search.hint")
                    .withStyle(Style.EMPTY.applyFormats(ChatFormatting.GRAY, ChatFormatting.ITALIC)));

            irisSearch$positionPackSearchBox(box);

            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            String savedQuery = searchable.irisSearch$getTypedSearchQuery();
            box.setValue(savedQuery);
            box.setCursorPosition(Math.min(searchable.irisSearch$getSavedCursorPosition(), savedQuery.length()));

            box.setResponder(text -> {
                try {
                    if (this.shaderPackList == null) {
                        return;
                    }
                    ISearchablePackList s = (ISearchablePackList) this.shaderPackList;
                    s.irisSearch$setTypedSearchQuery(text);
                    s.irisSearch$setSavedCursorPosition(box.getCursorPosition());
                    s.irisSearch$updateSearchQuery(text);
                    irisSearch$resetPackListScroll();
                } catch (Throwable t) {
                    irisSearch$debugLog("Pack search box responder failed: " + t);
                }
            });

            irisSearch$debugLog("Pack search box created");
            return box;
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to create pack search box: " + t);
            return null;
        }
    }

    // centerScrollOn (called by refresh()) no-ops if the previously selected pack isn't in the filtered
    // results, leaving a stale scroll offset that can scroll new results out of view. Force it back to top.
    @Unique
    private void irisSearch$resetPackListScroll() {
        try {
            this.shaderPackList.setScrollAmount(0.0);
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to reset pack list scroll: " + t);
        }
    }

    // Shift y0 down, then refresh() so centerScrollOn recomputes against shrunk visible area.
    @Unique
    private void irisSearch$reserveHeaderSpaceForPackList() {
        if (this.shaderPackList == null) {
            return;
        }
        try {
            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            int reservedHeight = searchable.irisSearch$getReservedHeaderHeight();
            int targetTop = searchable.irisSearch$getListTop() + reservedHeight;
            searchable.irisSearch$setListTop(targetTop);

            // Refresh so centerScrollOn recomputes against the shrunk visible area.
            try {
                this.shaderPackList.refresh();
            } catch (Throwable ignored) {
                // Worst case: list starts scrolled to whichever pack was centered.
            }

            irisSearch$debugLog("Reserved header space above pack list: y0=" + targetTop);
        } catch (Throwable t) {
            irisSearch$debugLog("Could not reserve header space for pack list: " + t);
        }
    }

    // Position box in reserved header strip, centered horizontally with the list's rows.
    @Unique
    private void irisSearch$positionPackSearchBox(EditBox box) {
        if (this.shaderPackList == null || box == null) {
            return;
        }

        try {
            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            int reservedHeight = searchable.irisSearch$getReservedHeaderHeight();
            int rowWidth = searchable.irisSearch$getRowWidth();
            int listX = searchable.irisSearch$getListLeft();
            int listWidth = searchable.irisSearch$getListWidth();

            final int padding = 3;
            int boxHeight = Math.max(10, reservedHeight - padding * 2);
            int rowX = listX + (listWidth - rowWidth) / 2;
            // getListTop() returns current (shifted) y0, so reserved strip is [current - reservedHeight, current).
            int boxY = searchable.irisSearch$getListTop() - reservedHeight + padding;

            box.setX(rowX);
            box.setY(boxY);
            box.setWidth(rowWidth);
            box.setHeight(boxHeight);
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to position pack search box: " + t);
        }
    }

    // Pack box lives in fixed header strip (not inside scrolling rows), so just reposition each frame.
    @Unique
    private void irisSearch$syncPackSearchBox() {
        if (this.irisSearch$packSearchBox == null || this.shaderPackList == null) {
            return;
        }

        try {
            boolean shouldShow = !this.optionMenuOpen && !this.guiHidden;
            if (shouldShow != this.irisSearch$packSearchBox.isVisible()) {
                this.irisSearch$packSearchBox.setVisible(shouldShow);
                if (!shouldShow) {
                    irisSearch$unfocusSearchBox(this.irisSearch$packSearchBox);
                }
            }

            if (shouldShow) {
                irisSearch$positionPackSearchBox(this.irisSearch$packSearchBox);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to sync pack search box: " + t);
        }
    }

    @Unique
    private int irisSearch$liveOrCachedX() {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListLeft();
    }

    @Unique
    private int irisSearch$liveOrCachedY() {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListTop();
    }

    @Unique
    private int irisSearch$liveOrCachedWidth() {
        try {
            return this.shaderOptionList.getWidth();
        } catch (Throwable t) {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListWidth();
        }
    }

    /** Prefers live getX/getY/getWidth over cached constructor-time bounds since Iris may add internal offsets. Falls back to cached bounds on pre-1.20.2 where those getters don't exist. */
    @Unique
    private void irisSearch$positionSearchBox(EditBox box) {
        if (this.shaderOptionList == null || box == null) {
            return;
        }

        final int boxHeight = 16;
        int rowX, rowY, rowWidth, rowHeight;

        try {
            ISearchableOptionList bounds = (ISearchableOptionList) this.shaderOptionList;
            if (bounds.irisSearch$hasHeaderRowBounds()) {
                rowX = bounds.irisSearch$getHeaderRowX();
                rowY = bounds.irisSearch$getHeaderRowY();
                rowWidth = bounds.irisSearch$getHeaderRowWidth();
                rowHeight = bounds.irisSearch$getHeaderRowHeight();
            } else {
                rowWidth = this.shaderOptionList.getRowWidth();
                int listX = irisSearch$liveOrCachedX();
                int listY = irisSearch$liveOrCachedY();
                int listWidth = irisSearch$liveOrCachedWidth();
                rowX = listX + (listWidth - rowWidth) / 2;
                rowY = listY;
                rowHeight = 24;
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed resolving header row bounds, using defaults: " + t);
            rowX = 0; rowY = 0; rowWidth = 220; rowHeight = 24;
        }

        int leftMargin;
        try {
            leftMargin = ((ISearchableOptionList) this.shaderOptionList).irisSearch$getReservedLeftWidth();
        } catch (Throwable t) {
            leftMargin = 48;
        }
        final int rightMargin = 2;
        boolean usesGetterShape;
        try {
            ISearchableOptionList bounds = (ISearchableOptionList) this.shaderOptionList;
            usesGetterShape = bounds.irisSearch$hasHeaderRowBounds() && bounds.irisSearch$headerRowUsesGetterShape();
        } catch (Throwable t) {
            usesGetterShape = false;
        }
        int verticalOffset = usesGetterShape ? 4 : 2;

        int boxX = rowX + leftMargin;
        int boxY = rowY + ((rowHeight - boxHeight) / 2) - verticalOffset;
        int boxWidth = Math.max(40, rowWidth - leftMargin - rightMargin);

        try { box.setX(boxX); } catch (Throwable t) { irisSearch$debugLog("Failed to set search box X position: " + t); }
        try { box.setY(boxY); } catch (Throwable t) { irisSearch$debugLog("Failed to set search box Y position: " + t); }
        try { box.setWidth(boxWidth); } catch (Throwable t) { irisSearch$debugLog("Failed to set search box width: " + t); }
        try { box.setHeight(boxHeight); } catch (Throwable t) { irisSearch$debugLog("Failed to set search box height: " + t); }
    }

    @Unique
    private void irisSearch$focusSearchBox(EditBox box) {
        try {
            box.setFocused(true);
            irisSearch$focusHandler().setFocused(box);
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to focus search box: " + t);
        }
    }

    @Unique
    private void irisSearch$unfocusSearchBox(EditBox box) {
        try {
            box.setFocused(false);
            GuiEventListener current = irisSearch$focusHandler().getFocused();
            if (current == box) {
                irisSearch$focusHandler().setFocused(null);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to unfocus search box: " + t);
        }
    }

    @Unique
    private void irisSearch$syncSearchBoxVisibility() {
        if (this.irisSearch$searchBox == null || this.shaderOptionList == null) {
            return;
        }

        try {
            ISearchableOptionList searchable = (ISearchableOptionList) this.shaderOptionList;
            boolean shouldBeActive = this.optionMenuOpen && searchable.irisSearch$isSearchModeActive();
            boolean currentlyVisible = this.irisSearch$searchBox.isVisible();

            if (shouldBeActive != currentlyVisible) {
                if (shouldBeActive) {
                    String query = searchable.irisSearch$getTypedSearchQuery();
                    this.irisSearch$searchBox.setValue(query);
                    this.irisSearch$searchBox.setCursorPosition(Math.min(searchable.irisSearch$getSavedCursorPosition(), query.length()));

                    irisSearch$positionSearchBox(this.irisSearch$searchBox);
                    this.irisSearch$searchBox.setVisible(true);
                    irisSearch$focusSearchBox(this.irisSearch$searchBox);
                    irisSearch$debugLog("Search box synced to visible/focused");
                } else {
                    this.irisSearch$searchBox.setVisible(false);
                    irisSearch$unfocusSearchBox(this.irisSearch$searchBox);
                    irisSearch$debugLog("Search box synced to hidden/unfocused");
                }
            }

            if (shouldBeActive) {
                irisSearch$updateScrollClipping(this.irisSearch$searchBox);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to sync search box visibility: " + t);
        }
    }

    /** Reflective scroll-amount getter; tries multiple candidate names since Iris renames methods across versions. */
    @Unique
    private double irisSearch$getScrollAmount() {
        for (String name : new String[]{"m_93517_", "method_25341", "method_44387", "getScrollAmount", "scrollAmount"}) {
            try {
                Object result = ReflectionUtils.invokeMethod(this.shaderOptionList, name, new Class<?>[]{});
                if (result instanceof Number) {
                    return ((Number) result).doubleValue();
                }
            } catch (Throwable ignored) {
            }
        }
        irisSearch$debugLog("Could not resolve getScrollAmount()/scrollAmount() on shaderOptionList; scroll-based hiding disabled this frame");
        return 0.0;
    }

    @Unique
    private void irisSearch$updateScrollClipping(EditBox box) {
        double scroll = irisSearch$getScrollAmount();

        if (scroll > 0.5) {
            try {
                box.setY(OFFSCREEN_Y);
            } catch (Throwable t) {
                irisSearch$debugLog("Failed to move search box off-screen while scrolled: " + t);
            }
        } else {
            irisSearch$positionSearchBox(box);
        }
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), require = 0, remap = false)
    private void irisSearch$onRenderHead(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            irisSearch$syncSearchBoxVisibility();
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to sync search box during render: " + t);
        }
        try {
            irisSearch$syncPackSearchBox();
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to sync pack search box during render: " + t);
        }
    }

    @Inject(method = "m_88315_", at = @At("TAIL"), require = 0, remap = false)
    private void irisSearch$onRenderTail(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (this.irisSearch$searchBox != null && this.irisSearch$searchBox.isVisible()) {
                this.irisSearch$searchBox.render(guiGraphics, mouseX, mouseY, delta);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to re-render search box on top: " + t);
        }
        try {
            if (this.irisSearch$packSearchBox != null && this.irisSearch$packSearchBox.isVisible()) {
                this.irisSearch$packSearchBox.render(guiGraphics, mouseX, mouseY, delta);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to re-render pack search box on top: " + t);
        }
    }

    // -- Old key API (keyPressed with int keyCode, int scanCode, int modifiers) --

    @Inject(method = "m_7933_(III)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void irisSearch$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean isEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
            boolean ctrlDown = Screen.hasControlDown();

            if (irisSearch$handleSearchKeyPress(keyCode, ctrlDown, isEscape)) {
                cir.setReturnValue(true);
                return;
            }
            if (irisSearch$handlePackSearchKeyPress(keyCode, ctrlDown, isEscape)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed keyPressed handling: " + t);
        }
    }

    /** Mutually exclusive with irisSearch$handleSearchKeyPress via the !optionMenuOpen guard, since only one of the two search boxes is ever showing at a time. */
    @Unique
    private boolean irisSearch$handlePackSearchKeyPress(int key, boolean ctrlDown, boolean isEscapeKey) {
        if (this.shaderPackList == null || this.optionMenuOpen || this.irisSearch$packSearchBox == null) {
            return false;
        }

        try {
            if (isEscapeKey && this.irisSearch$packSearchBox.isFocused()) {
                ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
                String query = searchable.irisSearch$getTypedSearchQuery();
                if (!query.isEmpty()) {
                    this.irisSearch$packSearchBox.setValue("");
                    searchable.irisSearch$updateSearchQuery("");
                    irisSearch$debugLog("Escape pressed while pack-searching, cleared query");
                } else {
                    irisSearch$unfocusSearchBox(this.irisSearch$packSearchBox);
                    irisSearch$debugLog("Escape pressed while pack-searching, unfocused box");
                }
                return true;
            }

            if (ctrlDown && key == GLFW.GLFW_KEY_F) {
                GuiUtil.playButtonClickSound();
                if (this.irisSearch$packSearchBox.isFocused()) {
                    irisSearch$unfocusSearchBox(this.irisSearch$packSearchBox);
                    irisSearch$debugLog("Ctrl+F unfocused the pack search box");
                } else {
                    irisSearch$focusSearchBox(this.irisSearch$packSearchBox);
                    irisSearch$debugLog("Ctrl+F focused the pack search box");
                }
                return true;
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to handle pack search key press: " + t);
        }

        return false;
    }

    @Unique
    private boolean irisSearch$handleSearchKeyPress(int key, boolean ctrlDown, boolean isEscapeKey) {
        if (this.shaderOptionList == null) {
            return false;
        }

        try {
            ISearchableOptionList searchable = (ISearchableOptionList) this.shaderOptionList;

            if (isEscapeKey && searchable.irisSearch$isSearchModeActive()) {
                searchable.irisSearch$disableSearchModeAndRebuild();
                irisSearch$debugLog("Escape pressed while searching, search mode disabled");
                return true;
            }

            if (ctrlDown && key == GLFW.GLFW_KEY_F && this.optionMenuOpen && !searchable.irisSearch$isOnSubScreen()) {
                GuiUtil.playButtonClickSound();

                if (searchable.irisSearch$isSearchModeActive()) {
                    searchable.irisSearch$disableSearchModeAndRebuild();
                } else {
                    searchable.irisSearch$enableSearchModeAndRebuild();
                }

                irisSearch$debugLog("Ctrl+F toggled search mode");
                return true;
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to handle search key press: " + t);
        }

        return false;
    }

    // -- Old mouse API (mouseClicked with double x, double y, int button) --

    @Dynamic
    @Inject(method = "m_6375_", at = @At("HEAD"), cancellable = true, require = 0)
    private void irisSearch$onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (this.optionMenuOpen && this.irisSearch$searchBox != null && this.irisSearch$searchBox.isVisible()) {
                if (this.irisSearch$searchBox.mouseClicked(mouseX, mouseY, button)) {
                    irisSearch$focusSearchBox(this.irisSearch$searchBox);
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (!this.optionMenuOpen && this.irisSearch$packSearchBox != null && this.irisSearch$packSearchBox.isVisible()) {
                if (this.irisSearch$packSearchBox.mouseClicked(mouseX, mouseY, button)) {
                    irisSearch$focusSearchBox(this.irisSearch$packSearchBox);
                    cir.setReturnValue(true);
                }
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed mouseClicked handling: " + t);
        }
    }
}
