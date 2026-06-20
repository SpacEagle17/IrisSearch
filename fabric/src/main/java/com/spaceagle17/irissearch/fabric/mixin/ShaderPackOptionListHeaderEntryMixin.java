package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.fabric.ISearchableHeaderEntry;
import com.spaceagle17.irissearch.fabric.ISearchableOptionList;
import com.spaceagle17.irissearch.fabric.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.IrisElementRow;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;

@SuppressWarnings("NameDoesntMatchTargetClass")
@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.element.ShaderPackOptionList$HeaderEntry", remap = false, priority = 1500)
public abstract class ShaderPackOptionListHeaderEntryMixin implements ISearchableHeaderEntry {

    @Shadow @Final @Mutable private IrisElementRow backButton;
    @Shadow @Final private static int MIN_SIDE_BUTTON_WIDTH;

    @Unique private static final String CLEAR_TEXT_RAW = "❌ Clear";
    @Unique private static final String SEARCH_TEXT_RAW = "\uD83D\uDD0D Search";
    @Unique private static final int SEARCH_BOX_GAP = 3;

    @Unique private static final String SEARCH_TOOLTIP_TEXT = "Search Shader Settings (Ctrl + F)";
    @Unique private static final String CLEAR_TOOLTIP_TEXT = "Exit Shader Search (Esc)";

    @Unique private Object irisSearch$searchToggleButtonElement;
    @Unique private String irisSearch$searchToggleTooltipText;

    @Unique private static int irisSearch$backButtonWidth = 0;

    @Unique private int irisSearch$capturedX;
    @Unique private int irisSearch$capturedY;
    @Unique private int irisSearch$capturedEntryWidth;

