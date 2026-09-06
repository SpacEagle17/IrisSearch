package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.forge.IJumpHintWidget;
import net.irisshaders.iris.gui.element.widget.SliderElementWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code SliderElementWidget.render} has its own tooltip logic instead of calling {@code tryRenderTooltip},
 * so the base widget's Ctrl+hover tooltip never shows for sliders. Re-trigger it here from the shared {@link IJumpHintWidget}.
 */
@Mixin(value = SliderElementWidget.class, remap = false)
public abstract class SliderElementWidgetMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false, require = 0)
    private void irisSearch$sliderJumpHint(@Coerce Object guiGraphics, int mouseX, int mouseY, float tickDelta,
                                           boolean hovered, CallbackInfo ci) {
        if (this instanceof IJumpHintWidget hint) {
            hint.irisSearch$tryQueueJumpHint(guiGraphics, mouseX, mouseY, hovered);
        }
    }
}
