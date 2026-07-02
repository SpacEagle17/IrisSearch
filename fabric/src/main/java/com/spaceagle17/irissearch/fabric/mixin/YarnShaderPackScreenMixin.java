package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.fabric.ISearchableOptionList;
import com.spaceagle17.irissearch.fabric.ISearchablePackList;
import com.spaceagle17.irissearch.fabric.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
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
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("NameDoesntMatchTargetClass")
@Mixin(ShaderPackScreen.class)
public abstract class YarnShaderPackScreenMixin {

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

    // Moves the search box off-screen when the list is scrolled so it doesn't float above content.
    @Unique
    private static final int OFFSCREEN_Y = -10000;

    @Unique
    private boolean irisSearch$preservedSearchMode = false;

    @Unique
    private String irisSearch$preservedSearchQuery = "";

    @Unique
    private int irisSearch$preservedCursorPosition = 0;

    @Unique
    private boolean irisSearch$optionsViewWasActive = false;

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[YarnShaderPackScreenMixin] " + message);
    }

    @Unique
    private ScreenAccessor irisSearch$accessor() {
        return (ScreenAccessor) this;
    }

    @Unique
    private ContainerEventHandler irisSearch$focusHandler() {
        return (ContainerEventHandler) this;
    }

    @Inject(method = "init", at = @At("HEAD"), require = 0)
    private void irisSearch$onInitHead(CallbackInfo ci) {
        try {
            if (this.irisSearch$optionsViewWasActive && this.shaderOptionList != null) {
                ISearchableOptionList old = (ISearchableOptionList) this.shaderOptionList;
                String q = old.irisSearch$getTypedSearchQuery();
                this.irisSearch$preservedSearchMode = old.irisSearch$isSearchModeActive() && !q.isEmpty();
                this.irisSearch$preservedSearchQuery = q;
                this.irisSearch$preservedCursorPosition = old.irisSearch$getSavedCursorPosition();
                debugLog("Preserved from options view: active=" + this.irisSearch$preservedSearchMode + " query=\"" + q + "\"");
            } else if (this.shaderOptionList == null && IrisSearch.pendingSearchRestore) {
                this.irisSearch$preservedSearchMode = true;
                this.irisSearch$preservedSearchQuery = IrisSearch.pendingSearchQuery;
                this.irisSearch$preservedCursorPosition = IrisSearch.pendingSearchCursor;
                debugLog("Loaded from static (new instance): query=\"" + IrisSearch.pendingSearchQuery + "\"");
            } else {
                debugLog("Skipped save (optionsViewWasActive=" + this.irisSearch$optionsViewWasActive + " listNull=" + (this.shaderOptionList == null) + " pending=" + IrisSearch.pendingSearchRestore + ")");
            }
        } catch (Throwable t) {
            debugLog("Failed to preserve search state: " + t);
        }
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void irisSearch$onInit(CallbackInfo ci) {
        try {
            this.irisSearch$searchBox = null;

            if (this.irisSearch$preservedSearchMode && this.optionMenuOpen && this.shaderOptionList != null) {
                try {
                    ((ISearchableOptionList) this.shaderOptionList).irisSearch$restoreSearchState(
                            true, this.irisSearch$preservedSearchQuery, this.irisSearch$preservedCursorPosition);
                    debugLog("Restored search state: query=\"" + this.irisSearch$preservedSearchQuery + "\"");
                } catch (Throwable t) {
                    debugLog("Failed to restore search state: " + t);
                }
            }

            if (!this.guiHidden && this.optionMenuOpen && this.shaderOptionList != null) {
                this.irisSearch$searchBox = irisSearch$createSearchBox();

                if (this.irisSearch$searchBox != null) {
                    irisSearch$accessor().irisSearch$invokeAddRenderableWidget(this.irisSearch$searchBox);
                    debugLog("Search box created during init() and added to renderables");
                } else {
                    debugLog("Search box creation failed during init()");
                }
            }

            this.irisSearch$optionsViewWasActive = this.optionMenuOpen;
            debugLog("optionsViewWasActive=" + this.irisSearch$optionsViewWasActive);
        } catch (Throwable t) {
            debugLog("Failed to add search box during init: " + t);
        }

        try {
            this.irisSearch$packSearchBox = null;

            if (!this.guiHidden && !this.optionMenuOpen && this.shaderPackList != null) {
                irisSearch$reserveHeaderSpaceForPackList();
                this.irisSearch$packSearchBox = irisSearch$createPackSearchBox();

                if (this.irisSearch$packSearchBox != null) {
                    irisSearch$accessor().irisSearch$invokeAddRenderableWidget(this.irisSearch$packSearchBox);
                    debugLog("Pack search box created during init() and added to renderables");
                } else {
                    debugLog("Pack search box creation failed during init()");
                }
            }
        } catch (Throwable t) {
            debugLog("Failed to add pack search box during init: " + t);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), require = 0)
    private void irisSearch$onCloseDisableSearch(CallbackInfo ci) {
        try {
            if (this.optionMenuOpen && this.shaderOptionList != null) {
                ISearchableOptionList s = (ISearchableOptionList) this.shaderOptionList;
                String q = s.irisSearch$getTypedSearchQuery();
                IrisSearch.pendingSearchRestore = s.irisSearch$isSearchModeActive() && !q.isEmpty();
                IrisSearch.pendingSearchQuery = q;
                IrisSearch.pendingSearchCursor = s.irisSearch$getSavedCursorPosition();
                debugLog("onClose: saved pending state active=" + IrisSearch.pendingSearchRestore + " query=\"" + q + "\"");
            } else {
                IrisSearch.pendingSearchRestore = false;
                IrisSearch.pendingSearchQuery = "";
                IrisSearch.pendingSearchCursor = 0;
                debugLog("onClose: cleared pending state (optionMenuOpen=" + this.optionMenuOpen + ")");
            }
        } catch (Throwable t) {
            debugLog("Failed to save search state on close: " + t);
        }
    }

    @Unique
    private EditBox irisSearch$createSearchBox() {
        if (this.shaderOptionList == null) {
            return null;
        }

        try {
            EditBox box = new EditBox(irisSearch$accessor().irisSearch$getFont(), 0, 0, 10, 16, Component.translatable("iris_search.search.hint"));
            box.setMaxLength(64);
            box.setBordered(true);
            box.setHint(Component.translatable("iris_search.search.hint")
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
                    debugLog("Search box responder failed: " + t);
                }
            });

            boolean active = searchable.irisSearch$isSearchModeActive();
            box.setVisible(active);
            if (active) {
                irisSearch$focusSearchBox(box);
            }

            debugLog("Search box created (active=" + active + ")");
            return box;
        } catch (Throwable t) {
            debugLog("Failed to create search box: " + t);
            return null;
        }
    }

    @Unique
    private EditBox irisSearch$createPackSearchBox() {
        if (this.shaderPackList == null) {
            return null;
        }

        try {
            EditBox box = new EditBox(irisSearch$accessor().irisSearch$getFont(), 0, 0, 10, 14, Component.translatable("iris_search.search.pack_hint"));
            box.setMaxLength(64);
            box.setBordered(true);
            box.setHint(Component.translatable("iris_search.search.pack_hint")
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
                    debugLog("Pack search box responder failed: " + t);
                }
            });

            debugLog("Pack search box created");
            return box;
        } catch (Throwable t) {
            debugLog("Failed to create pack search box: " + t);
            return null;
        }
    }

    // centerScrollOn (called by refresh()) no-ops if the previously selected pack isn't in the filtered
    // results, leaving a stale scroll offset that can scroll new results out of view. Force it back to top.
    @Unique
    private void irisSearch$resetPackListScroll() {
        for (String name : new String[]{"method_44382", "method_25307"}) {
            try {
                ReflectionUtils.invokeMethod(this.shaderPackList, name, new Class<?>[]{double.class}, 0.0);
                return;
            } catch (Throwable ignored) {
            }
        }
        debugLog("Could not resolve setScrollAmount(double) on shaderPackList; scroll reset skipped this call");
    }

    @Unique
    private void irisSearch$reserveHeaderSpaceForPackList() {
        if (this.shaderPackList == null) {
            return;
        }
        try {
            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            int reservedHeight = searchable.irisSearch$getReservedHeaderHeight();
            int targetY = searchable.irisSearch$getListTop() + reservedHeight;
            int targetHeight = Math.max(20, searchable.irisSearch$getListBottom() - reservedHeight);

            try {
                this.shaderPackList.setY(targetY);
                this.shaderPackList.setHeight(targetHeight);
                try {
                    this.shaderPackList.refresh();
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) { // This is for 1.20.1 ewwww
                debugLog("setY()/setHeight() unavailable: " + t);
                int rawTargetTop = (searchable.irisSearch$getListTop() - 4) + reservedHeight;
                boolean applied = ReflectionUtils.setFieldValue(this.shaderPackList, "field_19085", rawTargetTop);
                debugLog("Field write for list top (field_19085 -> " + rawTargetTop + "): applied=" + applied);
            }
            debugLog("Reserved header space above pack list: y=" + targetY + " height=" + targetHeight);
        } catch (Throwable t) {
            debugLog("Could not reserve header space for pack list: " + t);
        }
    }

    @Unique
    private void irisSearch$positionPackSearchBox(EditBox box) {
        if (this.shaderPackList == null || box == null) {
            return;
        }

        try {
            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            int reservedHeight = searchable.irisSearch$getReservedHeaderHeight();
            int rowWidth = irisSearch$liveOrCachedPackRowWidth();
            int listX = irisSearch$liveOrCachedPackX();
            int listWidth = irisSearch$liveOrCachedPackWidth();

            final int padding = 3;
            int boxHeight = Math.max(10, reservedHeight - padding * 2);
            int rowX = listX + (listWidth - rowWidth) / 2;
            int boxY = searchable.irisSearch$getListTop() + padding;

            try { box.setX(rowX); } catch (Throwable t) { debugLog("Failed to set pack search box X position: " + t); }
            try { box.setY(boxY); } catch (Throwable t) { debugLog("Failed to set pack search box Y position: " + t); }
            try { box.setWidth(rowWidth); } catch (Throwable t) { debugLog("Failed to set pack search box width: " + t); }
            try {
                box.setHeight(boxHeight);
            } catch (Throwable t) { // Again for 1.20.1 ewwwww
                debugLog("Failed to set pack search box height, falling back to field write: " + t);
                boolean applied = ReflectionUtils.setFieldValue(box, "field_22759", boxHeight);
                debugLog("Field write for box height (field_22759): applied=" + applied);
            }
        } catch (Throwable t) {
            debugLog("Failed to position pack search box: " + t);
        }
    }

    /** getRowWidth() overrides a vanilla method, so its intermediary id isn't guaranteed to resolve the same way across very different Minecraft generations -- fall back to the value ShaderPackSelectionListMixin computed independently from Iris's own formula. */
    @Unique
    private int irisSearch$liveOrCachedPackRowWidth() {
        try {
            return this.shaderPackList.getRowWidth();
        } catch (Throwable t) {
            return ((ISearchablePackList) this.shaderPackList).irisSearch$getRowWidth();
        }
    }

    @Unique
    private int irisSearch$liveOrCachedPackX() {
        try {
            return this.shaderPackList.getX();
        } catch (Throwable t) {
            return ((ISearchablePackList) this.shaderPackList).irisSearch$getListLeft();
        }
    }

    @Unique
    private int irisSearch$liveOrCachedPackWidth() {
        try {
            return this.shaderPackList.getWidth();
        } catch (Throwable t) {
            return ((ISearchablePackList) this.shaderPackList).irisSearch$getListWidth();
        }
    }

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
            debugLog("Failed to sync pack search box: " + t);
        }
    }

    @Unique
    private int irisSearch$liveOrCachedX() {
        try {
            return this.shaderOptionList.getX();
        } catch (Throwable t) {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListLeft();
        }
    }

    @Unique
    private int irisSearch$liveOrCachedY() {
        try {
            return this.shaderOptionList.getY();
        } catch (Throwable t) {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListTop();
        }
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
            debugLog("Failed resolving header row bounds, using defaults: " + t);
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

        try { box.setX(boxX); } catch (Throwable t) { debugLog("Failed to set search box X position: " + t); }
        try { box.setY(boxY); } catch (Throwable t) { debugLog("Failed to set search box Y position: " + t); }
        try { box.setWidth(boxWidth); } catch (Throwable t) { debugLog("Failed to set search box width: " + t); }
        try { box.setHeight(boxHeight); } catch (Throwable t) { debugLog("Failed to set search box height: " + t); }
    }

    @Unique
    private void irisSearch$focusSearchBox(EditBox box) {
        try {
            box.setFocused(true);
            irisSearch$focusHandler().setFocused(box);
        } catch (Throwable t) {
            debugLog("Failed to focus search box: " + t);
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
            debugLog("Failed to unfocus search box: " + t);
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
                    debugLog("Search box synced to visible/focused");
                } else {
                    this.irisSearch$searchBox.setVisible(false);
                    irisSearch$unfocusSearchBox(this.irisSearch$searchBox);
                    debugLog("Search box synced to hidden/unfocused");
                }
            }

            if (shouldBeActive) {
                irisSearch$updateScrollClipping(this.irisSearch$searchBox);
            }
        } catch (Throwable t) {
            debugLog("Failed to sync search box visibility: " + t);
        }
    }

    /** Reflective scroll-amount getter; tries multiple candidate names since Iris renames methods across versions. */
    @Unique
    private double irisSearch$getScrollAmount() {
        for (String name : new String[]{"method_25341", "method_44387", "getScrollAmount", "scrollAmount"}) {
            try {
                Object result = ReflectionUtils.invokeMethod(this.shaderOptionList, name, new Class<?>[]{});
                if (result instanceof Number) {
                    return ((Number) result).doubleValue();
                }
            } catch (Throwable ignored) {
            }
        }
        debugLog("Could not resolve getScrollAmount()/scrollAmount() on shaderOptionList; scroll-based hiding disabled this frame");
        return 0.0;
    }

    @Unique
    private void irisSearch$updateScrollClipping(EditBox box) {
        double scroll = irisSearch$getScrollAmount();

        if (scroll > 0.5) {
            try {
                box.setY(OFFSCREEN_Y);
            } catch (Throwable t) {
                debugLog("Failed to move search box off-screen while scrolled: " + t);
            }
        } else {
            irisSearch$positionSearchBox(box);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void irisSearch$onRenderHead(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            irisSearch$syncSearchBoxVisibility();
        } catch (Throwable t) {
            debugLog("Failed to sync search box during render: " + t);
        }
        try {
            irisSearch$syncPackSearchBox();
        } catch (Throwable t) {
            debugLog("Failed to sync pack search box during render: " + t);
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void irisSearch$onRenderTail(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (this.irisSearch$searchBox != null && this.irisSearch$searchBox.isVisible()) {
                this.irisSearch$searchBox.render(guiGraphics, mouseX, mouseY, delta);
            }
        } catch (Throwable t) {
            debugLog("Failed to re-render search box on top: " + t);
        }
        try {
            if (this.irisSearch$packSearchBox != null && this.irisSearch$packSearchBox.isVisible()) {
                this.irisSearch$packSearchBox.render(guiGraphics, mouseX, mouseY, delta);
            }
        } catch (Throwable t) {
            debugLog("Failed to re-render pack search box on top: " + t);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
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
            debugLog("Failed keyPressed handling: " + t);
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
                    irisSearch$resetPackListScroll();
                    debugLog("Escape pressed while pack-searching, cleared query");
                } else {
                    irisSearch$unfocusSearchBox(this.irisSearch$packSearchBox);
                    debugLog("Escape pressed while pack-searching, unfocused box");
                }
                return true;
            }

            if (ctrlDown && key == GLFW.GLFW_KEY_F) {
                GuiUtil.playButtonClickSound();
                irisSearch$focusSearchBox(this.irisSearch$packSearchBox);
                debugLog("Ctrl+F focused the pack search box");
                return true;
            }
        } catch (Throwable t) {
            debugLog("Failed to handle pack search key press: " + t);
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
                debugLog("Escape pressed while searching, search mode disabled");
                return true;
            }

            if (ctrlDown && key == GLFW.GLFW_KEY_F && this.optionMenuOpen && !searchable.irisSearch$isOnSubScreen()) {
                GuiUtil.playButtonClickSound();

                if (searchable.irisSearch$isSearchModeActive()) {
                    searchable.irisSearch$disableSearchModeAndRebuild();
                } else {
                    searchable.irisSearch$enableSearchModeAndRebuild();
                }

                debugLog("Ctrl+F toggled search mode");
                return true;
            }
        } catch (Throwable t) {
            debugLog("Failed to handle search key press: " + t);
        }

        return false;
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
                        debugLog("Re-applied search filter after pack refresh: \"" + query + "\"");
                    }
                }
            }
        } catch (Throwable t) {
            debugLog("Failed to re-apply search after pack refresh: " + t);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
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
            debugLog("Failed mouseClicked handling: " + t);
        }
    }

    @Dynamic
    @Inject(method = "method_25404(Lnet/minecraft/class_11908;)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onKeyPressedEvent(@Coerce Object event, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (event == null) return;

            Object keyObj = null;
            for (String name : new String[]{"key", "comp_4795"}) {
                try {
                    keyObj = ReflectionUtils.invokeMethod(event, name, new Class<?>[]{});
                    if (keyObj != null) break;
                } catch (Throwable ignored) {}
            }
            int key = keyObj instanceof Integer ? (Integer) keyObj : -1;
            boolean isEscape = key == GLFW.GLFW_KEY_ESCAPE;

            boolean ctrlDown = MinecraftBridge.isControlDown();
            debugLog("keyPressed(event): key=" + key + " isEscape=" + isEscape + " ctrlDown=" + ctrlDown);

            if (irisSearch$handleSearchKeyPress(key, ctrlDown, isEscape)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            debugLog("Failed event-based keyPressed handling: " + t);
        }
    }

    @Dynamic
    @Inject(method = "method_25402(Lnet/minecraft/class_11909;Z)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onMouseClickedEvent(@Coerce Object event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!this.optionMenuOpen || this.irisSearch$searchBox == null || !this.irisSearch$searchBox.isVisible() || event == null) {
                return;
            }
            Object result = ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "method_25402",
                    new Class<?>[]{event.getClass(), boolean.class}, event, doubleClick);
            if (Boolean.TRUE.equals(result)) {
                irisSearch$focusSearchBox(this.irisSearch$searchBox);
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            debugLog("Failed event-based mouseClicked handling: " + t);
        }
    }
}
