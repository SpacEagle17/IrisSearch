package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.engine.MinecraftLanguageAccess;
import com.spaceagle17.irissearch.engine.ShaderOptionsSearchEngine;
import com.spaceagle17.irissearch.forge.IJumpHintWidget;
import com.spaceagle17.irissearch.forge.ISearchableOptionContainer;
import com.spaceagle17.irissearch.forge.ISearchableOptionList;
import com.spaceagle17.irissearch.forge.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.element.widget.BaseOptionElementWidget;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Mixin(value = BaseOptionElementWidget.class, remap = false)
public class BaseOptionElementWidgetMixin implements IJumpHintWidget {

    @Shadow protected ShaderPackScreen screen;

    private static final String JUMP_HINT_KEY = "iris_search.option_search.jump_hint";

    // Forge production uses SRG method names for vanilla MC classes
    private static final String COMPONENT_CLASS = "net.minecraft.network.chat.Component";
    private static final String METHOD_LITERAL      = "m_237113_"; // Component.literal(String)
    private static final String METHOD_TRANSLATABLE = "m_237115_"; // Component.translatable(String)
    private static final String METHOD_EMPTY        = "m_237119_"; // Component.empty()
    private static final String METHOD_SIBLINGS     = "m_7360_";   // MutableComponent.getSiblings()

    @Unique
    private boolean irisSearch$isInSearchMode() {
        try {
            if (this.screen == null) return false;
            Object optionList = ReflectionUtils.getFieldValue(this.screen, "shaderOptionList");
            if (optionList instanceof ISearchableOptionList searchable) {
                return searchable.irisSearch$isSearchModeActive();
            }
        } catch (Throwable t) {
            irisSearch$debugLog("isInSearchMode check failed: " + t);
        }
        return false;
    }

