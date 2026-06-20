package com.spaceagle17.irissearch;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtils {

    public static boolean checkClassExists(String className) {
        try {
            String resourceName = className.replace('.', '/') + ".class";
            return ReflectionUtils.class.getClassLoader().getResource(resourceName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static Object getFieldValue(Object target, String fieldName) {
        Class<?> clazz;
        Object instance = target;
        try {
            if (target instanceof String) {
                clazz = Class.forName((String) target);
                instance = null;
            } else if (target instanceof Class<?>) {
                clazz = (Class<?>) target;
                instance = null;
            } else {
                clazz = target.getClass();
            }

            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(instance);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * Finds and retrieves the value of a field inside an object by its class type name.
     */
    public static Object getFieldByType(Object target, String typeClassName) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().getName().equals(typeClassName)) {
                    try {
                        field.setAccessible(true);
                        return field.get(target);
                    } catch (Exception ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Safely executes static or instance methods across runtime class variants.
     */
    public static Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Class<?> clazz;
            Object instance = target;

            if (target instanceof String) {
                clazz = Class.forName((String) target);
                instance = null;
            } else if (target instanceof Class<?>) {
                clazz = (Class<?>) target;
                instance = null;
            } else {
                clazz = target.getClass();
            }

            Method method = null;
            int iterations = 0; // Failsafe breakout counter
            while (clazz != null && method == null && iterations < 50) {
                iterations++;
                try {
                    if (paramTypes != null) {
                        method = clazz.getDeclaredMethod(methodName, paramTypes);
                    } else {
                        for (Method m : clazz.getDeclaredMethods()) {
                            if (m.getName().equals(methodName) && m.getParameterCount() == (args == null ? 0 : args.length)) {
                                method = m;
                                break;
                            }
                        }
                    }
                } catch (NoSuchMethodException e) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (method != null) {
                method.setAccessible(true);
                return method.invoke(instance, args);
            }
        } catch (Exception ignored) {}
        return null;
    }
}