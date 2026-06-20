package com.spaceagle17.irissearch;

import java.nio.file.Path;

/**
 * Abstract class for mod loader-specific functionality.
 * Each mod loader (Fabric, Forge, NeoForge, etc.) should provide its own implementation.
 */
public abstract class ModLoaderSpecifics {
    // Instance name constants
    public static final String FABRIC = "Fabric";
    public static final String FORGE = "Forge";
    public static final String NEOFORGE = "NeoForge";

    private static ModLoaderSpecifics instance;
    private static boolean instanceLogged = false;

    /**
     * Set the mod loader-specific instance.
     * This should be called early in the mod initialization by the specific loader implementation.
     */
    public static void setInstance(ModLoaderSpecifics impl) {
        instance = impl;
        if (!instanceLogged && impl != null) {
            instanceLogged = true;
        }
    }

    /**
     * Get the current mod loader-specific instance.
     */
    public static ModLoaderSpecifics getInstance() {
        if (instance == null) {
            throw new IllegalStateException("[IrisSearch] ModLoaderSpecifics instance not set! This indicates a serious initialization error. - ModLoaderSpecifics getInstance() called before setInstance()");
        }
        return instance;
    }

    // Abstract methods that must be implemented by each mod loader

    /**
     * Get the name of this mod loader instance.
     * Should return one of the constant values: FABRIC, FORGE, NEOFORGE, etc.
     */
    public abstract String getInstanceName();

    /**
     * Get the path to the config directory.
     */
    public abstract Path getConfigDirectory();

    /**
     * Check if the mod is running on a server (and should be disabled).
     * @return true if running on server, false otherwise
     */
    public abstract boolean serverCheck();

    // Convenience static methods that delegate to the instance

    /**
     * Get the name of the current mod loader instance.
     */
    public static String getInstanceNameStatic() {
        return getInstance().getInstanceName();
    }

    /**
     * Check if the current instance matches the given name.
     * @param name Instance name to check (use constants like FABRIC, FORGE, etc.)
     * @return true if the current instance matches the given name
     */
    public static boolean isInstance(String name) {
        return getInstance().getInstanceName().equals(name);
    }

    /**
     * Get the path to the config directory.
     */
    public static Path configDirectory() {
        return getInstance().getConfigDirectory();
    }

    /**
     * Check if the mod is running on a server (and should be disabled).
     * @return true if running on server, false otherwise
     */
    public static boolean serverCheckStatic() {
        return getInstance().serverCheck();
    }
}
