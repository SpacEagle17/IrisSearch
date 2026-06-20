package com.spaceagle17.irissearch.forge;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.minecraftforge.fml.common.Mod;

@Mod("iris_search")
public class ClientIrisSearch {

    public ClientIrisSearch() {
        ForgeModLoaderSpecifics forgeSpecifics = new ForgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(forgeSpecifics);

        if (ModLoaderSpecifics.serverCheckStatic()) return;
        new IrisSearch();
    }
}
