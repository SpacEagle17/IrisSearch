package com.spaceagle17.irissearch.fabric.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.gui.element.IrisElementRow", remap = false)
public abstract class IrisElementRowMixin {

    @Shadow private int width;

    @Unique
    public int irisSearch$getWidth() {
        return this.width;
    }
}
