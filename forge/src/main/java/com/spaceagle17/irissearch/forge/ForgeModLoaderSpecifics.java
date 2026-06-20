package com.spaceagle17.irissearch.forge;

import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;


public class ForgeModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path configDirectory;
    // 0 = unknown, 1 = obfuscated, 2 = modern, 3 = pre-1.16.5
    private static int mappingBranch = 0;

    public ForgeModLoaderSpecifics() {
        this.configDirectory = FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.FORGE;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                System.err.println("[IrisSearch] Server detected. Disabling client-only features.");
                return true;
            }
        } catch (Throwable t) {
            // Any error, assume not a server
        }
        return false;
    }

    /**
     * Discovers which mapping branch is being used and caches the result.
     * Tries obfuscated, then modern, then pre-1.16.5.
     */
    private void discoverMappingBranch() {
        if (mappingBranch != 0) {
            return; // Already discovered
        }

        // Try obfuscated first
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("m_91087_").invoke(null);
            mappingBranch = 1;
            return;
        } catch (Throwable t) {
        }

        // Try modern
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("getInstance").invoke(null);
            mappingBranch = 2;
            return;
        } catch (Throwable t) {
        }

        // Try pre-1.16.5
        try {
            Class<?> minecraftClass = Minecraft.class;
            minecraftClass.getMethod("func_71410_x").invoke(null);
            mappingBranch = 3;
            return;
        } catch (Throwable t) {
        }
    }
}
