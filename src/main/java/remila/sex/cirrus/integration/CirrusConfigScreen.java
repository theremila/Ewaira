package remila.sex.cirrus.integration;

import remila.sex.cirrus.CirrusClient;
import remila.sex.cirrus.ModIdentity;
import remila.sex.cirrus.config.CirrusSettings;
import remila.sex.cirrus.config.Optimization;
import java.io.IOException;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CirrusConfigScreen extends Screen {
    private final Screen parent;
    private final String version = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow()
            .getMetadata().getVersion().getFriendlyString();
    private CirrusSettings draft = CirrusClient.config().settings();
    private boolean saveFailed;
    private int page;

    public CirrusConfigScreen(Screen parent) {
        super(Component.translatable(ModIdentity.CONFIG_TRANSLATION_PREFIX + "title", ModIdentity.MOD_NAME));
        this.parent = parent;
    }

    @Override protected void init() {
        int column = Math.min(210, (width - 30) / 2);
        int left = width / 2 - column - 3;
        int footer = height - 24;
        int rowHeight = 20;
        int modulesTop = 48;
        var modules = Arrays.stream(Optimization.values()).filter(module -> module.supports(version)).toList();
        int rows = Math.max(1, (footer - modulesTop) / rowHeight);
        boolean paginate = rows * 2 < modules.size();
        if (paginate) rows = Math.max(1, (footer - modulesTop - 22) / rowHeight);
        int pageSize = 2 * rows;
        int pages = (modules.size() + pageSize - 1) / pageSize;
        page = Math.min(page, pages - 1);

        addRenderableWidget(new StringWidget(left, 4, column * 2 + 6, 12, title, font));
        addRenderableWidget(new StringWidget(left, 16, column * 2 + 6, 12,
                Component.translatable(ModIdentity.CONFIG_TRANSLATION_PREFIX + "hint"), font));
        addRenderableWidget(Checkbox.builder(Component.translatable(ModIdentity.CONFIG_TRANSLATION_PREFIX + "enabled"), font)
                .pos(left, 29).maxWidth(column).selected(draft.enabled())
                .onValueChange((box, selected) -> draft = draft.withEnabled(selected)).build());
        String debugKey = ModIdentity.CONFIG_TRANSLATION_PREFIX + "debug";
        addRenderableWidget(Checkbox.builder(Component.translatable(debugKey), font)
                .pos(left + column + 6, 29).maxWidth(column).selected(draft.debug())
                .onValueChange((box, selected) -> draft = draft.withDebug(selected))
                .tooltip(Tooltip.create(Component.translatable(debugKey + ".tooltip"))).build());
        int start = page * pageSize;
        int end = Math.min(start + pageSize, modules.size());
        for (int index = start; index < end; index++) {
            Optimization module = modules.get(index);
            int slot = index - start;
            String key = ModIdentity.CONFIG_TRANSLATION_PREFIX + module.key();
            addRenderableWidget(Checkbox.builder(Component.translatable(key), font)
                    .pos(left + (slot % 2) * (column + 6), modulesTop + (slot / 2) * rowHeight)
                    .maxWidth(column).selected(draft.modules().get(module))
                    .onValueChange((box, selected) -> draft = draft.withModule(module, selected))
                    .tooltip(Tooltip.create(Component.translatable(key + ".tooltip"))).build());
        }
        if (paginate) {
            var previous = addRenderableWidget(Button.builder(Component.translatable("gui.previous"),
                    button -> { page--; rebuildWidgets(); }).bounds(left, footer - 22, column, 20).build());
            previous.active = page > 0;
            var next = addRenderableWidget(Button.builder(Component.translatable("gui.next"),
                    button -> { page++; rebuildWidgets(); }).bounds(left + column + 6, footer - 22, column, 20).build());
            next.active = page + 1 < pages;
        }
        int third = (column * 2 - 6) / 3;
        addRenderableWidget(Button.builder(Component.translatable(ModIdentity.CONFIG_TRANSLATION_PREFIX + "defaults"), button -> {
            draft = CirrusSettings.defaults(); saveFailed = false; rebuildWidgets();
        }).bounds(left, footer, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + third + 6, footer, third, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(saveFailed ? ModIdentity.CONFIG_TRANSLATION_PREFIX + "save_failed" : ModIdentity.CONFIG_TRANSLATION_PREFIX + "apply"), button -> save())
                .bounds(left + (third + 6) * 2, footer, third, 20).build());
    }

    private void save() {
        CirrusSettings before = CirrusClient.config().settings();
        try { CirrusClient.config().save(draft); }
        catch (IOException failure) {
            ModIdentity.LOGGER.warn("Could not save settings", failure);
            saveFailed = true; rebuildWidgets(); return;
        }
        // Existing ungrouped meshes or occupancy masks need rebuilding when terrain/occlusion becomes active.
        if (((!before.active(Optimization.TERRAIN_CULLING) && draft.active(Optimization.TERRAIN_CULLING))
                || (!before.active(Optimization.TERRAIN_LOD) && draft.active(Optimization.TERRAIN_LOD))
                || (!before.active(Optimization.ENTITY_OCCLUSION) && draft.active(Optimization.ENTITY_OCCLUSION)))
                && minecraft.level != null) ConfigScreenAccess.rebuildTerrain(minecraft);
        onClose();
    }
    @Override public void onClose() { ConfigScreenAccess.close(minecraft, parent); }
}
