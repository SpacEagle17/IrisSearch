package com.spaceagle17.irissearch.forge.mixin;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.forge.ISearchableHeaderEntry;
import com.spaceagle17.irissearch.forge.ISearchableOptionList;
import com.spaceagle17.irissearch.forge.MinecraftBridge;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import net.irisshaders.iris.gui.GuiUtil;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.IrisElementRow;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.irisshaders.iris.gui.screen.ShaderPackScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    @Unique private static final String SEARCH_TEXT_RAW = "🔍 Search";
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
        // Try both Mojang dev name ("literal") and Forge SRG production name ("m_237113_"),
        // plus Fabric intermediary. String literals are not remapped by reobfJar so we must
        // enumerate all candidate names here.
        String[] componentClasses = {"net.minecraft.src.C_4996_", "net.minecraft.network.chat.Component", "net.minecraft.class_2561"};
        String[] methodNames = {"m_237113_", "literal", "method_43470"};
        for (String cls : componentClasses) {
            if (!ReflectionUtils.checkClassExists(cls)) continue;
            for (String mn : methodNames) {
                try {
                    Object result = ReflectionUtils.invokeMethod(cls, mn, new Class<?>[]{String.class}, text);
                    if (result != null) {
                        irisSearch$debugLog("createLiteralComponent(\"" + text + "\"): OK via " + cls + "#" + mn);
                        return result;
                    }
                } catch (Exception ignored) {}
            }
        }
        irisSearch$debugLog("createLiteralComponent(\"" + text + "\"): all candidates failed, falling back to BACK_BUTTON_TEXT");
        try {
            Class<?> headerEntryClass = Class.forName("net.irisshaders.iris.gui.element.ShaderPackOptionList$HeaderEntry");
            return ReflectionUtils.getFieldValue(headerEntryClass, "BACK_BUTTON_TEXT");
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private static int irisSearch$getFontWidth(Object component) {
        // Class name is Mojang on Forge (net.minecraft.client.Minecraft).
        // Field name is SRG (f_91062_) in production, "font" in dev.
        // width(Component) is m_92852_ in SRG, "width" in dev.  m_92895_ is width(String), not Component.
        String[] mcClasses  = {"net.minecraft.client.Minecraft", "net.minecraft.src.C_3391_", "net.minecraft.class_310"};
        String[] getInstanceMethods = {"m_91087_", "getInstance", "method_1551"};
        String[] fontFields = {"f_91062_", "font", "field_1772"};
        String[] widthMethods = {"m_92852_", "m_92895_", "width", "method_1727"};
        for (String mcCls : mcClasses) {
            if (!ReflectionUtils.checkClassExists(mcCls)) continue;
            Object mcInstance = null;
            String foundGim = null;
            for (String gim : getInstanceMethods) {
                try { mcInstance = ReflectionUtils.invokeMethod(mcCls, gim, null); } catch (Exception ignored) {}
                if (mcInstance != null) { foundGim = gim; break; }
            }
            if (mcInstance == null) continue;
            for (String ff : fontFields) {
                Object font = ReflectionUtils.getFieldValue(mcInstance, ff);
                if (font == null) continue;
                for (String wm : widthMethods) {
                    try {
                        Object result = ReflectionUtils.invokeMethod(font, wm, null, component);
                        if (result instanceof Integer) {
                            irisSearch$debugLog("getFontWidth: OK via " + mcCls + "#" + foundGim + " -> field " + ff + " -> " + wm);
                            return (int) result;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        irisSearch$debugLog("getFontWidth: all candidates failed, returning default 50");
        return 50;
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

//    @Dynamic
//    @Inject(
//            method = "<init>(Lnet/irisshaders/iris/gui/screen/ShaderPackScreen;Lnet/irisshaders/iris/gui/NavigationController;Lnet/minecraft/network/chat/Component;Z)V",
//            at = @At("TAIL"), remap = false, require = 0
//    )
//    private void irisSearch$applyOfficialWithOuter(
//            ShaderPackScreen screen, NavigationController navigation,
//            @Coerce Object text, boolean hasBackButton, CallbackInfo ci) {
//        Object outerList = ReflectionUtils.getFieldValue(this, "this$0");
//        if (outerList == null) {
//            outerList = ReflectionUtils.getFieldByType(screen, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
//        }
//        irisSearch$applySearchAwareHeader(outerList, screen, navigation, text, hasBackButton);
//    }

    @Dynamic
    @Inject(
            method = "<init>",
            at = @At("TAIL"), remap = false, require = 0
    )
    private void irisSearch$applyOfficialWithOuterSecond(
            ShaderPackScreen screen, NavigationController navigation, Component text, boolean hasBackButton, CallbackInfo ci) {
        Object outerList = ReflectionUtils.getFieldValue(this, "this$0");
        if (outerList == null) {
            outerList = ReflectionUtils.getFieldByType(screen, "net.irisshaders.iris.gui.element.ShaderPackOptionList");
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
                            ? irisSearch$createLiteralComponent(CLEAR_TEXT_RAW)
                            : irisSearch$createLiteralComponent(SEARCH_TEXT_RAW);
                }

                int width = Math.max(MIN_SIDE_BUTTON_WIDTH, irisSearch$getFontWidth(buttonText) + 8);
                irisSearch$backButtonWidth = width;
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
                            searchable.irisSearch$isSearchModeActive() ? CLEAR_TOOLTIP_TEXT : SEARCH_TOOLTIP_TEXT;
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
            IrisSearch.log(3, "Couldn't build the search header for one of the shader option screens.");
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
                irisSearch$debugLog("Screen object found: " + (screenObj != null ? screenObj.getClass().getName(): "null"));
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
            irisSearch$debugLog("Error occurred inside searchButtonClicked click routine: " + e);
        }
    }

    /**
     * Render tooltips for the search/clear button when search is not active.
     * Targets m_6311_ (SRG name for the 10-param render method on Forge 1.20.1).
     */
    @Dynamic
    @Inject(method = "m_6311_", at = @At("TAIL"), remap = false, require = 0)
    private void irisSearch$onRenderBareName(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                                             int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        try {
            MinecraftBridge.queueHeaderTooltip(guiGraphics, this.irisSearch$searchToggleButtonElement,
                    this.irisSearch$searchToggleTooltipText, x, y - 16);
            irisSearch$debugLog("Queued search button tooltip in bare render name TAIL inject.");
        } catch (Throwable t) {
            irisSearch$debugLog("Failed queueing search button tooltip (bare render name TAIL): " + t);
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
                ReflectionUtils.invokeMethod(guiGraphics, "m_285795_",
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
                irisSearch$debugLog("Queued search button tooltip during suppressed render.");
            } catch (Throwable t) {
                irisSearch$debugLog("Could not queue the search button tooltip during suppressed render: " + t);
            }

            return true;
        } catch (Throwable t) {
            IrisSearch.log(3, "Header row couldn't render correctly while searching.");
            irisSearch$debugLog("Reflective row suppression failed: " + t);
            return false;
        }
    }

    @Dynamic
    @Inject(method = "m_6311_", at = @At("HEAD"), remap = false, require = 0)
    private void irisSearch$captureRenderParams(@Coerce Object guiGraphics, int index, int y, int x,
                                                int entryWidth, int entryHeight, int mouseX, int mouseY,
                                                boolean hovered, float tickDelta, CallbackInfo ci) {
        this.irisSearch$capturedX = x;
        this.irisSearch$capturedY = y;
        this.irisSearch$capturedEntryWidth = entryWidth;
    }

    @Dynamic
    @Inject(method = "m_6311_", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void irisSearch$onSuppressBareName(@Coerce Object guiGraphics, int index, int y, int x, int entryWidth, int entryHeight,
                                               int mouseX, int mouseY, boolean hovered, float tickDelta, CallbackInfo ci) {
        if (irisSearch$suppressRow(guiGraphics, x, y, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta, false)) {
            ci.cancel();
        }
    }

    @Dynamic
    @Redirect(method = "m_6311_", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;m_280653_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), remap = false, require = 0)
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
            irisSearch$debugLog("Error in irisSearch$redirectDrawCenteredString redirection layer: " + e.getMessage());
        }

        // Vanilla fallback loop execution if utilities are absent or reflection properties error out
        try {
            Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
            Class<?> fontClass = Class.forName("net.minecraft.client.gui.Font");
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            guiGraphicsClass.getMethod("m_280653_", fontClass, componentClass, int.class, int.class, int.class).invoke(guiGraphics, font, text, x, y, color);
        } catch (Exception fallbackEx) {
            irisSearch$debugLog("Critical rendering fallback failed: " + fallbackEx.getMessage());
        }
    }

    @Unique
    private static void irisSearch$renderScrollingString(Object guiGraphics, Object font, Object text, int centerX, int minX, int minY, int maxX, int maxY, int color) {
        try {
            // Get text width: font.width(text)
            int textWidth = (int) font.getClass().getMethod("m_92852_", Class.forName("net.minecraft.network.chat.FormattedText")).invoke(font, text);
            int yPos = (minY + maxY - 9) / 2 + 1;
            int availableWidth = maxX - minX;

            Class<?> guiGraphicsClass = Class.forName("net.minecraft.client.gui.GuiGraphics");
            Class<?> fontClass = Class.forName("net.minecraft.client.gui.Font");
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");

            if (textWidth > availableWidth) {
                // Text is too wide, scroll it with smooth sine wave animation
                int scrollRange = textWidth - availableWidth;

                // Get current time: Util.getMillis()
                Class<?> utilClass = Class.forName("net.minecraft.Util");
                long currentTimeMillis = (long) utilClass.getMethod("m_137550_").invoke(null);
                double currentTime = (double) currentTimeMillis / 1000.0;

                double scrollDuration = Math.max((double) scrollRange * 0.5, 3.0);
                double scrollProgress = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * currentTime / scrollDuration)) / 2.0 + 0.5;

                // Mth.lerp()
                Class<?> mthClass = Class.forName("net.minecraft.util.Mth");
                double scrollOffset = (double) mthClass.getMethod("m_14139_", double.class, double.class, double.class).invoke(null, scrollProgress, 0.0, (double) scrollRange);

                // guiGraphics.enableScissor()
                guiGraphicsClass.getMethod("m_280588_", int.class, int.class, int.class, int.class).invoke(guiGraphics, minX, minY, maxX, maxY);

                // guiGraphics.drawString(font, text, x, y, color)
                guiGraphicsClass.getMethod("m_280430_", fontClass, componentClass, int.class, int.class, int.class)
                        .invoke(guiGraphics, font, text, minX - (int) scrollOffset, yPos, color);

                // guiGraphics.disableScissor()
                guiGraphicsClass.getMethod("m_280618_").invoke(guiGraphics);
            } else {
                // Text fits, center it at the specified centerX position using drawCenteredString
                guiGraphicsClass.getMethod("m_280653_", fontClass, componentClass, int.class, int.class, int.class)
                        .invoke(guiGraphics, font, text, centerX, yPos, color);
            }
        } catch (Exception e) {
            irisSearch$debugLog("Error inside converted renderScrollingString: " + e.getMessage());
        }
    }
}
