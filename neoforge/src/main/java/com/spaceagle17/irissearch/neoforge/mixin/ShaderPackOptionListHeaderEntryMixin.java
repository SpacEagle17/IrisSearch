package com.spaceagle17.irissearch.neoforge.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.neoforge.ISearchableHeaderEntry;
import com.spaceagle17.irissearch.neoforge.ISearchableOptionList;
import com.spaceagle17.irissearch.neoforge.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.IrisElementRow;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;

@SuppressWarnings("NameDoesntMatchTargetClass")
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.element.ShaderPackOptionList$HeaderEntry", remap = false)
public abstract class ShaderPackOptionListHeaderEntryMixin implements ISearchableHeaderEntry {

    @Shadow @Final @Mutable private IrisElementRow backButton;
    @Shadow @Final private static int MIN_SIDE_BUTTON_WIDTH;

    @Unique private static final String CLEAR_BUTTON_EMOJI = "❌ ";
    @Unique private static final String SEARCH_BUTTON_EMOJI = "🔍 ";
    @Unique private static final int SEARCH_BOX_GAP = 3;

    @Unique private static final String SEARCH_TOOLTIP_KEY = "iris_search.tooltip.search";
    @Unique private static final String CLEAR_TOOLTIP_KEY = "iris_search.tooltip.clear";

    @Unique private Object irisSearch$searchToggleButtonElement;
    @Unique private String irisSearch$searchToggleTooltipText;

    @Unique
    private static void irisSearch$debugLog(String message) {
        IrisSearchLogger.debugLog("[ShaderPackOptionListHeaderEntryMixin] " + message);
    }

    @Override
    public Object irisSearch$getSearchToggleButton() {
        return this.irisSearch$searchToggleButtonElement;
    }

    @Override
    public String irisSearch$getSearchToggleTooltipText() {
        return this.irisSearch$searchToggleTooltipText;
    }

