package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.forge.ISearchableOptionContainer;
import com.spaceagle17.irissearch.forge.ISearchableOptionList;
import com.spaceagle17.irissearch.forge.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.element.widget.AbstractElementWidget;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.irisshaders.iris.shaderpack.option.menu.OptionMenuOptionElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ShaderPackOptionList.ElementRowEntry.class, remap = false)
public abstract class ElementRowEntryMixin {

    @Shadow @Final private List<AbstractElementWidget<?>> widgets;
    @Shadow @Final private ShaderPackScreen screen;

    @Shadow public abstract int getHoveredWidget(int mouseX);

    // m_6375_ = mouseClicked(double, double, int); Forge production uses SRG names
    @Inject(method = "m_6375_(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onRowClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (irisSearch$navigateToClickedOption((int) mouseX)) cir.setReturnValue(true);
    }

    /**
     * If Ctrl is held and a search is active, exit search and walk the option list to the clicked
     * option's home submenu (its breadcrumb path). Returns true when handled, so the caller cancels
     * the normal value change.
     */
    @Unique
    private boolean irisSearch$navigateToClickedOption(int mouseX) {
        try {
            // Shift keeps priority: shift+click still does Iris's "reset to default", matching the hover hint.
            if (!MinecraftBridge.isControlDown() || MinecraftBridge.isShiftDown()
                    || this.screen == null
                    || this.widgets == null || this.widgets.isEmpty()) {
                return false;
            }

            // navigation lives on the ElementRowEntry's super (BaseEntry)
            Object navObj = ReflectionUtils.getFieldValue(this, "navigation");
            if (!(navObj instanceof NavigationController navigation)) return false;

            Object optionList = ReflectionUtils.getFieldValue(this.screen, "shaderOptionList");
            if (!(optionList instanceof ISearchableOptionList searchable) || !searchable.irisSearch$isSearchModeActive()) {
                return false;
            }

            int index = getHoveredWidget(mouseX);
            if (index < 0 || index >= this.widgets.size()) return false;

            Object elementObj = ReflectionUtils.getFieldValue(this.widgets.get(index), "element");
            if (!(elementObj instanceof OptionMenuOptionElement optEl) || optEl.optionId == null) return false;
            if (!(optEl.container instanceof ISearchableOptionContainer container)) return false;

            String rawPath = container.irisSearch$getOptionPath(optEl.optionId); // "root/A/B", or "root"/"unknown"

            List<String> segments = new ArrayList<>();
            if (rawPath != null) {
                for (String segment : rawPath.split("/")) {
                    if (!segment.isEmpty() && !"root".equals(segment) && !"unknown".equals(segment)) segments.add(segment);
                }
            }

            // Remember the query so the back button in the target menu returns to the search screen with the same initial query
            String query = searchable.irisSearch$getTypedSearchQuery();
            int cursor = searchable.irisSearch$getSavedCursorPosition();

            searchable.irisSearch$disableSearchMode();

            if (segments.isEmpty()) {
                navigation.rebuild(); // root-level option: no submenu to open, aka no back button to return to search
            } else {
                // We need to load it all so when manually exploring, when going back we can also go back the full tree
                for (String segment : segments) {
                    navigation.open(segment);
                }
                searchable.irisSearch$armSearchReturn(query, cursor);
            }

            GuiUtil.playButtonClickSound();
            irisSearch$debugLog("Ctrl+Click -> navigated to \"" + rawPath + "\"");
            return true;
        } catch (Throwable t) {
            irisSearch$debugLog("navigateToClickedOption failed: " + t);
            return false;
        }
    }

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[ElementRowEntryMixin] " + message);
    }
}
