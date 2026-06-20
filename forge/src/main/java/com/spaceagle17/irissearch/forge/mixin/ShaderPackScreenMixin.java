package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.forge.ISearchableOptionContainer;
import com.spaceagle17.irissearch.forge.ISearchableOptionList;
import com.spaceagle17.irissearch.forge.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.minecraft.client.gui.Font;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.ShaderPack;
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
    private boolean optionMenuOpen;

    @Shadow
    private boolean guiHidden;

    @Unique
    private EditBox irisSearch$searchBox;

    @Unique
    private ScreenAccessor irisSearch$accessor() {
        return (ScreenAccessor) this;
    }



    // Moves the search box off-screen when the list is scrolled so it doesn't float above content.
    @Unique
    private static final int OFFSCREEN_Y = -10000;

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackScreenMixin] " + message);
    }

    @Unique
    private ContainerEventHandler irisSearch$focusHandler() {
        return (ContainerEventHandler) this;
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), require = 0, remap = false)
    private void irisSearch$onInit(CallbackInfo ci) {
        try {
            this.irisSearch$searchBox = null;

            Iris.getCurrentPack().ifPresent((ShaderPack pack) -> {
                Object container = pack.getMenuContainer();
                if (container instanceof ISearchableOptionContainer) {
                    ((ISearchableOptionContainer) container).irisSearch$setSearchQuery(null);
                    irisSearch$debugLog("Reset search container query during init()");
                }
            });

            if (!this.guiHidden && this.optionMenuOpen && this.shaderOptionList != null) {
                this.irisSearch$searchBox = irisSearch$createSearchBox();

                if (this.irisSearch$searchBox != null) {
//                    irisSearch$accessor().irisSearch$invokeAddRenderableWidget(this.irisSearch$searchBox);
                    ScreenAccessor sa = irisSearch$accessor();
                    sa.irisSearch$getRenderables().add(this.irisSearch$searchBox);
                    sa.irisSearch$getChildren().add(this.irisSearch$searchBox);
                    sa.irisSearch$getNarratables().add(this.irisSearch$searchBox);
//                    MinecraftBridge.invokeAddRenderableWidget(this, this.irisSearch$searchBox);
                    irisSearch$debugLog("Search box created during init() and added to renderables");
                } else {
                    irisSearch$debugLog("Search box creation failed during init()");
                }
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to add search box during init: " + t);
        }
    }

    @Inject(method = "m_7379_", at = @At("HEAD"), require = 0, remap = false)
    private void irisSearch$onCloseDisableSearch(CallbackInfo ci) {
        try {
            if (this.shaderOptionList != null) {
                ((ISearchableOptionList) this.shaderOptionList).irisSearch$disableSearchModeAndRebuild();
                irisSearch$debugLog("Disabled search mode on screen close");
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed to disable search mode on close: " + t);
        }
    }

    @Unique
    private EditBox irisSearch$createSearchBox() {
        if (this.shaderOptionList == null) {
            return null;
        }

        try {
            irisSearch$debugLog("before search box creation yaaaay");
            EditBox box = new EditBox(irisSearch$accessor().irisSearch$getFont(), 0, 0, 10, 16, Component.literal("Search shader options"));
            irisSearch$debugLog("Creating search box with initial bounds");
            box.setMaxLength(64);
            box.setBordered(true);
            box.setHint(Component.literal("Search options...")
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
    private int irisSearch$liveOrCachedX() {
//        try {
//            Object getY = ReflectionUtils.invokeMethod(this.shaderOptionList, "m_252754_", new Class<?>[]{});
//            int getYInt = Integer.parseInt(String.valueOf(getY));
//            irisSearch$debugLog("Successfully invoked getY() on shaderOptionList, Y=" + getYInt);
//            return getYInt;
//        } catch (Throwable t) {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListLeft();
//        }
    }

    @Unique
    private int irisSearch$liveOrCachedY() {
//        try {
//            Object getY = ReflectionUtils.invokeMethod(this.shaderOptionList, "m_252907_", new Class<?>[]{});
//            int getYInt = Integer.parseInt(String.valueOf(getY));
//            irisSearch$debugLog("Successfully invoked getY() on shaderOptionList, Y=" + getYInt);
//            return getYInt;
//        } catch (Throwable t) {
            return ((ISearchableOptionList) this.shaderOptionList).irisSearch$getListTop();
//        }
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
    }

    // -- Old key API (keyPressed with int keyCode, int scanCode, int modifiers) --

    @Inject(method = "m_7933_(III)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void irisSearch$onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        try {
            boolean isEscape = keyCode == GLFW.GLFW_KEY_ESCAPE;
            boolean ctrlDown = Screen.hasControlDown();

            if (irisSearch$handleSearchKeyPress(keyCode, ctrlDown, isEscape)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed keyPressed handling: " + t);
        }
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
            if (!this.optionMenuOpen || this.irisSearch$searchBox == null || !this.irisSearch$searchBox.isVisible()) {
                return;
            }

            if (this.irisSearch$searchBox.mouseClicked(mouseX, mouseY, button)) {
                irisSearch$focusSearchBox(this.irisSearch$searchBox);
                cir.setReturnValue(true);
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed mouseClicked handling: " + t);
        }
    }
}
