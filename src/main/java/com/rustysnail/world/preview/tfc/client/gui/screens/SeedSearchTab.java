package com.rustysnail.world.preview.tfc.client.gui.screens;

import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class SeedSearchTab implements Tab, AutoCloseable
{

    private final SeedSearchContainer container;

    public SeedSearchTab(CreateWorldScreen screen, PreviewTab previewTab)
    {
        this.container = new SeedSearchContainer(screen, previewTab);
    }

    @NotNull
    @Override
    public Component getTabTitle()
    {
        return WorldPreviewComponents.SEARCH_TITLE;
    }

    @Override
    public void visitChildren(@NotNull Consumer<AbstractWidget> consumer)
    {
        this.container.widgets().forEach(consumer);
    }

    @Override
    public void doLayout(@NotNull ScreenRectangle screenRectangle)
    {
        this.container.doLayout(screenRectangle);
    }

    public void start()
    {
        this.container.start();
    }

    public void stop()
    {
        this.container.stop();
    }

    @Override
    public void close()
    {
        this.container.close();
    }
}
