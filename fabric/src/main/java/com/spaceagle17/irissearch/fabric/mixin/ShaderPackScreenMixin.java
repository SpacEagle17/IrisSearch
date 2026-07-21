package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.engine.IrisShaderPackTranslations;
import com.spaceagle17.irissearch.fabric.ISearchableOptionList;
import com.spaceagle17.irissearch.fabric.ISearchablePackList;
import com.spaceagle17.irissearch.fabric.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.util.PreservedSearchState;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.ShaderPackSelectionList;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Reflective-only counterpart to YarnShaderPackScreenMixin, for the official-mapped
 * Minecraft 26.1 shape this project has no compile-time access to. See
 * YarnShaderPackScreenMixin for the typed equivalent on the project's real compile target.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.screen.ShaderPackScreen", remap = false)
public class ShaderPackScreenMixin {

    @Shadow
    private ShaderPackOptionList shaderOptionList;

    @Shadow
    private ShaderPackSelectionList shaderPackList;

    @Shadow
    private boolean optionMenuOpen;

    @Shadow
    private boolean guiHidden;

    @Unique
    private Object irisSearch$searchBox;

    @Unique
    private Object irisSearch$packSearchBox;

    @Unique
    private Method irisSearch$screenSetFocusedMethod;

    @Unique
    private static final int OFFSCREEN_Y = -10000;

    // Preserves the search across same-instance flips between the pack-list and options-list views.
    @Unique
    private final PreservedSearchState irisSearch$preserved = new PreservedSearchState();

