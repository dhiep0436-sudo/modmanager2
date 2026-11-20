package com.example.modmanager;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Tabs: Mods / Resource Packs / Shaders (search + install from Modrinth) and Installed Mods (enable/disable).
 * Resource packs are applied immediately. Mods and shaders need a restart / shader menu respectively.
 */
public class ManagerScreen extends Screen {
    private static final int PAGE_SIZE = 7;
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 66;

    private record Row(String title, String subtitle, String buttonText, String iconKey, Runnable action) {}

    private final Screen parent;
    private final Path gameDir = FabricLoader.getInstance().getGameDir();

    // 0 = mods, 1 = resource packs, 2 = shaders, 3 = installed mods
    private int tab = 0;
    private int page = 0;
    private String query = "";
    private String status = "Search Modrinth for " + Modrinth.MC_VERSION + " content.";
    private List<Row> rows = new ArrayList<>();
    private boolean hasNextPage = false;
    private EditBox searchBox;

    public ManagerScreen(Screen parent) {
        super(Component.literal("Mod Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // Tab buttons
        String[] names = {"Mods", "Resource Packs", "Shaders", "Installed Mods"};
        int tabWidth = 100;
        int tabsLeft = centerX - (names.length * (tabWidth + 4)) / 2;
        for (int i = 0; i < names.length; i++) {
            final int t = i;
            Button b = Button.builder(Component.literal(names[i]), btn -> switchTab(t))
                    .bounds(tabsLeft + i * (tabWidth + 4), 8, tabWidth, 20)
                    .build();
            b.active = tab != i;
            this.addRenderableWidget(b);
        }

        // Search bar (Modrinth tabs only)
        if (tab < 3) {
            searchBox = new EditBox(this.font, centerX - 160, 36, 250, 20, Component.literal("Search"));
            searchBox.setMaxLength(60);
            searchBox.setValue(query);
            searchBox.setResponder(s -> query = s);
            this.addRenderableWidget(searchBox);
            this.addRenderableWidget(Button.builder(Component.literal("Search"), b -> {
                page = 0;
                runSearch();
            }).bounds(centerX + 96, 36, 64, 20).build());
        }

        // Row buttons
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(row.buttonText()), b -> row.action().run())
                    .bounds(centerX + 96, LIST_TOP + i * ROW_HEIGHT, 64, 20)
                    .build());
        }

        // Paging + close
        int bottom = this.height - 28;
        Button prev = Button.builder(Component.literal("< Prev"), b -> changePage(-1))
                .bounds(centerX - 160, bottom, 60, 20).build();
        prev.active = page > 0;
        this.addRenderableWidget(prev);

        Button next = Button.builder(Component.literal("Next >"), b -> changePage(1))
                .bounds(centerX - 94, bottom, 60, 20).build();
        next.active = hasNextPage;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
                .bounds(centerX + 100, bottom, 60, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        int left = this.width / 2 - 160;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = LIST_TOP + i * ROW_HEIGHT;
            Identifier icon = row.iconKey() == null ? null : IconCache.get(row.iconKey());
            if (icon != null) {
                g.blit(RenderPipelines.GUI_TEXTURED, icon, left, y, 0f, 0f,
                        IconCache.SIZE, IconCache.SIZE, IconCache.SIZE, IconCache.SIZE);
            }
            int textX = left + 26;
            g.drawString(this.font, clip(row.title(), 34), textX, y, 0xFFFFFFFF);
            g.drawString(this.font, clip(row.subtitle(), 36), textX, y + 10, 0xFFAAAAAA);
        }
        g.drawString(this.font, clip(status, 80), left, this.height - 44, 0xFFFFFF55);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    // ---- actions -------------------------------------------------------------------------

    private void switchTab(int newTab) {
        tab = newTab;
        page = 0;
        rows = new ArrayList<>();
        hasNextPage = false;
        if (tab == 3) {
            status = "Disabled mods are renamed to .jar.disabled. Restart the game to apply.";
            loadInstalled();
        } else {
            status = "Search Modrinth for " + Modrinth.MC_VERSION + " content.";
        }
        this.rebuildWidgets();
    }

