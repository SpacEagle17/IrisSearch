package com.spaceagle17.irissearch.neoforge;

import com.spaceagle17.irissearch.IrisSearch;
import com.spaceagle17.irissearch.ModLoaderSpecifics;
import net.neoforged.fml.common.Mod;

@Mod("iris_search")
public class ClientIrisSearch {

    public ClientIrisSearch() {
        NeoforgeModLoaderSpecifics neoforgeSpecifics = new NeoforgeModLoaderSpecifics();
        ModLoaderSpecifics.setInstance(neoforgeSpecifics);

        if(ModLoaderSpecifics.serverCheckStatic()) return;
        new IrisSearch();
    }
}
