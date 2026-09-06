package com.spaceagle17.irissearch.fabric.mixin;

import com.spaceagle17.irissearch.fabric.ISearchableOptionList;
import net.irisshaders.iris.gui.NavigationController;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Deque;

/**
 * After a Ctrl+Click jump, the first back returns the search again with the same query as before
 */
@Mixin(value = NavigationController.class, remap = false)
public abstract class NavigationControllerMixin {

    @Shadow private ShaderPackOptionList optionList;
    @Shadow @Final private Deque<String> history;
    @Shadow private String currentScreen;

    @Inject(method = "back", at = @At("HEAD"), cancellable = true)
    private void irisSearch$backReturnsToSearch(CallbackInfo ci) {
        if (this.optionList instanceof ISearchableOptionList searchable && searchable.irisSearch$isSearchReturnArmed()) {
            this.history.clear();
            this.currentScreen = null; // results only render when no sub-screen is selected
            searchable.irisSearch$consumeSearchReturn();
            ci.cancel();
        }
    }

    // if the user goes into a submenu after a jump, the back button should return to the search results, but behave like normal navigation
    @Inject(method = "open", at = @At("HEAD"))
    private void irisSearch$forwardNavAbandonsReturn(String screen, CallbackInfo ci) {
        if (this.optionList instanceof ISearchableOptionList searchable) {
            searchable.irisSearch$clearSearchReturn();
        }
    }
}