    private void changePage(int delta) {
        page = Math.max(0, page + delta);
        if (tab == 3) loadInstalled();
        else runSearch();
    }

    private void runSearch() {
        if (tab > 2) return;
        Modrinth.Kind kind = Modrinth.Kind.values()[tab];
        String q = query;
        int p = page;
        status = "Searching...";
        CompletableFuture.runAsync(() -> {
            try {
                // Ask for one extra result to know whether a next page exists.
                List<Modrinth.Hit> hits = Modrinth.search(kind, q, p, PAGE_SIZE + 1);
                boolean more = hits.size() > PAGE_SIZE;
                List<Modrinth.Hit> shown = more ? hits.subList(0, PAGE_SIZE) : hits;
                List<Row> newRows = new ArrayList<>();
                for (Modrinth.Hit h : shown) {
                    IconCache.request(h.id(), h.iconUrl());
                    newRows.add(new Row(h.title(), h.description(), "Install", h.id(), () -> install(kind, h)));
                }
                Minecraft.getInstance().execute(() -> {
                    rows = newRows;
                    hasNextPage = more;
                    status = newRows.isEmpty() ? "No results." : "Found " + newRows.size() + " result(s) on this page.";
                    this.rebuildWidgets();
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status = "Search failed: " + e.getMessage());
            }
        });
    }

    private void install(Modrinth.Kind kind, Modrinth.Hit hit) {
        status = "Installing " + hit.title() + "...";
        CompletableFuture.runAsync(() -> {
            try {
                List<String> files = Modrinth.install(kind, hit.id(), gameDir);
                Minecraft.getInstance().execute(() -> afterInstall(kind, hit, files));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status = "Install failed: " + e.getMessage());
            }
        });
    }

    private void afterInstall(Modrinth.Kind kind, Modrinth.Hit hit, List<String> files) {
        switch (kind) {
            case MOD -> status = "Installed " + hit.title() + ". Restart Minecraft to load it.";
            case SHADER -> status = "Saved " + hit.title() + ". Pick it in Video Settings > Shader Packs (needs Iris).";
            case RESOURCEPACK -> {
                String file = files.get(files.size() - 1);
                status = applyResourcePack(file)
                        ? "Installed and enabled " + hit.title() + "."
                        : "Installed " + hit.title() + ". Enable it in Options > Resource Packs.";
            }
        }
    }

    /** Refreshes the pack list, selects the new pack and reloads resources, all without a restart. */
    private boolean applyResourcePack(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        String id = "file/" + fileName;
        if (!repo.getAvailableIds().contains(id)) return false;

        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        if (!selected.contains(id)) {
            selected.add(id);
            repo.setSelected(selected);
        }
        mc.options.updateResourcePacks(repo);
        return true;
    }

    private void loadInstalled() {
        try {
            List<ModFiles.Entry> all = ModFiles.list();
            int from = Math.min(page * PAGE_SIZE, all.size());
            int to = Math.min(from + PAGE_SIZE, all.size());
            hasNextPage = to < all.size();

            List<Row> newRows = new ArrayList<>();
            for (ModFiles.Entry e : all.subList(from, to)) {
                newRows.add(new Row(e.displayName(), e.enabled() ? "Enabled" : "Disabled (restart to apply)",
                        e.enabled() ? "Disable" : "Enable", null, () -> toggle(e)));
            }
            rows = newRows;
        } catch (Exception e) {
            status = "Could not read mods folder: " + e.getMessage();
        }
    }

    private void toggle(ModFiles.Entry entry) {
        try {
            ModFiles.toggle(entry);
            status = (entry.enabled() ? "Disabled " : "Enabled ") + entry.displayName() + ". Restart Minecraft to apply.";
            loadInstalled();
            this.rebuildWidgets();
        } catch (Exception e) {
            status = "Toggle failed: " + e.getMessage();
        }
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
