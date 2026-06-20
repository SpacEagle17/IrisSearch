package com.spaceagle17.irissearch.fabric.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public class IrisSearchMixinPlugin implements IMixinConfigPlugin {

    private boolean alreadyLogged = false;
    public static final String IRIS_CLASS = "net.irisshaders.iris.Iris";
    public static final String MINECRAFT_CLIENT_CLASS = "net.minecraft.client.Minecraft";
    public static final String MINECRAFT_CLIENT_YARN_CLASS = "net.minecraft.class_310";

    private boolean checkClassExists(String className) {
        String resourceName = className.replace('.', '/') + ".class";
        return getClass().getClassLoader().getResource(resourceName) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!checkClassExists(IRIS_CLASS)) {
            if (!alreadyLogged) System.err.println("[IrisSearch]: Iris is not installed! Disabling mod...");
            alreadyLogged = true;
            return false;
        }
        if (mixinClassName.contains("ShaderPackScreenMixin") && !mixinClassName.contains("Yarn")) {
            return checkClassExists(MINECRAFT_CLIENT_CLASS);
        }
        if (mixinClassName.contains("YarnShaderPackScreenMixin")) {
            return checkClassExists(MINECRAFT_CLIENT_YARN_CLASS);
        }
        if (mixinClassName.contains("YarnShaderPackOptionListHeaderEntryMixin")) {
            return checkClassExists(MINECRAFT_CLIENT_YARN_CLASS);
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode classNode, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String target, ClassNode classNode, String mixinClassName, IMixinInfo mixinInfo) {}
}
