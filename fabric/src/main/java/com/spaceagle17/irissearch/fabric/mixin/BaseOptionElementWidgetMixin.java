package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.ShaderSearchEngine;
import com.spaceagle17.irissearch.fabric.ISearchableOptionContainer;
import com.spaceagle17.irissearch.fabric.ISearchableOptionList;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.element.widget.BaseOptionElementWidget;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(value = BaseOptionElementWidget.class, remap = false)
public class BaseOptionElementWidgetMixin {

    @Shadow protected ShaderPackScreen screen;

    @Unique private static boolean irisSearch$isMappingsInitialized = false;
    @Unique private static String irisSearch$componentClassName;
    @Unique private static String irisSearch$literalMethodName;
    @Unique private static String irisSearch$emptyMethodName;
    @Unique private static String irisSearch$getSiblingsMethodName;

    @Unique
    private static void irisSearch$initializeMappings() {
        if (irisSearch$isMappingsInitialized) return;

        boolean isMojang = ReflectionUtils.checkClassExists("net.minecraft.network.chat.Component");
        irisSearch$componentClassName = isMojang ? "net.minecraft.network.chat.Component" : "net.minecraft.class_2561";
        irisSearch$literalMethodName = isMojang ? "literal" : "method_43470";
        irisSearch$emptyMethodName = isMojang ? "empty" : "method_43473";
        irisSearch$getSiblingsMethodName = isMojang ? "getSiblings" : "method_10855";

        irisSearch$isMappingsInitialized = true;
    }

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

    /**
     * Returns a breadcrumb like "Lighting > Shadows" using properly-cased translations,
     * or null for root-level options (no breadcrumb needed there).
     */
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
                String translated = ShaderSearchEngine.getDisplaySettingsName(segment).replaceAll("\\s+>", "");
                irisSearch$debugLog("Translating segment '" + segment + "' to '" + translated + "'");
                String label = translated.isEmpty() ? segment : translated;
                if (!display.isEmpty()) display.append(" > ");
                display.append(label);
            }

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

        irisSearch$initializeMappings();

        Optional<?> originalBody = cir.getReturnValue();

        Object bodyRoot = ReflectionUtils.invokeMethod(
                irisSearch$componentClassName, irisSearch$emptyMethodName,
                new Class<?>[]{});

        if (bodyRoot == null) {
            bodyRoot = ReflectionUtils.invokeMethod(
                    irisSearch$componentClassName, irisSearch$literalMethodName,
                    new Class<?>[]{String.class}, "");
        }
        if (bodyRoot == null) return;

        // §l§o = bold + italic; §r resets before the newline so original body starts clean
        Object pathComponent = ReflectionUtils.invokeMethod(
                irisSearch$componentClassName, irisSearch$literalMethodName,
                new Class<?>[]{String.class}, "§l§o" + pathLabel + "§r");
        Object newlineComponent = ReflectionUtils.invokeMethod(
                irisSearch$componentClassName, irisSearch$literalMethodName,
                new Class<?>[]{String.class}, "\n");

        try {
            List siblings = (List) ReflectionUtils.invokeMethod(
                    bodyRoot, irisSearch$getSiblingsMethodName, new Class<?>[]{});

            if (siblings != null) {
                if (pathComponent != null) siblings.add(pathComponent);
                if (newlineComponent != null) siblings.add(newlineComponent);
                if (originalBody != null && originalBody.isPresent()) siblings.add(originalBody.get());
                cir.setReturnValue(Optional.of(bodyRoot));
            }
        } catch (Exception e) {
            irisSearch$debugLog("Failed to build comment body: " + e);
        }
    }

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[BaseOptionElementWidgetMixin] " + message);
    }
}
