package com.spaceagle17.irissearch.neoforge;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ReflectionUtils;
import com.spaceagle17.irissearch.logging.IrisSearchLogger;

import java.lang.reflect.Constructor;
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
        for (String name : new String[]{"font", "field_22793"}) {
            Object value = ReflectionUtils.getFieldValue(screen, name);
            if (value != null) {
                return value;
            }
        }
        return null;
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
        for (String name : new String[]{"font", "field_1772"}) {
            Object value = ReflectionUtils.getFieldValue(mc, name);
            if (value != null) {
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
                    "net.minecraft.network.chat.Component",
                    "net.minecraft.class_2561"
            );
            if (componentClass == null) {
                return null;
            }

            for (String methodName : new String[]{"literal", "method_43470"}) {
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
                    "net.minecraft.network.chat.Component",
                    "net.minecraft.class_2561"
            );
            if (componentClass == null) {
                return null;
            }

            for (String methodName : new String[]{"translatable", "method_43471"}) {
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

    public static int getFontWidth(Object font, Object component) {
        if (font == null || component == null) {
            return 50;
        }
        try {
            for (String methodName : new String[]{"width", "method_27525"}) {
                try {
                    Method m = font.getClass().getDeclaredMethod(methodName, component.getClass());
                    m.setAccessible(true);
                    Object result = m.invoke(font, component);
                    if (result instanceof Integer) {
                        return (Integer) result;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }

            for (String methodName : new String[]{"width", "method_27525"}) {
                Method m = findMethodByInterfaceParam(font.getClass(), methodName, component.getClass());
                if (m != null) {
                    Object result = m.invoke(font, component);
                    if (result instanceof Integer) {
                        return (Integer) result;
                    }
                }
            }
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't measure search button text width; using a fallback size.");
            debugLog("getFontWidth failed: " + t);
        }
        return 50;
    }

    public static Object instantiate(Class<?> clazz, Object... args) {
        outer:
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length != args.length) {
                continue;
            }
            for (int i = 0; i < paramTypes.length; i++) {
                if (!isAssignable(paramTypes[i], args[i])) {
                    continue outer;
                }
            }
            try {
                ctor.setAccessible(true);
                return ctor.newInstance(args);
            } catch (Throwable t) {
                IrisSearch.log(3, "Couldn't create a " + clazz.getSimpleName() + " instance." + t);
                debugLog("instantiate() found a matching constructor for "
                        + clazz.getName() + " but invocation failed: " + t);
            }
        }
        return null;
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

    public static boolean invokeWithInterfaceParam(Object target, String methodName, Object arg) {
        if (target == null || arg == null) {
            return false;
        }
        Method m = findMethodByInterfaceParam(target.getClass(), methodName, arg.getClass());
        if (m == null) {
            return false;
        }
        try {
            m.invoke(target, arg);
            return true;
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't call " + methodName + " on the search box." + t);
            debugLog("invokeWithInterfaceParam failed calling " + methodName + ": " + t);
            return false;
        }
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

    public static int invokeIntGetter(Object target, String officialName, String intermediaryName, int fallback) {
        if (target == null) {
            return fallback;
        }

        Object result = ReflectionUtils.invokeMethod(target, officialName, new Class<?>[]{});
        if (!(result instanceof Integer) && intermediaryName != null) {
            result = ReflectionUtils.invokeMethod(target, intermediaryName, new Class<?>[]{});
        }
        return result instanceof Integer ? (Integer) result : fallback;
    }

    public static double invokeDoubleGetter(Object target, String officialName, String intermediaryName, double fallback) {
        if (target == null) {
            return fallback;
        }

        Object result = ReflectionUtils.invokeMethod(target, officialName, new Class<?>[]{});
        if (!(result instanceof Number) && intermediaryName != null) {
            result = ReflectionUtils.invokeMethod(target, intermediaryName, new Class<?>[]{});
        }
        return result instanceof Number ? ((Number) result).doubleValue() : fallback;
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
            return net.minecraft.client.Minecraft.getInstance();
        } catch (Throwable t) {
            // Fall back to reflection if direct call fails
            try {
                Class<?> mcClass = null;
                for (String name : new String[]{"net.minecraft.client.Minecraft", "net.minecraft.class_310"}) {
                    try { mcClass = Class.forName(name); break; } catch (ClassNotFoundException ignored) {}
                }
                if (mcClass != null) {
                    for (String name : new String[]{"getInstance", "method_1551"}) {
                        try {
                            Method m = mcClass.getMethod(name);
                            m.setAccessible(true);
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

    public static boolean isControlDown() {
        try {
            Object mc = getMinecraftClientInstance();
            if (mc == null) {
                return false;
            }
            for (String name : new String[]{"hasControlDown", "method_74188"}) {
                try {
                    Method m = findMethodDeep(mc.getClass(), name);
                    if (m != null) {
                        Object result = m.invoke(mc);
                        if (result instanceof Boolean) {
                            return (Boolean) result;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            return false;
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't check whether Ctrl is held." + t);
            debugLog("isControlDown failed: " + t);
            return false;
        }
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

    /**
     * Queues a hover/focus-conditional tooltip via Iris's TOP_LAYER_RENDER_QUEUE, matching
     * how Iris's own HeaderEntry draws its button tooltips. Silently no-ops on any failure
     * since a missing tooltip is cosmetic, not worth crashing the row over.
     */
    public static void queueHeaderTooltip(Object guiGraphics, Object buttonElement, String tooltipKey, int x, int y) {
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

            Runnable task = () -> {
                try {
                    drawTextPanel.invoke(null, font, guiGraphics, textComponent, x, y);
                } catch (Throwable t) {
                    debugLog("Tooltip draw task failed: " + t);
                }
            };

            Method addMethod = renderQueue.getClass().getMethod("add", Object.class);
            addMethod.invoke(renderQueue, task);
        } catch (Throwable t) {
            IrisSearch.log(3, "Couldn't show the search button tooltip." + t);
            debugLog("queueHeaderTooltip failed: " + t);
        }
    }
}
