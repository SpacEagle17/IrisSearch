package com.spaceagle17.irissearch.neoforge;

import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class NeoforgeModLoaderSpecifics extends ModLoaderSpecifics {

    private final Path configDirectory;

    public NeoforgeModLoaderSpecifics() {
        this.configDirectory = FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String getInstanceName() {
        return ModLoaderSpecifics.NEOFORGE;
    }

    @Override
    public Path getConfigDirectory() {
        return configDirectory;
    }

    @Override
    public boolean serverCheck() {
        try {
            // Try to use getDist() if available (NeoForge 1.21.10+)
            java.lang.reflect.Method getDistMethod = FMLEnvironment.class.getMethod("getDist");
            Object dist = getDistMethod.invoke(null);
            if (dist == Dist.DEDICATED_SERVER) {
                System.err.println("[IrisSearch] Server detected. Disabling client-only features.");
                return true;
            }
        } catch (NoSuchMethodException e) {
            // Fallback for older NeoForge versions
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                System.err.println("[IrisSearch] Server detected. Disabling client-only features.");
                return true;
            }
        } catch (Throwable t) {
            // Any other error, assume not a server
        }
        return false;
    }
}