    @Unique
    private static void debugLog(String message) {
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
    private static Object createLiteralComponent(String text) {
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
    private static int getFontWidth(Object component) {
        try {
            String mcClassStr = ReflectionUtils.checkClassExists("net.minecraft.client.Minecraft")
                    ? "net.minecraft.client.Minecraft" : "net.minecraft.class_310";
            Object mcInstance = ReflectionUtils.invokeMethod(mcClassStr, "getInstance", null);

            String fontFieldName = ReflectionUtils.checkClassExists("net.minecraft.client.Minecraft")
                    ? "font" : "field_3772";
            Object fontRenderer = ReflectionUtils.getFieldValue(mcInstance, fontFieldName);

            String widthMethodName = ReflectionUtils.checkClassExists("net.minecraft.client.Minecraft")
                    ? "width" : "method_1727";

            Object result = ReflectionUtils.invokeMethod(fontRenderer, widthMethodName, null, component);
            return result != null ? (int) result : 50;
        } catch (Exception e) {
            return 50;
        }
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
                            debugLog("Proxy callback fired for button purpose: " + purpose);
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

    // -- Variant 1: non-static HeaderEntry (outer param present), OFFICIAL mappings (26.1) --
    @Inject(
            method = "<init>(Lnet/irisshaders/iris/gui/element/ShaderPackOptionList;Lnet/irisshaders/iris/gui/screen/ShaderPackScreen;Lnet/irisshaders/iris/gui/NavigationController;Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$applyOfficialWithOuter(
            @Coerce Object outerList, ShaderPackScreen screen, NavigationController navigation,
            @Coerce Object text, boolean hasBackButton, CallbackInfo ci) {
        irisSearch$applySearchAwareHeader(outerList, screen, navigation, text, hasBackButton);
    }

    // -- Variant 2: non-static HeaderEntry (outer param present), INTERMEDIARY mappings (1.21.1 Yarn) --
    @Dynamic
    @Inject(
            method = "<init>(Lnet/irisshaders/iris/gui/element/ShaderPackOptionList;Lnet/irisshaders/iris/gui/screen/ShaderPackScreen;Lnet/irisshaders/iris/gui/NavigationController;Lnet/minecraft/class_2561;Z)V",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$applyIntermediaryWithOuter(
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
            debugLog("Static-HeaderEntry (official) path: could not resolve outer ShaderPackOptionList, skipping");
            return;
        }
        irisSearch$applySearchAwareHeader(outerList, screen, navigation, text, hasBackButton);
    }

    // -- Variant 4: static HeaderEntry (no outer param), INTERMEDIARY mappings (1.20.1 Yarn) --
    @Dynamic
    @Inject(
            method = "<init>(Lnet/irisshaders/iris/gui/screen/ShaderPackScreen;Lnet/irisshaders/iris/gui/NavigationController;Lnet/minecraft/class_2561;Z)V",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$applyIntermediaryNoOuter(
            ShaderPackScreen screen, NavigationController navigation,
            @Coerce Object text, boolean hasBackButton, CallbackInfo ci) {
        Object outerList = ReflectionUtils.getFieldByType(screen, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
        if (outerList == null) {
            debugLog("Static-HeaderEntry (intermediary) path: could not resolve outer ShaderPackOptionList, skipping");
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
            debugLog("Applying Search Header: isSubScreen=" + isSubScreen + ", searchModeActive=" + searchable.irisSearch$isSearchModeActive());

            if (isSubScreen && searchable.irisSearch$isSearchModeActive()) {
                debugLog("Sub-screen opened with search active, force-disabling search mode.");
                searchable.irisSearch$disableSearchMode();
            }

            this.irisSearch$searchToggleButtonElement = null;
            this.irisSearch$searchToggleTooltipText = null;

            if (!isSubScreen || hasBackButton) {
                Object buttonText;
                if (isSubScreen) {
                    buttonText = ReflectionUtils.getFieldValue(this.getClass(), "BACK_BUTTON_TEXT");
                    if (buttonText == null) buttonText = createLiteralComponent("< Back");
                } else {
                    buttonText = searchable.irisSearch$isSearchModeActive()
                            ? createLiteralComponent(CLEAR_TEXT_RAW)
                            : createLiteralComponent(SEARCH_TEXT_RAW);
                }

                int width = Math.max(MIN_SIDE_BUTTON_WIDTH, getFontWidth(buttonText) + 8);
                irisSearch$backButtonWidth = width;
                debugLog("Button text resolved, calculated width: " + width);

                if (!isSubScreen) {
                    searchable.irisSearch$setReservedLeftWidth(width + SEARCH_BOX_GAP);
                    debugLog("Published reserved left width: " + (width + SEARCH_BOX_GAP));
                }

                Object element;
                if (isSubScreen) {
                    final Object[] elementHolder = new Object[1];
                    elementHolder[0] = irisSearch$createDynamicIrisButton(buttonText, "BACK_ACTION", () -> {
                        debugLog("Invoking original backButtonClicked target method dynamically.");
                        ReflectionUtils.invokeMethod(this, "backButtonClicked", null, elementHolder[0]);
                    });
                    element = elementHolder[0];
                } else {
                    element = irisSearch$createDynamicIrisButton(buttonText, "SEARCH_TOGGLE_ACTION", () -> {
                        debugLog("Search button action trigger point hit!");
                        this.irisSearch$searchButtonClicked();
                    });

                    this.irisSearch$searchToggleButtonElement = element;
                    this.irisSearch$searchToggleTooltipText =
                            searchable.irisSearch$isSearchModeActive() ? CLEAR_TOOLTIP_TEXT : SEARCH_TOOLTIP_TEXT;
                }

                this.backButton = new IrisElementRow();
                ReflectionUtils.invokeMethod(this.backButton, "add",
                        new Class<?>[]{Class.forName("net.irisshaders.iris.gui.element.IrisElementRow$Element"), int.class}, element, width);
                debugLog("Header component injection routine completed successfully.");
            } else {
                this.backButton = null;
                debugLog("No header buttons required on this configuration context.");
            }
        } catch (Exception e) {
            IrisSearch.log(3, "Couldn't build the search header for one of the shader option screens.");
            debugLog("Cross-version exception handling header setup: " + e);
        }
    }

    @Unique
    private void irisSearch$searchButtonClicked() {
        debugLog("irisSearch$searchButtonClicked method execution started.");
        try {
            GuiUtil.playButtonClickSound();

            Object listObj = ReflectionUtils.getFieldValue(this, "this$0");
            if (listObj == null) {
                debugLog("this$0 field missing/null, searching inside 'screen' fields...");
                Object screenObj = ReflectionUtils.getFieldValue(this, "screen");
                listObj = ReflectionUtils.getFieldByType(screenObj, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
            }

            if (listObj == null) {
                debugLog("CRITICAL: Failed to locate ShaderPackOptionList reference anywhere.");
                return;
            }

            ISearchableOptionList searchable = (ISearchableOptionList) listObj;
            if (searchable.irisSearch$isSearchModeActive()) {
                debugLog("Flipping mode: Disabling search mode and rebuilding option list.");
                searchable.irisSearch$disableSearchModeAndRebuild();
            } else {
                debugLog("Flipping mode: Enabling search mode and rebuilding option list.");
                searchable.irisSearch$enableSearchModeAndRebuild();
            }
        } catch (Exception e) {
            debugLog("Error occurred inside searchButtonClicked click routine: " + e);
        }
    }

    /**
     * Render the tooltips of the search and clear buttons - yarn
     */
    @Dynamic
    @Inject(
            method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$onRenderYarnIntermediary(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                                                     int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        try {
            MinecraftBridge.queueHeaderTooltip(guiGraphics, this.irisSearch$searchToggleButtonElement,
                    this.irisSearch$searchToggleTooltipText, x, y - 16);
        } catch (Throwable t) {
            debugLog("Failed queueing search button tooltip (Yarn intermediary render path): " + t);
        }
    }

    /**
     * Render the tooltips of the search and clear buttons - official
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
            debugLog("Failed queueing search button tooltip (official extractContent path): " + t);
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
            debugLog("Could not publish header row bounds to outer list, skipping bounds update: " + e);
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
                debugLog("Could not draw the divider line during suppressed render: " + t);
            }

            if (this.backButton != null) {
                try {
                    ReflectionUtils.invokeMethod(this.backButton, "render",
                            new Class<?>[]{guiGraphics.getClass(), int.class, int.class, int.class, int.class, int.class, float.class, boolean.class},
                            guiGraphics, x, y, 16, mouseX, mouseY, tickDelta, hovered);
                } catch (Throwable t) {
                    debugLog("Could not render the search/clear button during suppressed render: " + t);
                }
            }

            try {
                MinecraftBridge.queueHeaderTooltip(guiGraphics, this.irisSearch$searchToggleButtonElement,
                        this.irisSearch$searchToggleTooltipText, x, y - 16);
            } catch (Throwable t) {
                debugLog("Could not queue the search button tooltip during suppressed render: " + t);
            }

            return true;
        } catch (Throwable t) {
            IrisSearch.log(3, "Header row couldn't render correctly while searching.");
            debugLog("Reflective row suppression failed: " + t);
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
    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onSuppressYarnIntermediary(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
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

    /** Official (26.1) counterpart to the title-left-bound widener in YarnShaderPackOptionListHeaderEntryMixin. */
    @Dynamic
    @ModifyArg(
            method = "extractContent",
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
            debugLog("Failed widening title left bound for back button (official path): " + t);
        }
        return minX;
    }

    @Dynamic
    @Inject(method = "method_25343(Lnet/minecraft/class_332;IIIIIIIZF)V", at = @At("HEAD"), remap = false, require = 0)
    private void irisSearch$captureRenderParams(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        this.irisSearch$capturedX = x;
        this.irisSearch$capturedY = y;
        this.irisSearch$capturedEntryWidth = entryWidth;
    }

    @Dynamic
    @Redirect(method = "method_25343", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_332;method_27534(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;III)V"), remap = false, require = 0)
    private void irisSearch$redirectDrawCenteredString(@Coerce Object guiGraphics, @Coerce Object font, @Coerce Object text, int x, int y, int color) {
        try {
            Object utilityButtons = ReflectionUtils.getFieldValue(this, "utilityButtons");
            if (utilityButtons != null) {
                // Try to find the getter width from either our mixin or Euphoria Patcher's mixin wrapper
                int utilityButtonsWidth = (int) utilityButtons.getClass().getMethod("irisSearch$getWidth").invoke(utilityButtons);

                int minX = this.irisSearch$capturedX + 5;

                // Directly adjust the bounding box for our search toggle element when active
                if (this.backButton != null) {
                    minX += irisSearch$backButtonWidth;
                }

                int minY = this.irisSearch$capturedY + 5;
                int maxX = ((this.irisSearch$capturedX + this.irisSearch$capturedEntryWidth) - 10) - utilityButtonsWidth;
                int maxY = this.irisSearch$capturedY + 15;

                // Call our freshly converted 1:1 local scrolling calculation
                irisSearch$renderScrollingString(guiGraphics, font, text, x, minX, minY, maxX, maxY, 0xFFFFFF);
                return;
            }
        } catch (Exception e) {
            debugLog("Error in irisSearch$redirectDrawCenteredString redirection layer: " + e.getMessage());
        }

        // Vanilla fallback loop execution if utilities are absent or reflection properties error out
        try {
            Class<?> guiGraphicsClass = Class.forName("net.minecraft.class_332");
            Class<?> fontClass = Class.forName("net.minecraft.class_327");
            Class<?> componentClass = Class.forName("net.minecraft.class_2561");
            guiGraphicsClass.getMethod("method_27534", fontClass, componentClass, int.class, int.class, int.class).invoke(guiGraphics, font, text, x, y, color);
        } catch (Exception fallbackEx) {
            debugLog("Critical rendering fallback failed: " + fallbackEx.getMessage());
        }
    }

    @Unique
    private static void irisSearch$renderScrollingString(Object guiGraphics, Object font, Object text, int centerX, int minX, int minY, int maxX, int maxY, int color) {
        try {
            // Get text width: font.width(text)
            int textWidth = (int) font.getClass().getMethod("method_27525", Class.forName("net.minecraft.class_5348")).invoke(font, text);
            int yPos = (minY + maxY - 9) / 2 + 1;
            int availableWidth = maxX - minX;

            Class<?> guiGraphicsClass = Class.forName("net.minecraft.class_332");
            Class<?> fontClass = Class.forName("net.minecraft.class_327");
            Class<?> componentClass = Class.forName("net.minecraft.class_2561");


            if (textWidth > availableWidth) {
                // Text is too wide, scroll it with smooth sine wave animation
                int scrollRange = textWidth - availableWidth;

                // Get current time: Util.getMillis()
                long currentTimeMillis =  Util.getMillis();
                double currentTime = (double) currentTimeMillis / 1000.0;

                double scrollDuration = Math.max((double) scrollRange * 0.5, 3.0);
                double scrollProgress = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * currentTime / scrollDuration)) / 2.0 + 0.5;

                // Mth.lerp()
                Class<?> mthClass = Class.forName("net.minecraft.class_3532");
                double scrollOffset = (double) mthClass.getMethod("method_16436", double.class, double.class, double.class).invoke(null, scrollProgress, 0.0, (double) scrollRange);

                // guiGraphics.enableScissor()
                guiGraphicsClass.getMethod("method_44379", int.class, int.class, int.class, int.class).invoke(guiGraphics, minX, minY, maxX, maxY);

                // guiGraphics.drawString(font, text, x, y, color)
                guiGraphicsClass.getMethod("method_27535", fontClass, componentClass, int.class, int.class, int.class)
                        .invoke(guiGraphics, font, text, minX - (int) scrollOffset, yPos, color);

                // guiGraphics.disableScissor()
                guiGraphicsClass.getMethod("method_44380").invoke(guiGraphics);
                debugLog("Finished rendering scrolling text.");
            } else {
                // Text fits, center it at the specified centerX position using drawCenteredString
                guiGraphicsClass.getMethod("method_27534", fontClass, componentClass, int.class, int.class, int.class)
                        .invoke(guiGraphics, font, text, centerX, yPos, color);
            }
        } catch (Exception e) {
           debugLog("Error inside converted renderScrollingString: " + e.getMessage());
        }
    }


}