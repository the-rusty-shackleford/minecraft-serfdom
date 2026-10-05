/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.job.RecipeBook;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** What a cook or a blacksmith can be asked to keep (D-0002): everything its job's stations make,
 * read off the recipes the server sent this client, as a grid of icons under a search box (by name
 * or id). A click adds the item to the list, keeping one of a tool and sixteen of anything that
 * stacks, and goes back to the list. */
public final class PickerScreen extends Screen {
    private static final int PAD = 8, LINE = 11, CELL = 18, SMALL = 20, MAX_COLUMNS = 12;
    private static final int TITLE = 0xFFD37F, TEXT = 0xE0E0E0;
    private final StockScreen list;
    private final List<ItemStack> all;
    private List<ItemStack> shown;
    private EditBox search;
    private String query = "";
    private int left, top, panelWidth, panelHeight, columns, rows, scroll;

    public PickerScreen(StockScreen list) {
        super(Component.translatable("screen.serfdom.picker.title"));
        this.list = list;
        this.all = makeable(list.view());
        this.shown = all;
    }

    public BlockPos pos() { return list.pos(); }

    /** effects: the ids of the items shown now, in order. */
    public List<String> shownIds() { return shown.stream().map(s -> BuiltInRegistries.ITEM.getKey(s.getItem()).toString()).toList(); }

    /** effects: clicks the first item shown, as the mouse would; false when none is. */
    public boolean clickFirst() { return !shown.isEmpty() && mouseClicked(left + PAD + CELL / 2.0, gridTop() + CELL / 2.0, 0); }

    /** effects: a refreshed post goes to the list behind. */
    public void refresh(Screens.PostView view) { list.refresh(view); }

    /** effects: what the post's job can make and its list does not keep yet, by name. */
    private static List<ItemStack> makeable(Screens.PostView view) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return List.of();
        var job = view.jobs().stream().filter(j -> j.id().equals(view.job())).findFirst();
        if (job.isEmpty()) return List.of();
        var kinds = EnumSet.noneOf(Station.class);
        for (var n : job.get().stations()) Station.named(n).ifPresent(kinds::add);
        var kept = view.rows().stream().map(r -> r.item().toString()).toList();
        var out = new ArrayList<ItemStack>();
        for (var id : RecipeBook.rules(level.getRecipeManager(), level.registryAccess(), kinds).results()) {
            if (kept.contains(id)) continue;
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            if (!stack.isEmpty()) out.add(stack);
        }
        out.sort(Comparator.comparing(s -> s.getHoverName().getString().toLowerCase(Locale.ROOT)));
        return out;
    }

    @Override protected void init() {
        columns = Math.clamp((width - 2 * PAD - 20) / CELL, 4, MAX_COLUMNS);
        panelWidth = columns * CELL + 2 * PAD;
        int room = height - 2 * PAD - (PAD + LINE + 4 + SMALL + 6) - (6 + SMALL + PAD);
        rows = Math.max(2, room / CELL);
        panelHeight = PAD + LINE + 4 + SMALL + 6 + rows * CELL + 6 + SMALL + PAD;
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        search = addRenderableWidget(new EditBox(font, left + PAD, top + PAD + LINE + 4, panelWidth - 2 * PAD, SMALL, Component.translatable("screen.serfdom.picker.search")));
        search.setValue(query);
        search.setResponder(q -> { query = q; filter(); });
        setInitialFocus(search);
        addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.back"), b -> minecraft.setScreen(list))
                .bounds(left + PAD, top + panelHeight - PAD - SMALL, panelWidth - 2 * PAD, SMALL).build());
        filter();
    }

    private void filter() {
        var q = query.trim().toLowerCase(Locale.ROOT);
        shown = q.isEmpty() ? all : all.stream().filter(s -> s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                || BuiltInRegistries.ITEM.getKey(s.getItem()).toString().contains(q)).toList();
        scroll = 0;
    }

    private int gridTop() { return top + PAD + LINE + 4 + SMALL + 6; }

    /** effects: the index into {@code shown} of the cell under the mouse, or -1. */
    private int cellAt(double mx, double my) {
        int x = (int) Math.floor((mx - left - PAD) / CELL), y = (int) Math.floor((my - gridTop()) / CELL);
        if (mx < left + PAD || x < 0 || x >= columns || my < gridTop() || y < 0 || y >= rows) return -1;
        int i = (scroll + y) * columns + x;
        return i < shown.size() ? i : -1;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + PAD, top + PAD, TITLE);
        if (shown.isEmpty()) { g.drawString(font, Component.translatable("screen.serfdom.picker.none"), left + PAD, gridTop() + 4, TEXT); return; }
        int hover = cellAt(mouseX, mouseY);
        for (int r = 0; r < rows; r++) for (int c = 0; c < columns; c++) {
            int i = (scroll + r) * columns + c;
            if (i >= shown.size()) break;
            int x = left + PAD + c * CELL, y = gridTop() + r * CELL;
            if (i == hover) g.fill(x, y, x + CELL, y + CELL, 0x60FFFFFF);
            g.renderItem(shown.get(i), x + 1, y + 1);
        }
        if (hover >= 0) g.renderTooltip(font, shown.get(hover), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int i = cellAt(mx, my);
        if (i >= 0 && button == 0) {
            var stack = shown.get(i);
            int keep = stack.getMaxStackSize() == 1 ? 1 : Math.min(16, stack.getMaxStackSize());
            list.send(list.view().rows().size(), BuiltInRegistries.ITEM.getKey(stack.getItem()), keep);
            minecraft.setScreen(list);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int lines = (shown.size() + columns - 1) / columns;
        scroll = Math.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, lines - rows));
        return true;
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelWidth, top + panelHeight, 0xC0101010);
        g.renderOutline(left, top, panelWidth, panelHeight, 0xFF505050);
    }

    @Override public boolean isPauseScreen() { return false; }
}
