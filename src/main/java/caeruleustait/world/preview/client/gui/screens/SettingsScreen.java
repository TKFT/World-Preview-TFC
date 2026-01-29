package caeruleustait.world.preview.client.gui.screens;

import caeruleustait.world.preview.WorldPreview;
import caeruleustait.world.preview.backend.color.PreviewData;
import caeruleustait.world.preview.backend.color.PreviewMappingData;
import caeruleustait.world.preview.client.WorldPreviewComponents;
import caeruleustait.world.preview.client.gui.screens.settings.BiomesTab;
import caeruleustait.world.preview.client.gui.screens.settings.CacheTab;
import caeruleustait.world.preview.client.gui.screens.settings.DimensionsTab;
import caeruleustait.world.preview.client.gui.screens.settings.GeneralTab;
import caeruleustait.world.preview.client.gui.screens.settings.HeightmapTab;
import caeruleustait.world.preview.client.gui.screens.settings.SamplingTab;
import caeruleustait.world.preview.client.gui.screens.settings.TFCTab;
import net.dries007.tfc.world.ChunkGeneratorExtension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

public class SettingsScreen extends Screen {
    public static final ResourceLocation HEADER_SEPERATOR = ResourceLocation.parse("textures/gui/header_separator.png");
    public static final ResourceLocation FOOTER_SEPERATOR = ResourceLocation.parse("textures/gui/footer_separator.png");
    public static final ResourceLocation LIGHT_DIRT_BACKGROUND = ResourceLocation.parse("textures/gui/light_dirt_background.png");
    private final Screen lastScreen;
    private final PreviewContainer previewContainer;
    private final TabManager tabManager;
    private TabNavigationBar tabNavigationBar;
    private GridLayout bottomButtons;
    @Nullable
    private final ChunkGeneratorExtension tfcExtension;



    public SettingsScreen(Screen lastScreen, PreviewContainer previewContainer, @Nullable ChunkGeneratorExtension tfcExtension) {
        super(WorldPreviewComponents.SETTINGS_TITLE);
        this.lastScreen = lastScreen;
        this.previewContainer = previewContainer;
        this.tfcExtension = tfcExtension;
        this.tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    }

    protected void init() {
        // Build list of tabs, conditionally adding TFC tab if enabled
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new GeneralTab(this.minecraft));
        tabs.add(new CacheTab(this.minecraft, this.previewContainer.dataProvider()));
        tabs.add(new SamplingTab(this.minecraft));
        tabs.add(new HeightmapTab(this.minecraft, this.previewContainer.previewData()));
        tabs.add(new DimensionsTab(this.minecraft, this.previewContainer.levelStemKeys()));
        tabs.add(new BiomesTab(this.minecraft, this.previewContainer));
        if (this.tfcExtension != null) {
            tabs.add(new TFCTab(this.minecraft, this.previewContainer, tfcExtension, this::onClose));
        }



        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width)
                .addTabs(tabs.toArray(new Tab[0]))
                .build();
        this.tabNavigationBar.selectTab(0, false);
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

    public void repositionElements() {
        if (this.tabNavigationBar != null) {
            this.tabNavigationBar.setWidth(this.width);
            this.tabNavigationBar.arrangeElements();
            this.bottomButtons.arrangeElements();
            FrameLayout.centerInRectangle(this.bottomButtons, 0, this.height - 36, this.width, 36);
            int i = this.tabNavigationBar.getRectangle().bottom();
            ScreenRectangle screenRectangle = new ScreenRectangle(0, i, this.width, this.bottomButtons.getY() - i);
            this.tabManager.setTabArea(screenRectangle);
        }
    }

    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        guiGraphics.blit(FOOTER_SEPERATOR, 0, Mth.roundToward(this.height - 36 - 2, 2), 0.0F, 0.0F, this.width, 2, 32, 2);
        super.render(guiGraphics, i, j, f);
    }

    public void onClose() {
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
        this.minecraft.setScreen(this.lastScreen);
    }
}
