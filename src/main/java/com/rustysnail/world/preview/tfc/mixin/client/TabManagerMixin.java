package com.rustysnail.world.preview.tfc.mixin.client;

import com.rustysnail.world.preview.tfc.client.gui.screens.PreviewTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.SeedSearchTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TabManager.class)
public abstract class TabManagerMixin
{
    @Shadow
    @Nullable
    public abstract Tab getCurrentTab();

    @Inject(method = "setCurrentTab", at = @At("HEAD"))
    private void onTabChange(Tab tab, boolean bl, CallbackInfo ci)
    {
        if (tab != this.getCurrentTab())
        {
            // Handle PreviewTab lifecycle - use start()/stop() methods directly
            // to avoid circular class loading issues with PreviewContainer
            if (tab instanceof PreviewTab previewTab)
            {
                previewTab.start();
            }
            else if (this.getCurrentTab() instanceof PreviewTab previewTab)
            {
                previewTab.stop();
            }

            // Handle SeedSearchTab lifecycle - same pattern
            if (tab instanceof SeedSearchTab seedSearchTab)
            {
                seedSearchTab.start();
            }
            else if (this.getCurrentTab() instanceof SeedSearchTab seedSearchTab)
            {
                seedSearchTab.stop();
            }
        }
    }
}