    @Unique
    private static Object irisSearch$createLiteralComponent(String text) {
        try {
            boolean isMojang = ReflectionUtils.checkClassExists("net.minecraft.network.chat.Component");
            String componentClass = isMojang ? "net.minecraft.network.chat.Component" : "net.minecraft.class_2561";
            String methodName = isMojang ? "literal" : "method_43470";

            Object literalComp = ReflectionUtils.invokeMethod(componentClass, methodName, new Class<?>[]{String.class}, text);
            if (literalComp != null) {
                return literalComp;
            }

            Class<?> headerEntryClass = Class.forName("net.irisshaders.iris.gui.element.ShaderPackOptionList$HeaderEntry");
            return ReflectionUtils.getFieldValue(headerEntryClass, "BACK_BUTTON_TEXT");
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private static int irisSearch$getFontWidth(Object component) {
        Object font = MinecraftBridge.getMinecraftFont();
        return MinecraftBridge.getFontWidth(font, component);
    }

    @Unique
    private Object irisSearch$createDynamicIrisButton(Object buttonText, String purpose, Runnable action) throws Exception {
        Class<?> textButtonClass = Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$TextButtonElement");

        for (Constructor<?> ctor : textButtonClass.getConstructors()) {
            if (ctor.getParameterTypes().length == 2) {
                Class<?> actionInterface = ctor.getParameterTypes()[1];
                Object proxyInstance = Proxy.newProxyInstance(
                        actionInterface.getClassLoader(),
                        new Class<?>[]{actionInterface},
                        (proxy, method, args) -> {
                            if (method.getName().equals("equals") || method.getName().equals("hashCode") || method.getName().equals("toString")) {
                                return method.invoke(action, args);
                            }
                            irisSearch$debugLog("Proxy callback fired for button purpose: " + purpose);
                            action.run();

                            if (method.getName().equals("apply") || method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                                return Boolean.TRUE;
                            }
                            return null;
                        }
                );
                return ctor.newInstance(buttonText, proxyInstance);
            }
        }
        throw new NoSuchMethodException("Could not locate TextButtonElement constructor.");
    }

    // -- Variant 1: non-static HeaderEntry (outer param present), OFFICIAL mappings --
    @Inject(
            method = "<init>(Lnet/irisshaders/iris/gui/element/ShaderPackOptionList;Lnet/irisshaders/iris/gui/screen/ShaderPackScreen;Lnet/irisshaders/iris/gui/NavigationController;Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$applyOfficialWithOuter(
            @Coerce Object outerList, ShaderPackScreen screen, NavigationController navigation,
            @Coerce Object text, boolean hasBackButton, CallbackInfo ci) {
        irisSearch$applySearchAwareHeader(outerList, screen, navigation, text, hasBackButton);
    }

    // -- Variant 3: static HeaderEntry (no outer param), OFFICIAL mappings --
    @Dynamic
    @Inject(
            method = "<init>(Lnet/irisshaders/iris/gui/screen/ShaderPackScreen;Lnet/irisshaders/iris/gui/NavigationController;Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$applyOfficialNoOuter(
            ShaderPackScreen screen, NavigationController navigation,
            @Coerce Object text, boolean hasBackButton, CallbackInfo ci) {
        Object outerList = ReflectionUtils.getFieldByType(screen, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
        if (outerList == null) {
            irisSearch$debugLog("Static-HeaderEntry (official) path: could not resolve outer ShaderPackOptionList, skipping");
            return;
        }
        irisSearch$applySearchAwareHeader(outerList, screen, navigation, text, hasBackButton);
    }

    @SuppressWarnings("unused")
    @Unique
    private void irisSearch$applySearchAwareHeader(
            Object outerList, ShaderPackScreen screen, NavigationController navigation,
            Object text, boolean hasBackButton) {
        try {
            ISearchableOptionList searchable = (ISearchableOptionList) outerList;
            boolean isSubScreen = navigation.getCurrentScreen() != null;
            irisSearch$debugLog("Applying Search Header: isSubScreen=" + isSubScreen + ", searchModeActive=" + searchable.irisSearch$isSearchModeActive());

            if (isSubScreen && searchable.irisSearch$isSearchModeActive()) {
                irisSearch$debugLog("Sub-screen opened with search active, force-disabling search mode.");
                searchable.irisSearch$disableSearchMode();
            }

            this.irisSearch$searchToggleButtonElement = null;
            this.irisSearch$searchToggleTooltipText = null;

            if (!isSubScreen || hasBackButton) {
                Object buttonText;
                if (isSubScreen) {
                    buttonText = ReflectionUtils.getFieldValue(this.getClass(), "BACK_BUTTON_TEXT");
                    if (buttonText == null) buttonText = irisSearch$createLiteralComponent("< Back");
                } else {
                    buttonText = searchable.irisSearch$isSearchModeActive()
                            ? MinecraftBridge.appendComponent(irisSearch$createLiteralComponent(CLEAR_BUTTON_EMOJI), MinecraftBridge.createTranslatableComponent("iris_search.button.clear"))
                            : MinecraftBridge.appendComponent(irisSearch$createLiteralComponent(SEARCH_BUTTON_EMOJI), MinecraftBridge.createTranslatableComponent("iris_search.button.search"));
                }

                int width = Math.max(MIN_SIDE_BUTTON_WIDTH, irisSearch$getFontWidth(buttonText) + 8);
                irisSearch$debugLog("Button text resolved, calculated width: " + width);

                if (!isSubScreen) {
                    searchable.irisSearch$setReservedLeftWidth(width + SEARCH_BOX_GAP);
                    irisSearch$debugLog("Published reserved left width: " + (width + SEARCH_BOX_GAP));
                }

                Object element;
                if (isSubScreen) {
                    final Object[] elementHolder = new Object[1];
                    elementHolder[0] = irisSearch$createDynamicIrisButton(buttonText, "BACK_ACTION", () -> {
                        irisSearch$debugLog("Invoking original backButtonClicked target method dynamically.");
                        ReflectionUtils.invokeMethod(this, "backButtonClicked", null, elementHolder[0]);
                    });
                    element = elementHolder[0];
                } else {
                    element = irisSearch$createDynamicIrisButton(buttonText, "SEARCH_TOGGLE_ACTION", () -> {
                        irisSearch$debugLog("Search button action trigger point hit!");
                        this.irisSearch$searchButtonClicked();
                    });

                    this.irisSearch$searchToggleButtonElement = element;
                    this.irisSearch$searchToggleTooltipText =
                            searchable.irisSearch$isSearchModeActive() ? CLEAR_TOOLTIP_KEY : SEARCH_TOOLTIP_KEY;
                }

                this.backButton = new IrisElementRow();
                ReflectionUtils.invokeMethod(this.backButton, "add",
                        new Class<?>[]{Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$Element"), int.class}, element, width);
                irisSearch$debugLog("Header component injection routine completed successfully.");
            } else {
                this.backButton = null;
                irisSearch$debugLog("No header buttons required on this configuration context.");
            }
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't build the search header for one of the shader option screens." + e);
            irisSearch$debugLog("Cross-version exception handling header setup: " + e);
        }
    }

    @Unique
    private void irisSearch$searchButtonClicked() {
        irisSearch$debugLog("irisSearch$searchButtonClicked method execution started.");
        try {
            GuiUtil.playButtonClickSound();

            Object listObj = ReflectionUtils.getFieldValue(this, "this$0");
            if (listObj == null) {
                irisSearch$debugLog("this$0 field missing/null, searching inside 'screen' fields...");
                Object screenObj = ReflectionUtils.getFieldValue(this, "screen");
                listObj = ReflectionUtils.getFieldByType(screenObj, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
            }

            if (listObj == null) {
                irisSearch$debugLog("CRITICAL: Failed to locate ShaderPackOptionList reference anywhere.");
                return;
            }

            ISearchableOptionList searchable = (ISearchableOptionList) listObj;
            if (searchable.irisSearch$isSearchModeActive()) {
                irisSearch$debugLog("Flipping mode: Disabling search mode and rebuilding option list.");
                searchable.irisSearch$disableSearchModeAndRebuild();
            } else {
                irisSearch$debugLog("Flipping mode: Enabling search mode and rebuilding option list.");
                searchable.irisSearch$enableSearchModeAndRebuild();
            }
        } catch (Exception e) {
            IrisSearch.log(3, "Error occurred inside searchButtonClicked click routine: " + e);
            irisSearch$debugLog("Error occurred inside searchButtonClicked click routine: " + e);
        }
    }

    /**
     * Render tooltips for the search/clear button — old Iris render API (bare "render" name, 10-param form).
     * On NeoForge with Mojang mappings the method is called "render", not a Yarn intermediary name.
     */
    @Dynamic
    @Inject(method = "render", at = @At("TAIL"), remap = false, require = 0)
    private void irisSearch$onRenderBareName(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                                             int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        try {
            MinecraftBridge.queueHeaderTooltip(guiGraphics, this.irisSearch$searchToggleButtonElement,
                    this.irisSearch$searchToggleTooltipText, x, y - 16);
        } catch (Throwable t) {
            irisSearch$debugLog("Failed queueing search button tooltip (bare render name TAIL): " + t);
        }
    }

    /**
     * Render tooltips for the search/clear button — new Iris render API (extractContent, Mojang 1.26+).
     */
    @Dynamic
    @Inject(method = "extractContent", at = @At("TAIL"), remap = false, require = 0)
    private void irisSearch$onRenderOfficial(@Coerce Object guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta, CallbackInfo ci) {
        try {
            if (this.irisSearch$searchToggleButtonElement == null) {
                return;
            }
            int x = MinecraftBridge.invokeIntGetter(this, "getX", null, 0);
            int y = MinecraftBridge.invokeIntGetter(this, "getY", null, 0);
            MinecraftBridge.queueHeaderTooltip(guiGraphics, this.irisSearch$searchToggleButtonElement,
                    this.irisSearch$searchToggleTooltipText, x, y - 16);
        } catch (Throwable t) {
            irisSearch$debugLog("Failed queueing search button tooltip (official extractContent path): " + t);
        }
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

    /*
     * Suppresses all utility buttons and shader name text from rendering when search is active.
     * Returns true if the row was suppressed (search active and outer list successfully resolved).
     */
    @Unique
    private boolean irisSearch$suppressRow(Object guiGraphics, int x, int y, int entryWidth, int entryHeight,
                                           int mouseX, int mouseY, boolean hovered, float tickDelta, boolean usesGetterShape) {
        Object outerList = irisSearch$resolveOuterList();
        if (outerList == null) {
            return false;
        }

        try {
            ((ISearchableOptionList) outerList).irisSearch$publishHeaderRowBounds(x, y, entryWidth, entryHeight, usesGetterShape);
        } catch (Throwable e) {
            irisSearch$debugLog("Could not publish header row bounds to outer list, skipping bounds update: " + e);
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

            try {
                ReflectionUtils.invokeMethod(guiGraphics, "fill",
                        new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                        x - 3, (y + entryHeight) - 2, x + entryWidth, (y + entryHeight) - 1, 0x66BEBEBE);
            } catch (Throwable t) {
                irisSearch$debugLog("Could not draw the divider line during suppressed render: " + t);
            }

            if (this.backButton != null) {
                try {
                    ReflectionUtils.invokeMethod(this.backButton, "render",
                            new Class<?>[]{guiGraphics.getClass(), int.class, int.class, int.class, int.class, int.class, float.class, boolean.class},
                            guiGraphics, x, y, 16, mouseX, mouseY, tickDelta, hovered);
                } catch (Throwable t) {
                    irisSearch$debugLog("Could not render the search/clear button during suppressed render: " + t);
                }
            }

            try {
                MinecraftBridge.queueHeaderTooltip(guiGraphics, this.irisSearch$searchToggleButtonElement,
                        this.irisSearch$searchToggleTooltipText, x, y - 16);
            } catch (Throwable t) {
                irisSearch$debugLog("Could not queue the search button tooltip during suppressed render: " + t);
            }

            return true;
        } catch (Throwable t) {
            IrisSearch.log(3, "Header row couldn't render correctly while searching." + t);
            irisSearch$debugLog("Reflective row suppression failed: " + t);
            return false;
        }
    }

    @Dynamic
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onSuppressBareName(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                                               int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        if (irisSearch$suppressRow(guiGraphics, x, y, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta, false)) {
            ci.cancel();
        }
    }

    @Dynamic
    @Inject(method = "extractContent", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onSuppressOfficial(@Coerce Object guiGraphics, int mouseX, int mouseY, boolean isHovered, float tickDelta, CallbackInfo ci) {
        int x = MinecraftBridge.invokeIntGetter(this, "getX", null, 0);
        int y = MinecraftBridge.invokeIntGetter(this, "getY", null, 0);
        int entryWidth = MinecraftBridge.invokeIntGetter(this, "getWidth", null, 0);
        int entryHeight = MinecraftBridge.invokeIntGetter(this, "getHeight", null, 0);

        if (irisSearch$suppressRow(guiGraphics, x, y, entryWidth, entryHeight, mouseX, mouseY, isHovered, tickDelta, true)) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/AbstractWidget;renderScrollingString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIIII)V",
                    remap = false
            ),
            index = 4
    )
    private int irisSearch$widenTitleLeftBound(int minX) {
        if (this.backButton != null) {
            return minX + this.backButton.getWidth();
        }
        return minX;
    }

    /** Official (Mojang) counterpart to the title-left-bound widener; targets the acceptScrolling call inside extractContent. */
    @Dynamic
    @ModifyArg(
            method = {"extractContent", "renderContent"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/ActiveTextCollector;acceptScrolling(Lnet/minecraft/network/chat/Component;IIIII)V",
                    remap = false
            ),
            index = 2,
            remap = false,
            require = 0
    )
    private int irisSearch$widenTitleLeftBoundOfficial(int minX) {
        try {
            if (this.backButton != null) {
                return minX + this.backButton.getWidth();
            }
        } catch (Throwable t) {
            irisSearch$debugLog("Failed widening title left bound for back button (official path): " + t);
        }
        return minX;
    }
}
