package com.spaceagle17.irissearch.fabric;

import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;

public class FabricModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path configDirectory;
    private static Boolean useYarnMappings = null; // null = not yet determined

    public FabricModLoaderSpecifics() {
        this.configDirectory = FabricLoader.getInstance().getConfigDir();
    }


    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.FABRIC;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
                System.err.println("[IrisSearch] Server detected. Disabling client-only features.");
                return true;
            }
        } catch (Throwable t) {
        }
        return false;
    }

    /**
     * Discovers which mapping type is being used (Yarn vs Reflection) and caches the result.
     * Tries Yarn first, then falls back to reflection.
     */
    private void discoverMappingBranch() {
        if (useYarnMappings != null) {
            return;
        }

        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                useYarnMappings = true;
                return;
            }
        } catch (Throwable t) {
        }

        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
            if (mcInstance != null) {
                useYarnMappings = false;
                return;
            }
        } catch (Throwable t) {
        }
    }
}
