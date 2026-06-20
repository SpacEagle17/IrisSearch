package com.spaceagle17.irissearch.fabric;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.fabricmc.api.ModInitializer;

public class ClientIrisSearch implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricModLoaderSpecifics fabricSpecifics = new FabricModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(fabricSpecifics);

        if (ModLoaderSpecifics.serverCheckStatic()) return;

        new IrisSearch();
    }
}