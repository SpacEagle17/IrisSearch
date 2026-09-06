package com.spaceagle17.irissearch.forge;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;
import com.spaceagle17.irissearch.util.SearchHints;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for constructing/calling Minecraft client objects entirely through
 * reflection, with no compile-time dependency on any net.minecraft.* class.
 */
public class MinecraftBridge {

    private MinecraftBridge() {
    }

    private static void debugLog(String message) {
        IrisSearchLogger.debugLog("[MinecraftBridge] " + message);
    }

    public static Class<?> resolveClass(String... candidates) {
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return null;
    }

    public static Object getFontFromScreen(Object screen) {
        for (String name : new String[]{"font", "f_96547_", "f_96539_", "field_22793"}) {
            Object value = ReflectionUtils.getFieldValue(screen, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static void invokeAddRenderableWidget(Object screen, Object widget) {
        for (String name : new String[]{"m_142416_", "addRenderableWidget"}) {
            try {
                Method m = findMethodByInterfaceParam(screen.getClass(), name, widget.getClass());
                if (m != null) {
                    m.invoke(screen, widget);
                    return;
                }
            } catch (Throwable t) {
                debugLog("invokeAddRenderableWidget(" + name + ") failed: " + t);
            }
        }
        debugLog("invokeAddRenderableWidget: could not find method under any candidate name");
    }

    /**
     * Minecraft.getInstance().font -- distinct from getFontFromScreen, which reads the
     * Screen's own inherited font field instead of going through the client instance.
     */
    public static Object getMinecraftFont() {
        Object mc = getMinecraftClientInstance();
        if (mc == null) {
            return null;
        }
        for (String name : new String[]{"f_91062_", "field_1772"}) {
            Object value = ReflectionUtils.getFieldValue(mc, name);
            if (value != null) {
                debugLog("getMinecraftFont: found font field \"" + name + "\" on Minecraft instance.");
                return value;
            }
        }
        IrisSearch.log(3, "Couldn't find the game's font, so a tooltip couldn't be drawn.");
        debugLog("getMinecraftFont: no candidate field name resolved on the Minecraft instance.");
        return null;
    }

    public static Object createLiteralComponent(String text) {
        try {
            Class<?> componentClass = resolveClass(
                    "net.minecraft.src.C_4996_",
                    "net.minecraft.network.chat.Component"
            );
            if (componentClass == null) {
                return null;
            }

            for (String methodName : new String[]{"m_237113_", "method_43470"}) {
                try {
                    Method m = componentClass.getDeclaredMethod(methodName, String.class);
                    m.setAccessible(true);
                    Object result = m.invoke(null, text);
                    if (result != null) {
                        return result;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't create search-box label text." + t);
            debugLog("createLiteralComponent failed for \"" + text + "\": " + t);
        }
        return null;
    }

    public static Object createTranslatableComponent(String key) {
        try {
            Class<?> componentClass = resolveClass(
                    "net.minecraft.src.C_4996_",
                    "net.minecraft.network.chat.Component"
            );
            if (componentClass == null) {
                return null;
            }

            for (String methodName : new String[]{"m_237115_", "translatable", "method_43471"}) {
                try {
                    Method m = componentClass.getDeclaredMethod(methodName, String.class);
                    m.setAccessible(true);
                    Object result = m.invoke(null, key);
                    if (result != null) {
                        return result;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable t) {
            debugLog("createTranslatableComponent failed for \"" + key + "\": " + t);
        }
        return null;
    }

    public static Object appendComponent(Object base, Object appendage) {
        if (base == null || appendage == null) return base;
        for (String methodName : new String[]{"append", "method_10852", "m_7220_"}) {
            Method m = findMethodByInterfaceParam(base.getClass(), methodName, appendage.getClass());
            if (m != null) {
                try {
                    Object result = m.invoke(base, appendage);
                    return result != null ? result : base;
                } catch (Throwable ignored) {
                }
            }
        }
        debugLog("appendComponent: all candidates failed");
        return base;
    }

    private static boolean isAssignable(Class<?> paramType, Object arg) {
        if (arg == null) {
            return !paramType.isPrimitive();
        }
        if (paramType.isPrimitive()) {
            return boxedType(paramType).isInstance(arg);
        }
        return paramType.isInstance(arg);
    }

    private static Class<?> boxedType(Class<?> primitive) {
        if (primitive == int.class) return Integer.class;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        if (primitive == long.class) return Long.class;
        if (primitive == short.class) return Short.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        return primitive;
    }

    public static Method findMethodByInterfaceParam(Class<?> targetClass, String methodName, Class<?> argRuntimeClass) {
        List<Class<?>> candidateTypes = new ArrayList<>();
        collectHierarchy(argRuntimeClass, candidateTypes);

        for (Class<?> declaring = targetClass; declaring != null; declaring = declaring.getSuperclass()) {
            for (Class<?> paramType : candidateTypes) {
                try {
                    Method m = declaring.getDeclaredMethod(methodName, paramType);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private static void collectHierarchy(Class<?> clazz, List<Class<?>> out) {
        if (clazz == null || out.contains(clazz)) {
            return;
        }
        out.add(clazz);
        collectHierarchy(clazz.getSuperclass(), out);
        for (Class<?> iface : clazz.getInterfaces()) {
            collectHierarchy(iface, out);
        }
    }

    private static Method findMethodDeep(Class<?> start, String methodName, Class<?>... paramTypes) {
        List<Class<?>> hierarchy = new ArrayList<>();
        collectHierarchy(start, hierarchy);

        for (Class<?> candidate : hierarchy) {
            try {
                Method m = candidate.getDeclaredMethod(methodName, paramTypes);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static Object getMinecraftClientInstance() {

        try {
            // Try direct call first
            Minecraft instance = Minecraft.getInstance();
            debugLog("getMinecraftClientInstance: returning instance of " + instance.getClass());
            return instance;
        } catch (Throwable t) {
            // Fall back to reflection if direct call fails
            try {
                Class<?> mcClass = null;
                for (String name : new String[]{"net.minecraft.src.C_3391_", "net.minecraft.client.Minecraft"}) {
                    try { mcClass = Class.forName(name); break; } catch (ClassNotFoundException ignored) {}
                }
                if (mcClass != null) {
                    for (String name : new String[]{"m_91087_", "method_1551"}) {
                        try {
                            Method m = mcClass.getMethod(name);
                            m.setAccessible(true);
                            debugLog("getMinecraftClientInstance: found Minecraft class \"" + mcClass.getName() + "\" with method \"" + name + "\"");
                            return m.invoke(null);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Throwable t2) {
                IrisSearch.log(3, "Couldn't access the Minecraft client instance." + t2);
                debugLog("getMinecraftClientInstance failed: " + t2);
                return null;
            }
        }
        return null;
    }

    /** Whether Ctrl is currently held, via {@code Screen.hasControlDown()} ({@code m_96637_} on Forge/1.20.1). */
    public static boolean isControlDown() {
        try {
            Class<?> screenClass = resolveClass("net.minecraft.client.gui.screens.Screen", "net.minecraft.class_437");
            if (screenClass != null) {
                for (String name : new String[]{"m_96637_", "hasControlDown", "method_25441"}) {
                    try {
                        Method m = screenClass.getDeclaredMethod(name);
                        m.setAccessible(true);
                        Object result = m.invoke(null);
                        if (result instanceof Boolean b) return b;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            debugLog("isControlDown failed: " + t);
        }
        return false;
    }

    /** Whether Shift is currently held, via {@code Screen.hasShiftDown()} ({@code m_96638_} on Forge/1.20.1). */
    public static boolean isShiftDown() {
        try {
            Class<?> screenClass = resolveClass("net.minecraft.client.gui.screens.Screen", "net.minecraft.class_437");
            if (screenClass != null) {
                for (String name : new String[]{"m_96638_", "hasShiftDown", "method_25442"}) {
                    try {
                        Method m = screenClass.getDeclaredMethod(name);
                        m.setAccessible(true);
                        Object result = m.invoke(null);
                        if (result instanceof Boolean b) return b;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            debugLog("isShiftDown failed: " + t);
        }
        return false;
    }

    /**
     * Like Class.getMethod() but matches by assignability rather than exact type, so it
     * works when a parameter is declared as an interface but the argument is an implementing class.
     */
    private static Method findCompatibleStaticMethod(Class<?> owner, String name, Object... args) {
        outer:
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] paramTypes = m.getParameterTypes();
            for (int i = 0; i < paramTypes.length; i++) {
                if (!isAssignable(paramTypes[i], args[i])) {
                    continue outer;
                }
            }
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    private static final String CLEAR_TOOLTIP_KEY = "iris_search.tooltip.clear";

    /** Returns the animated tooltip while hovering the clear button or {@code null}. */
    private static Object rotatingSyntaxHintLine(String tooltipKey) {
        if (!CLEAR_TOOLTIP_KEY.equals(tooltipKey)) {
            return null;
        }
        String key = SearchHints.currentRotatingTipKey();
        if (key == null) {
            return null;
        }
        Object hint = createTranslatableComponent(key);
        Object countdown = createLiteralComponent(SearchHints.countdownSuffix());
        return (hint != null && countdown != null) ? appendComponent(hint, countdown) : hint;
    }

    /** Queues a hover/focus header button tooltip with optional rotating search syntax hints, failing silently on error. */
    public static void queueHeaderTooltip(Object guiGraphics, Object buttonElement, String tooltipKey,
                                          int x, int y, int sideButtonWidth, boolean allowSyntaxHint) {
        if (buttonElement == null || tooltipKey == null || guiGraphics == null) {
            return;
        }

        try {
            boolean hovered = Boolean.TRUE.equals(ReflectionUtils.invokeMethod(buttonElement, "isHovered", new Class<?>[]{}));
            boolean focused = Boolean.TRUE.equals(ReflectionUtils.invokeMethod(buttonElement, "isFocused", new Class<?>[]{}));
            if (!hovered && !focused) {
                return;
            }

            Object font = getMinecraftFont();
            Object textComponent = createTranslatableComponent(tooltipKey);
            if (font == null || textComponent == null) {
                debugLog("Skipping tooltip draw: font or text component unavailable.");
                return;
            }

            Class<?> screenClass = resolveClass("net.irisshaders.iris.gui.screen.ShaderPackScreen");
            Class<?> guiUtilClass = resolveClass("net.irisshaders.iris.gui.GuiUtil");
            if (screenClass == null || guiUtilClass == null) {
                debugLog("Skipping tooltip draw: could not resolve ShaderPackScreen or GuiUtil class.");
                return;
            }

            Object renderQueue = screenClass.getField("TOP_LAYER_RENDER_QUEUE").get(null);
            Method drawTextPanel = findCompatibleStaticMethod(guiUtilClass, "drawTextPanel", font, guiGraphics, textComponent, x, y);
            if (drawTextPanel == null) {
                debugLog("Skipping tooltip draw: could not find a matching GuiUtil#drawTextPanel overload.");
                return;
            }

            Object hintLine = allowSyntaxHint ? rotatingSyntaxHintLine(tooltipKey) : null;
            int hintX = x + sideButtonWidth + SearchHints.HINT_LEFT_GAP;
            int hintY = y + SearchHints.HINT_ROW_OFFSET_Y;

            Runnable task = () -> {
                try {
                    drawTextPanel.invoke(null, font, guiGraphics, textComponent, x, y);
                    if (hintLine != null) {
                        drawTextPanel.invoke(null, font, guiGraphics, hintLine, hintX, hintY);
                    }
                } catch (Throwable t) {
                    debugLog("Tooltip draw task failed: " + t);
                }
            };

            renderQueue.getClass().getMethod("add", Object.class).invoke(renderQueue, task);
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't show the search button tooltip." + t);
            debugLog("queueHeaderTooltip failed: " + t);
        }
    }
}