    @Unique
    private String irisSearch$buildTranslatedPath() {
        try {
            Object elementObj = ReflectionUtils.getFieldValue(this, "element");
            if (!(elementObj instanceof OptionMenuOptionElement optEl)) return null;
            if (!(optEl.container instanceof ISearchableOptionContainer searchable)) return null;

            String rawPath = searchable.irisSearch$getOptionPath(optEl.optionId);

            String[] segments = rawPath.split("/");
            StringBuilder display = new StringBuilder();
            for (String segment : segments) {
                if ("root".equals(segment)) continue;
                String translated = ShaderOptionsSearchEngine.getDisplaySettingsName(segment).replaceAll("\\s+>", "");
                String label = translated.isEmpty() ? segment : translated;
                if (!display.isEmpty()) display.append(" > ");
                display.append(label);
            }

            irisSearch$debugLog("Final translated path" + (display.isEmpty() ? " (empty)" : ": " + display.toString()));

            return !display.isEmpty() ? display.toString() : null;
        } catch (Throwable t) {
            irisSearch$debugLog("buildTranslatedPath failed: " + t);
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getCommentBody", at = @At("RETURN"), cancellable = true)
    private void modifyCommentBody(CallbackInfoReturnable<Optional<?>> cir) {
        if (!irisSearch$isInSearchMode()) return;

        String pathLabel = irisSearch$buildTranslatedPath();
        if (pathLabel == null) return;

        Optional<?> originalBody = cir.getReturnValue();

        Object bodyRoot = ReflectionUtils.invokeMethod(COMPONENT_CLASS, METHOD_EMPTY, new Class<?>[]{});
        if (bodyRoot == null) {
            bodyRoot = ReflectionUtils.invokeMethod(COMPONENT_CLASS, METHOD_LITERAL, new Class<?>[]{String.class}, "");
        }
        if (bodyRoot == null) return;

        // §l§o = bold + italic; §r resets before the newline so original body starts clean
        Object pathComponent = ReflectionUtils.invokeMethod(COMPONENT_CLASS, METHOD_LITERAL,
                new Class<?>[]{String.class}, "§l§o" + pathLabel + "§r §7§o(");
        Object hintComponent = ReflectionUtils.invokeMethod(COMPONENT_CLASS, METHOD_TRANSLATABLE,
                new Class<?>[]{String.class}, JUMP_HINT_KEY);
        Object newlineComponent = ReflectionUtils.invokeMethod(COMPONENT_CLASS, METHOD_LITERAL,
                new Class<?>[]{String.class}, ")§r\n");

        try {
            List siblings = (List) ReflectionUtils.invokeMethod(bodyRoot, METHOD_SIBLINGS, new Class<?>[]{});
            if (siblings != null) {
                if (pathComponent != null) siblings.add(pathComponent);
                if (hintComponent != null) siblings.add(hintComponent);
                if (newlineComponent != null) siblings.add(newlineComponent);
                if (originalBody != null && originalBody.isPresent()) siblings.add(originalBody.get());
                cir.setReturnValue(Optional.of(bodyRoot));
            }
        } catch (Exception e) {
            irisSearch$debugLog("Failed to build comment body: " + e);
        }
    }

    /** Renders a Ctrl+Click hint tooltip. */
    @Inject(method = "tryRenderTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void irisSearch$onTryRenderTooltip(@Coerce Object guiGraphics, int mouseX, int mouseY, boolean hovered,
                                               CallbackInfo ci) {
        if (irisSearch$tryQueueJumpHint(guiGraphics, mouseX, mouseY, hovered)) ci.cancel();
    }

    /**
     * Sliders render their tooltip by calling {@code renderTooltip} directly.
     * Suppress that for Ctrl+click one is rendering to prevent z-fighting
     */
    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$suppressTooltipUnderCtrlHint(@Coerce Object guiGraphics, @Coerce Object text,
                                                        int mouseX, int mouseY, boolean hovered, CallbackInfo ci) {
        if (irisSearch$jumpHintApplies(hovered)) ci.cancel();
    }

    @Unique
    private boolean irisSearch$jumpHintApplies(boolean hovered) {
        try {
            return hovered && MinecraftBridge.isControlDown() && !MinecraftBridge.isShiftDown()
                    && irisSearch$isInSearchMode()
                    && ReflectionUtils.getFieldValue(this, "element") instanceof OptionMenuOptionElement;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Shared with {@code SliderElementWidgetMixin}: sliders render their own inline tooltip instead of
     * calling {@code tryRenderTooltip}, so they reach this through {@link IJumpHintWidget}.
     */
    @Override
    public boolean irisSearch$tryQueueJumpHint(Object guiGraphics, int mouseX, int mouseY, boolean hovered) {
        try {
            if (!irisSearch$jumpHintApplies(hovered)) return false;

            Object font = MinecraftBridge.getMinecraftFont();
            String hint = MinecraftLanguageAccess.getColorStrippedString(JUMP_HINT_KEY);
            Object text = !hint.isEmpty()
                    ? MinecraftBridge.createLiteralComponent("§a" + hint)
                    : MinecraftBridge.createTranslatableComponent(JUMP_HINT_KEY);
            Object queue = ReflectionUtils.getFieldValue(
                    "net.irisshaders.iris.gui.screen.ShaderPackScreen", "TOP_LAYER_RENDER_QUEUE");
            if (font == null || text == null || !(queue instanceof Collection)) return false;

            // What Iris does lol
            int px = mouseX + 2;
            int py = mouseY - 16;
            @SuppressWarnings("unchecked")
            Collection<Runnable> renderQueue = (Collection<Runnable>) queue;
            renderQueue.add(() -> ReflectionUtils.invokeMethod(
                    "net.irisshaders.iris.gui.GuiUtil", "drawTextPanel", null, font, guiGraphics, text, px, py));
            return true;
        } catch (Throwable t) {
            irisSearch$debugLog("Ctrl-hover tooltip failed: " + t);
            return false;
        }
    }

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[BaseOptionElementWidgetMixin] " + message);
    }
}
