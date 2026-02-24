package com.rustysnail.world.preview.tfc.client.gui.screens;

import com.rustysnail.world.preview.tfc.WorldPreview;
import com.rustysnail.world.preview.tfc.backend.color.PreviewData;
import com.rustysnail.world.preview.tfc.backend.color.PreviewMappingData;
import com.rustysnail.world.preview.tfc.client.WorldPreviewComponents;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.BiomesTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.CacheTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.DimensionsTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.GeneralTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.HeightmapTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.SamplingTab;
import com.rustysnail.world.preview.tfc.client.gui.screens.settings.TFCTab;

import net.dries007.tfc.world.ChunkGeneratorExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.GridLayout.RowHelper;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

public class SettingsScreen extends Screen
{
    public static final ResourceLocation FOOTER_SEPERATOR = ResourceLocation.parse("textures/gui/footer_separator.png");
    private final Screen lastScreen;
    private final PreviewContainer previewContainer;
    private final TabManager tabManager;
    private TabNavigationBar tabNavigationBar;
    private GridLayout bottomButtons;
    @Nullable
    private final ChunkGeneratorExtension tfcExtension;

    private final boolean openTfcTab;
    private final boolean tfcReadOnly;


    public SettingsScreen(Screen lastScreen, PreviewContainer previewContainer, @Nullable ChunkGeneratorExtension tfcExtension)
    {
        this(lastScreen, previewContainer, tfcExtension, false, false);
    }

    public SettingsScreen(Screen lastScreen, PreviewContainer previewContainer, @Nullable ChunkGeneratorExtension tfcExtension, boolean openTfcTab, boolean tfcReadOnly)
    {
        super(WorldPreviewComponents.SETTINGS_TITLE);
        this.lastScreen = lastScreen;
        this.previewContainer = previewContainer;
        this.tfcExtension = tfcExtension;
        this.openTfcTab = openTfcTab;
        this.tfcReadOnly = tfcReadOnly;
        this.tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    }

    protected void init()
    {
        var minecraft = Objects.requireNonNull(this.minecraft);
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new GeneralTab(minecraft));
        tabs.add(new CacheTab(minecraft, this.previewContainer.dataProvider()));
        tabs.add(new SamplingTab(minecraft));
        tabs.add(new HeightmapTab(minecraft, this.previewContainer.previewData()));
        tabs.add(new DimensionsTab(minecraft, this.previewContainer.levelStemKeys()));
        tabs.add(new BiomesTab(minecraft, this.previewContainer));
        if (this.tfcExtension != null)
        {
            tabs.add(new TFCTab(minecraft, this.previewContainer, tfcExtension, this::onClose, this.tfcReadOnly));
        }


        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
            .addTabs(tabs.toArray(new Tab[0]))
            .build();
        int initialIndex = 0;
        if (this.openTfcTab && this.tfcExtension != null)
        {
            initialIndex = tabs.size() - 1;
        }
        this.tabNavigationBar.selectTab(initialIndex, false);
        this.addRenderableWidget(this.tabNavigationBar);
        this.bottomButtons = new GridLayout().columnSpacing(10);
        RowHelper rowHelper = this.bottomButtons.createRowHelper(1);
        rowHelper.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).build());
        this.bottomButtons.visitWidgets(abstractWidget -> {
            abstractWidget.setTabOrderGroup(1);
            this.addRenderableWidget(abstractWidget);
        });
        this.repositionElements();
    }

    public void repositionElements()
    {
        if (this.tabNavigationBar != null)
        {
            this.tabNavigationBar.setWidth(this.width);
            this.tabNavigationBar.arrangeElements();
            this.bottomButtons.arrangeElements();
            FrameLayout.centerInRectangle(this.bottomButtons, 0, this.height - 36, this.width, 36);
            int i = this.tabNavigationBar.getRectangle().bottom();
            ScreenRectangle screenRectangle = new ScreenRectangle(0, i, this.width, this.bottomButtons.getY() - i);
            this.tabManager.setTabArea(screenRectangle);
        }
    }

    public void render(GuiGraphics guiGraphics, int i, int j, float f)
    {
        guiGraphics.blit(FOOTER_SEPERATOR, 0, Mth.roundToward(this.height - 36 - 2, 2), 0.0F, 0.0F, this.width, 2, 32, 2);
        super.render(guiGraphics, i, j, f);
    }

    public void onClose()
    {
        Map<ResourceLocation, PreviewMappingData.ColorEntry> toWrite = this.previewContainer
            .allBiomes()
            .stream()
            .filter(x -> x.dataSource() == PreviewData.DataSource.CONFIG)
            .collect(
                Collectors.toMap(
                    x -> x.entry().key().location(), x -> new PreviewMappingData.ColorEntry(PreviewData.DataSource.CONFIG, x.color(), x.isCave(), x.name())
                )
            );
        WorldPreview.get().writeUserColorConfig(toWrite);
        this.previewContainer.patchColorData();
        this.previewContainer.resetTabs();
        //noinspection DataFlowIssue
        this.minecraft.setScreen(this.lastScreen);
    }
}
