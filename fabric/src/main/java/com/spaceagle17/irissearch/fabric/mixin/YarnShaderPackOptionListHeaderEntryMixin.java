package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.fabric.ISearchableHeaderEntry;
import com.spaceagle17.irissearch.fabric.ISearchableOptionList;
import com.spaceagle17.irissearch.fabric.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.element.IrisElementRow;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShaderPackOptionList.HeaderEntry.class)
public abstract class YarnShaderPackOptionListHeaderEntryMixin {

    @Shadow @Final private @Nullable IrisElementRow backButton;
    @Unique private static final int TITLE_GAP_FROM_BUTTON = 6;

    @Unique
    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[YarnShaderPackOptionListHeaderEntryMixin] " + message);
    }

    @Unique
    private Object irisSearch$resolveOuterList() {
        Object listObj = ReflectionUtils.getFieldValue(this, "this$0");
        if (listObj == null) {
            Object screenObj = ReflectionUtils.getFieldValue(this, "screen");
            listObj = ReflectionUtils.getFieldByType(screenObj, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
        }
        return listObj;
    }

    @Unique
    private boolean irisSearch$suppressIfSearching(GuiGraphics guiGraphics, int x, int y, int entryWidth, int entryHeight,
                                                   int mouseX, int mouseY, boolean hovered, float tickDelta, boolean usesGetterShape) {
        Object outerList = irisSearch$resolveOuterList();
        if (outerList == null) {
            debugLog("Could not resolve outer ShaderPackOptionList, falling back to normal rendering");
            return false;
        }

        try {
            ((ISearchableOptionList) outerList).irisSearch$publishHeaderRowBounds(x, y, entryWidth, entryHeight, usesGetterShape);
        } catch (Throwable t) {
            debugLog("Could not publish header row bounds to outer list, skipping bounds update: " + t);
        }

        try {
            boolean searchActive;
            try {
                searchActive = ((ISearchableOptionList) outerList).irisSearch$isSearchModeActive();
            } catch (Throwable t) {
                return false;
            }

            if (!searchActive) {
                return false;
            }

            guiGraphics.fill(x - 3, (y + entryHeight) - 2, x + entryWidth, (y + entryHeight) - 1, 0x66BEBEBE);

            if (this.backButton != null) {
                this.backButton.render(guiGraphics, x, y, 16, mouseX, mouseY, tickDelta, hovered);
            }

            try {
                ISearchableHeaderEntry entry = (ISearchableHeaderEntry) this;
                MinecraftBridge.queueHeaderTooltip(guiGraphics, entry.irisSearch$getSearchToggleButton(),
                        entry.irisSearch$getSearchToggleTooltipText(), x, y - 16);
            } catch (Throwable t) {
                IrisSearch.log(3, "Couldn't show the search button tooltip while searching.");
                debugLog("Failed queueing tooltip from suppressed render path: " + t);
            }

            return true;
        } catch (Throwable t) {
            IrisSearch.log(3, "Header row couldn't render correctly while searching.");
            debugLog("Failed to suppress header row rendering during search: " + t);
            return false;
        }
    }

    @Dynamic
    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$suppressWhenSearching10Params(GuiGraphics guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                                                          int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        if (irisSearch$suppressIfSearching(guiGraphics, x, y, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta, false)) {
            ci.cancel();
        }
    }

    @Dynamic
    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIZF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$suppressWhenSearching5Params(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                         boolean hovered, float tickDelta, CallbackInfo ci) {
        int x = MinecraftBridge.invokeIntGetter(this, "getX", "method_46426", 0);
        int y = MinecraftBridge.invokeIntGetter(this, "getY", "method_46427", 0);
        int entryWidth = MinecraftBridge.invokeIntGetter(this, "getWidth", "method_25368", 0);
        int entryHeight = MinecraftBridge.invokeIntGetter(this, "getHeight", "method_25364", 0);

        if (irisSearch$suppressIfSearching(guiGraphics, x, y, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta, true)) {
            ci.cancel();
        }
    }

    /**
     * Tooltip for the Search button (non-searching state). Added here because the reflective
     * equivalent in ShaderPackOptionListHeaderEntryMixin was not woven in on 1.21.11.
     */
    @Dynamic
    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIZF)V", at = @At("TAIL"), remap = false, require = 0)
    private void irisSearch$onRenderTooltip5Params(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        try {
            ISearchableHeaderEntry entry = (ISearchableHeaderEntry) this;
            Object btn = entry.irisSearch$getSearchToggleButton();
            if (btn == null) {
                return;
            }
            int x = MinecraftBridge.invokeIntGetter(this, "getX", "method_46426", 0);
            int y = MinecraftBridge.invokeIntGetter(this, "getY", "method_46427", 0);
            MinecraftBridge.queueHeaderTooltip(guiGraphics, btn, entry.irisSearch$getSearchToggleTooltipText(), x, y - 16);
        } catch (Throwable t) {
            debugLog("Failed queueing search button tooltip (typed 5-param TAIL): " + t);
        }
    }

    @Dynamic
    @ModifyArg(
            method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gui/element/ShaderPackOptionList;access$000(Lnet/minecraft/class_332;Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIIII)V",
                    remap = false),
            index = 4, remap = false, require = 0
    )
    private int irisSearch$widenTitleLeftBound10Params(int minX) {
        try {
            if (this.backButton != null) {
                return minX + this.backButton.getWidth() + TITLE_GAP_FROM_BUTTON;
            }
        } catch (Throwable t) {
            debugLog("Failed widening title left bound (10-param path): " + t);
        }
        return minX;
    }

    @Dynamic
    @ModifyArg(
            method = "method_25343(Lnet/minecraft/class_332;IIZF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/class_12225;method_75771(Lnet/minecraft/class_2561;IIIII)V",
                    remap = false
            ),
            index = 2,
            remap = false,
            require = 0
    )
    private int irisSearch$widenTitleLeftBound5Params(int left) {
        try {
            if (this.backButton != null) {
                return left + this.backButton.getWidth();
            }
        } catch (Throwable t) {
            debugLog("Failed widening title left bound (5-param path): " + t);
        }
        return left;
    }
}