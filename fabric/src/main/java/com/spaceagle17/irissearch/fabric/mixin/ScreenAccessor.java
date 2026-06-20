package com.spaceagle17.irissearch.fabric.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Targets Screen.class directly (not ShaderPackScreen) because @Accessor/@Shadow only see
 * members declared in the literal @Mixin target class, not inherited ones. Both font and
 * addRenderableWidget are declared on Screen itself, not on ShaderPackScreen.
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Accessor("font")
    Font irisSearch$getFont();

    @SuppressWarnings("UnusedReturnValue")
    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T irisSearch$invokeAddRenderableWidget(T widget);
}