    // Prevent pack list (always inactive) from overwriting preserved active state from options list.
    @Unique
    private boolean irisSearch$optionsViewWasActive = false;

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackScreenMixin] " + message);
    }

    @Inject(method = "init", at = @At("HEAD"), require = 0, remap = false)
    private void irisSearch$onInitHead(CallbackInfo ci) {
        try {
            if (this.irisSearch$optionsViewWasActive && this.shaderOptionList != null) {
                ISearchableOptionList old = (ISearchableOptionList) this.shaderOptionList;
                irisSearch$preserved.captureFrom(old.irisSearch$getTypedSearchQuery(), old.irisSearch$isSearchModeActive(),
                        old.irisSearch$getSavedCursorPosition(), IrisShaderPackTranslations.getCurrentPackName());
                debugLog("Preserved from options view: active=" + irisSearch$preserved.active + " query=\"" + irisSearch$preserved.query + "\"");
            } else if (this.shaderOptionList == null && PreservedSearchState.pending.active) {
                irisSearch$preserved.copyFrom(PreservedSearchState.pending);
                debugLog("Loaded from static (new instance): query=\"" + irisSearch$preserved.query + "\"");
            } else {
                debugLog("Skipped save (optionsViewWasActive=" + this.irisSearch$optionsViewWasActive + " listNull=" + (this.shaderOptionList == null) + " pending=" + PreservedSearchState.pending.active + ")");
            }
        } catch (Throwable t) {
            debugLog("Failed to preserve search state: " + t);
        }
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void irisSearch$onInit(CallbackInfo ci) {
        try {
            this.irisSearch$searchBox = null;
            this.irisSearch$screenSetFocusedMethod = null;

            if (irisSearch$preserved.active && this.optionMenuOpen && this.shaderOptionList != null) {
                if (irisSearch$preserved.matchesCurrentPack()) {
                    try {
                        ((ISearchableOptionList) this.shaderOptionList).irisSearch$restoreSearchState(
                                true, irisSearch$preserved.query, irisSearch$preserved.cursor);
                        debugLog("Restored search state: query=\"" + irisSearch$preserved.query + "\"");
                    } catch (Throwable t) {
                        debugLog("Failed to restore search state: " + t);
                    }
                } else {
                    debugLog("Discarded preserved search state: shader pack changed since it was captured");
                    irisSearch$preserved.clear();
                }
            }

            if (!this.guiHidden && this.optionMenuOpen && this.shaderOptionList != null) {
                this.irisSearch$searchBox = irisSearch$createSearchBox();

                if (this.irisSearch$searchBox != null) {
                    boolean added = MinecraftBridge.invokeWithInterfaceParam(this, "addRenderableWidget", this.irisSearch$searchBox);
                    debugLog("Search box created during init(), added=" + added);
                } else {
                    debugLog("Search box creation failed during init()");
                }
            }

            this.irisSearch$optionsViewWasActive = this.optionMenuOpen;
            debugLog("optionsViewWasActive=" + this.irisSearch$optionsViewWasActive);
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to add search box during init." + t);
            debugLog("Failed to add search box during init: " + t);
        }

        try {
            this.irisSearch$packSearchBox = null;

            if (!this.guiHidden && !this.optionMenuOpen && this.shaderPackList != null
                    && ((ISearchablePackList) this.shaderPackList).irisSearch$shouldShowSearchBar()) {
                irisSearch$reserveHeaderSpaceForPackList();
                this.irisSearch$packSearchBox = irisSearch$createPackSearchBox();

                if (this.irisSearch$packSearchBox != null) {
                    boolean added = MinecraftBridge.invokeWithInterfaceParam(this, "addRenderableWidget", this.irisSearch$packSearchBox);
                    debugLog("Pack search box created during init(), added=" + added);
                } else {
                    debugLog("Pack search box creation failed during init()");
                }
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to add pack search box during init." + t);
            debugLog("Failed to add pack search box during init: " + t);
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), require = 0)
    private void irisSearch$onCloseDisableSearch(CallbackInfo ci) {
        try {
            if (this.optionMenuOpen && this.shaderOptionList != null) {
                ISearchableOptionList s = (ISearchableOptionList) this.shaderOptionList;
                PreservedSearchState.pending.captureFrom(s.irisSearch$getTypedSearchQuery(), s.irisSearch$isSearchModeActive(),
                        s.irisSearch$getSavedCursorPosition(), IrisShaderPackTranslations.getCurrentPackName());
                debugLog("onClose: saved pending state active=" + PreservedSearchState.pending.active + " query=\"" + PreservedSearchState.pending.query + "\" pack=\"" + PreservedSearchState.pending.packName + "\"");
            } else if (irisSearch$preserved.active) {
                // Not currently on the options view (e.g. flipped back to the pack list before closing), but
                // irisSearch$preserved still holds the last active search captured when we left it.
                PreservedSearchState.pending.copyFrom(irisSearch$preserved);
                debugLog("onClose: saved pending state from preserved options-view state, query=\"" + PreservedSearchState.pending.query + "\" pack=\"" + PreservedSearchState.pending.packName + "\"");
            } else {
                PreservedSearchState.pending.clear();
                debugLog("onClose: cleared pending state (optionMenuOpen=" + this.optionMenuOpen + ")");
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to save search state on close." + t);
            debugLog("Failed to save search state on close: " + t);
        }
    }

    @Unique
    private Object irisSearch$createSearchBox() {
        if (this.shaderOptionList == null) {
            return null;
        }

        try {
            Object font = ReflectionUtils.getFieldValue(this, "font");
            if (font == null) {
                debugLog("Could not locate font field on screen");
                return null;
            }

            Class<?> editBoxClass = MinecraftBridge.resolveClass("net.minecraft.client.gui.components.EditBox");
            if (editBoxClass == null) {
                debugLog("Could not resolve EditBox class");
                return null;
            }

            Object hintComponent = MinecraftBridge.createTranslatableComponent("iris_search.option_search.hint");
            if (hintComponent == null) {
                debugLog("Could not create narration/hint text components");
                return null;
            }

            Object box = MinecraftBridge.instantiate(editBoxClass, font, 0, 0, 10, 16, hintComponent);
            if (box == null) {
                debugLog("Could not find a matching EditBox constructor");
                return null;
            }

            ReflectionUtils.invokeMethod(box, "setMaxLength", new Class<?>[]{int.class}, 64);
            ReflectionUtils.invokeMethod(box, "setBordered", new Class<?>[]{boolean.class}, true);
            MinecraftBridge.invokeWithInterfaceParam(box, "setHint", hintComponent);

            irisSearch$positionSearchBox(box);

            ISearchableOptionList searchable = (ISearchableOptionList) this.shaderOptionList;
            String savedQuery = searchable.irisSearch$getTypedSearchQuery();
            ReflectionUtils.invokeMethod(box, "setValue", new Class<?>[]{String.class}, savedQuery);

            int savedCursor = Math.min(searchable.irisSearch$getSavedCursorPosition(), savedQuery.length());
            ReflectionUtils.invokeMethod(box, "setCursorPosition", new Class<?>[]{int.class}, savedCursor);

            Consumer<String> responder = text -> {
                try {
                    if (this.shaderOptionList == null) {
                        return;
                    }
                    ISearchableOptionList s = (ISearchableOptionList) this.shaderOptionList;
                    s.irisSearch$setTypedSearchQuery(text);

                    Object cursorObj = ReflectionUtils.invokeMethod(box, "getCursorPosition", new Class<?>[]{});
                    s.irisSearch$setSavedCursorPosition(cursorObj instanceof Integer ? (Integer) cursorObj : 0);

                    s.irisSearch$updateSearchQuery(text);
                } catch (Throwable t) {
                    IrisSearch.log(3, "Search box responder failed." + t);
                    debugLog("Search box responder failed: " + t);
                }
            };
            ReflectionUtils.invokeMethod(box, "setResponder", new Class<?>[]{Consumer.class}, responder);

            boolean active = searchable.irisSearch$isSearchModeActive();
            ReflectionUtils.invokeMethod(box, "setVisible", new Class<?>[]{boolean.class}, active);
            if (active) {
                irisSearch$focusSearchBox(box);
            }

            debugLog("Search box created (active=" + active + ")");
            return box;
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to create search box." + t);
            debugLog("Failed to create search box: " + t);
            return null;
        }
    }

    @Unique
    private Object irisSearch$createPackSearchBox() {
        if (this.shaderPackList == null) {
            return null;
        }

        try {
            Object font = ReflectionUtils.getFieldValue(this, "font");
            if (font == null) {
                debugLog("Could not locate font field on screen");
                return null;
            }

            Class<?> editBoxClass = MinecraftBridge.resolveClass("net.minecraft.client.gui.components.EditBox");
            if (editBoxClass == null) {
                debugLog("Could not resolve EditBox class");
                return null;
            }

            Object hintComponent = MinecraftBridge.createTranslatableComponent("iris_search.pack_search.hint");
            if (hintComponent == null) {
                debugLog("Could not create pack search hint text component");
                return null;
            }

            Object box = MinecraftBridge.instantiate(editBoxClass, font, 0, 0, 10, 14, hintComponent);
            if (box == null) {
                debugLog("Could not find a matching EditBox constructor");
                return null;
            }

            ReflectionUtils.invokeMethod(box, "setMaxLength", new Class<?>[]{int.class}, 64);
            ReflectionUtils.invokeMethod(box, "setBordered", new Class<?>[]{boolean.class}, true);
            MinecraftBridge.invokeWithInterfaceParam(box, "setHint", hintComponent);

            irisSearch$positionPackSearchBox(box);

            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            String savedQuery = searchable.irisSearch$getTypedSearchQuery();
            ReflectionUtils.invokeMethod(box, "setValue", new Class<?>[]{String.class}, savedQuery);

            int savedCursor = Math.min(searchable.irisSearch$getSavedCursorPosition(), savedQuery.length());
            ReflectionUtils.invokeMethod(box, "setCursorPosition", new Class<?>[]{int.class}, savedCursor);

            Consumer<String> responder = text -> {
                try {
                    if (this.shaderPackList == null) {
                        return;
                    }
                    ISearchablePackList s = (ISearchablePackList) this.shaderPackList;
                    s.irisSearch$setTypedSearchQuery(text);

                    Object cursorObj = ReflectionUtils.invokeMethod(box, "getCursorPosition", new Class<?>[]{});
                    s.irisSearch$setSavedCursorPosition(cursorObj instanceof Integer ? (Integer) cursorObj : 0);

                    s.irisSearch$updateSearchQuery(text);
                    irisSearch$resetPackListScroll();
                } catch (Throwable t) {
                    IrisSearch.log(3, "Pack search box responder failed." + t);
                    debugLog("Pack search box responder failed: " + t);
                }
            };
            ReflectionUtils.invokeMethod(box, "setResponder", new Class<?>[]{Consumer.class}, responder);

            debugLog("Pack search box created");
            return box;
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to create pack search box." + t);
            debugLog("Failed to create pack search box: " + t);
            return null;
        }
    }

    // centerScrollOn (called by refresh()) no-ops if the previously selected pack isn't in the filtered
    // results, leaving a stale scroll offset that can scroll new results out of view. Force it back to top.
    @Unique
    private void irisSearch$resetPackListScroll() {
        try {
            ReflectionUtils.invokeMethod(this.shaderPackList, "setScrollAmount", new Class<?>[]{double.class}, 0.0);
        } catch (Throwable t) {
            debugLog("Failed to reset pack list scroll: " + t);
        }
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

            ReflectionUtils.invokeMethod(this.shaderPackList, "setY", new Class<?>[]{int.class}, targetY);
            ReflectionUtils.invokeMethod(this.shaderPackList, "setHeight", new Class<?>[]{int.class}, targetHeight);

            // Refresh so centerScrollOn recomputes against shrunk dimensions.
            ReflectionUtils.invokeMethod(this.shaderPackList, "refresh", new Class<?>[]{});

            debugLog("Reserved header space above pack list: y=" + targetY + " height=" + targetHeight);
        } catch (Throwable t) {
            debugLog("Could not reserve header space for pack list (setY/setHeight unavailable?): " + t);
        }
    }

    // Position box in reserved header strip, centered horizontally with list rows.
    @Unique
    private void irisSearch$positionPackSearchBox(Object box) {
        if (this.shaderPackList == null || box == null) {
            return;
        }

        try {
            ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
            int reservedHeight = searchable.irisSearch$getReservedHeaderHeight();
            int rowWidth = irisSearch$liveOrCachedPackRowWidth(searchable);
            int listX = irisSearch$liveOrCachedPackX(searchable);
            int listWidth = irisSearch$liveOrCachedPackWidth(searchable);

            final int padding = 3;
            int boxHeight = Math.max(10, reservedHeight - padding * 2);
            int rowX = listX + (listWidth - rowWidth) / 2;
            int boxY = searchable.irisSearch$getListTop() + padding;

            ReflectionUtils.invokeMethod(box, "setX", new Class<?>[]{int.class}, rowX);
            ReflectionUtils.invokeMethod(box, "setY", new Class<?>[]{int.class}, boxY);
            ReflectionUtils.invokeMethod(box, "setWidth", new Class<?>[]{int.class}, rowWidth);
            ReflectionUtils.invokeMethod(box, "setHeight", new Class<?>[]{int.class}, boxHeight);
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't position the pack search box correctly." + t);
            debugLog("Failed to position pack search box: " + t);
        }
    }

    @Unique
    private int irisSearch$liveOrCachedPackRowWidth(ISearchablePackList searchable) {
        try {
            return this.shaderPackList.getRowWidth();
        } catch (Throwable t) {
            return searchable.irisSearch$getRowWidth();
        }
    }

    @Unique
    private int irisSearch$liveOrCachedPackX(ISearchablePackList searchable) {
        try {
            return this.shaderPackList.getX();
        } catch (Throwable t) {
            return searchable.irisSearch$getListLeft();
        }
    }

    @Unique
    private int irisSearch$liveOrCachedPackWidth(ISearchablePackList searchable) {
        try {
            return this.shaderPackList.getWidth();
        } catch (Throwable t) {
            return searchable.irisSearch$getListWidth();
        }
    }

    @Unique
    private void irisSearch$syncPackSearchBox() {
        if (this.irisSearch$packSearchBox == null || this.shaderPackList == null) {
            return;
        }

        try {
            boolean shouldShow = !this.optionMenuOpen && !this.guiHidden;

            Object visibleObj = ReflectionUtils.invokeMethod(this.irisSearch$packSearchBox, "isVisible", new Class<?>[]{});
            boolean currentlyVisible = Boolean.TRUE.equals(visibleObj);

            if (shouldShow != currentlyVisible) {
                ReflectionUtils.invokeMethod(this.irisSearch$packSearchBox, "setVisible", new Class<?>[]{boolean.class}, shouldShow);
                if (!shouldShow) {
                    irisSearch$unfocusSearchBox(this.irisSearch$packSearchBox);
                }
            }

            if (shouldShow) {
                irisSearch$positionPackSearchBox(this.irisSearch$packSearchBox);
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to sync pack search box visibility." + t);
            debugLog("Failed to sync pack search box: " + t);
        }
    }

    @Unique
    private void irisSearch$positionSearchBox(Object box) {
        if (this.shaderOptionList == null || box == null) {
            return;
        }

        try {
            final int boxHeight = 16;
            ISearchableOptionList bounds = (ISearchableOptionList) this.shaderOptionList;

            int rowX, rowY, rowWidth, rowHeight;
            if (bounds.irisSearch$hasHeaderRowBounds()) {
                rowX = bounds.irisSearch$getHeaderRowX();
                rowY = bounds.irisSearch$getHeaderRowY();
                rowWidth = bounds.irisSearch$getHeaderRowWidth();
                rowHeight = bounds.irisSearch$getHeaderRowHeight();
            } else {
                rowWidth = MinecraftBridge.invokeIntGetter(this.shaderOptionList, "getRowWidth", null, 220);
                int listX = MinecraftBridge.invokeIntGetter(this.shaderOptionList, "getX", null, 0);
                int listY = MinecraftBridge.invokeIntGetter(this.shaderOptionList, "getY", null, 0);
                int listWidth = MinecraftBridge.invokeIntGetter(this.shaderOptionList, "getWidth", null, 300);
                rowX = listX + (listWidth - rowWidth) / 2;
                rowY = listY;
                rowHeight = 24;
            }

            int leftMargin;
            try {
                leftMargin = ((ISearchableOptionList) this.shaderOptionList).irisSearch$getReservedLeftWidth();
            } catch (Throwable t) {
                leftMargin = 48;
            }
            final int rightMargin = 4;
            int verticalOffset = bounds.irisSearch$hasHeaderRowBounds() && bounds.irisSearch$headerRowUsesGetterShape() ? 4 : 2;

            int boxX = rowX + leftMargin;
            int boxY = rowY + ((rowHeight - boxHeight) / 2) - verticalOffset;
            int boxWidth = Math.max(40, rowWidth - leftMargin - rightMargin);

            ReflectionUtils.invokeMethod(box, "setX", new Class<?>[]{int.class}, boxX);
            ReflectionUtils.invokeMethod(box, "setY", new Class<?>[]{int.class}, boxY);
            ReflectionUtils.invokeMethod(box, "setWidth", new Class<?>[]{int.class}, boxWidth);
            ReflectionUtils.invokeMethod(box, "setHeight", new Class<?>[]{int.class}, boxHeight);
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't position the search box correctly." + t);
            debugLog("Failed to position search box: " + t);
        }
    }

    @Unique
    private void irisSearch$focusSearchBox(Object box) {
        try {
            ReflectionUtils.invokeMethod(box, "setFocused", new Class<?>[]{boolean.class}, true);

            if (this.irisSearch$screenSetFocusedMethod == null) {
                this.irisSearch$screenSetFocusedMethod =
                        MinecraftBridge.findMethodByInterfaceParam(this.getClass(), "setFocused", box.getClass());
            }
            if (this.irisSearch$screenSetFocusedMethod != null) {
                this.irisSearch$screenSetFocusedMethod.invoke(this, box);
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to focus search box." + t);
            debugLog("Failed to focus search box: " + t);
        }
    }

    @Unique
    private void irisSearch$unfocusSearchBox(Object box) {
        try {
            ReflectionUtils.invokeMethod(box, "setFocused", new Class<?>[]{boolean.class}, false);

            Object currentFocused = ReflectionUtils.invokeMethod(this, "getFocused", new Class<?>[]{});
            if (currentFocused == box && this.irisSearch$screenSetFocusedMethod != null) {
                this.irisSearch$screenSetFocusedMethod.invoke(this, (Object) null);
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to unfocus search box." + t);
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

            Object visibleObj = ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "isVisible", new Class<?>[]{});
            boolean currentlyVisible = Boolean.TRUE.equals(visibleObj);

            if (shouldBeActive != currentlyVisible) {
                if (shouldBeActive) {
                    String query = searchable.irisSearch$getTypedSearchQuery();
                    ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "setValue", new Class<?>[]{String.class}, query);

                    int cursor = Math.min(searchable.irisSearch$getSavedCursorPosition(), query.length());
                    ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "setCursorPosition", new Class<?>[]{int.class}, cursor);

                    ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "setVisible", new Class<?>[]{boolean.class}, true);
                    irisSearch$focusSearchBox(this.irisSearch$searchBox);
                    debugLog("Search box synced to visible/focused");
                } else {
                    ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "setVisible", new Class<?>[]{boolean.class}, false);
                    irisSearch$unfocusSearchBox(this.irisSearch$searchBox);
                    debugLog("Search box synced to hidden/unfocused");
                }
            }

            if (shouldBeActive) {
                irisSearch$updateScrollClipping(this.irisSearch$searchBox);
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to sync search box visibility." + t);
            debugLog("Failed to sync search box visibility: " + t);
        }
    }

    @Unique
    private void irisSearch$updateScrollClipping(Object box) {
        try {
            double scroll = MinecraftBridge.invokeDoubleGetter(this.shaderOptionList, "scrollAmount", null, 0.0);

            if (scroll > 0.5) {
                ReflectionUtils.invokeMethod(box, "setY", new Class<?>[]{int.class}, OFFSCREEN_Y);
            } else {
                irisSearch$positionSearchBox(box);
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to update search box scroll clipping." + t);
            debugLog("Failed to update search box scroll clipping: " + t);
        }
    }

    @Dynamic
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void irisSearch$onRender(@Coerce Object guiGraphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            irisSearch$syncSearchBoxVisibility();
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to sync search box during render." + t);
            debugLog("Failed to sync search box during extractRenderState: " + t);
        }
        try {
            irisSearch$syncPackSearchBox();
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to sync pack search box during render." + t);
            debugLog("Failed to sync pack search box during extractRenderState: " + t);
        }
    }

    @Dynamic
    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void irisSearch$onKeyPressed(@Coerce Object event, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (event == null) {
                return;
            }

            Object keyObj = ReflectionUtils.invokeMethod(event, "key", new Class<?>[]{});
            int key = keyObj instanceof Integer ? (Integer) keyObj : -1;
            boolean isEscape = key == GLFW.GLFW_KEY_ESCAPE;
            boolean ctrlDown = MinecraftBridge.isControlDown();
            debugLog("keyPressed: key=" + key + " isEscape=" + isEscape + " ctrlDown=" + ctrlDown);

            if (irisSearch$handleSearchKeyPress(key, ctrlDown, isEscape)) {
                cir.setReturnValue(true);
                return;
            }
            if (irisSearch$handlePackSearchKeyPress(key, ctrlDown, isEscape)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to handle key press." + t);
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
            Object focusedObj = ReflectionUtils.invokeMethod(this.irisSearch$packSearchBox, "isFocused", new Class<?>[]{});
            boolean isFocused = Boolean.TRUE.equals(focusedObj);

            if (isEscapeKey && isFocused) {
                ISearchablePackList searchable = (ISearchablePackList) this.shaderPackList;
                String query = searchable.irisSearch$getTypedSearchQuery();
                if (!query.isEmpty()) {
                    ReflectionUtils.invokeMethod(this.irisSearch$packSearchBox, "setValue", new Class<?>[]{String.class}, "");
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
            IrisSearch.log(3, "Failed to handle pack search key press." + t);
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
            IrisSearch.log(3, "Failed to handle search key press." + t);
            debugLog("Failed to handle search key press: " + t);
        }

        return false;
    }

    @Inject(method = "refreshForChangedPack", at = @At("TAIL"), require = 0)
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

    @Dynamic
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void irisSearch$onMouseClicked(@Coerce Object event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (event == null) {
                return;
            }

            if (this.optionMenuOpen && this.irisSearch$searchBox != null) {
                Object visibleObj = ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "isVisible", new Class<?>[]{});
                if (Boolean.TRUE.equals(visibleObj)) {
                    Object result = ReflectionUtils.invokeMethod(this.irisSearch$searchBox, "mouseClicked",
                            new Class<?>[]{event.getClass(), boolean.class}, event, doubleClick);
                    if (Boolean.TRUE.equals(result)) {
                        irisSearch$focusSearchBox(this.irisSearch$searchBox);
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }

            if (!this.optionMenuOpen && this.irisSearch$packSearchBox != null) {
                Object visibleObj = ReflectionUtils.invokeMethod(this.irisSearch$packSearchBox, "isVisible", new Class<?>[]{});
                if (Boolean.TRUE.equals(visibleObj)) {
                    Object result = ReflectionUtils.invokeMethod(this.irisSearch$packSearchBox, "mouseClicked",
                            new Class<?>[]{event.getClass(), boolean.class}, event, doubleClick);
                    if (Boolean.TRUE.equals(result)) {
                        irisSearch$focusSearchBox(this.irisSearch$packSearchBox);
                        cir.setReturnValue(true);
                    }
                }
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Failed to handle mouse click." + t);
            debugLog("Failed mouseClicked handling: " + t);
        }
    }
}
