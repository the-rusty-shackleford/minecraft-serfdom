/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.domain.Stock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** A cook's or a blacksmith's stock list (D-0002): each row's item, how many are stocked or on
 * their way against how many it keeps, and in a second line whether it is stocked, being made or
 * stuck and why; buttons to keep fewer or more (ten at a time with shift) or drop the row; Add
 * opens the picker. Rows past what the screen's height holds scroll with the wheel. Every change
 * goes to the server, which answers with the list as it now stands. */
public final class StockScreen extends Screen {
    private static final int WIDTH = 300, PAD = 8, LINE = 11, ROW = 24, SMALL = 20, BUTTON = 16;
    private static final int TITLE = 0xFFD37F, TEXT = 0xE0E0E0, COUNT = 0xC0C0C0;
    private Screens.PostView view;
    private int left, top, panel, visible, scroll;

    public StockScreen(Screens.PostView view) {
        super(Component.translatable("screen.serfdom.stock.title", Screens.jobName(view.job())));
        this.view = view;
    }

    public BlockPos pos() { return view.pos(); }
    public Screens.PostView view() { return view; }

    /** effects: shows the list as the server now has it. */
    public void refresh(Screens.PostView view) {
        this.view = view;
        scroll = Math.max(0, Math.min(scroll, view.rows().size() - visible));
        rebuildWidgets();
    }

    @Override protected void init() {
        int room = super.height - 2 * PAD - (PAD + LINE + 6) - (6 + SMALL + PAD);
        visible = Math.clamp(room / ROW, 1, Stock.MAX_ROWS);
        int rows = Math.max(1, Math.min(visible, view.rows().size()));
        panel = PAD + LINE + 6 + rows * ROW + 6 + SMALL + PAD;
        left = (width - WIDTH) / 2;
        top = (super.height - panel) / 2;
        scroll = Math.max(0, Math.min(scroll, view.rows().size() - visible));
        int y = top + PAD + LINE + 6;
        for (int i = scroll; i < Math.min(view.rows().size(), scroll + visible); i++) {
            final int index = i;
            var row = view.rows().get(i);
            int bx = left + WIDTH - PAD - 3 * (BUTTON + 2);
            int by = y + (ROW - BUTTON) / 2 - 1;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> keep(index, row.keep() - (hasShiftDown() ? 10 : 1))).bounds(bx, by, BUTTON, BUTTON).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> keep(index, row.keep() + (hasShiftDown() ? 10 : 1))).bounds(bx + BUTTON + 2, by, BUTTON, BUTTON).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> send(index, row.item(), 0)).bounds(bx + 2 * (BUTTON + 2), by, BUTTON, BUTTON).build());
            y += ROW;
        }
        int fy = top + panel - PAD - SMALL;
        int half = (WIDTH - 2 * PAD - 4) / 2;
        var add = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.stock.add"), b -> minecraft.setScreen(new PickerScreen(this)))
                .bounds(left + PAD, fy, half, SMALL).build());
        add.active = view.rows().size() < Stock.MAX_ROWS;
        addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.back"), b -> minecraft.setScreen(new PostScreen(view)))
                .bounds(left + PAD + half + 4, fy, half, SMALL).build());
    }

    private void keep(int index, int keep) { send(index, view.rows().get(index).item(), Math.clamp(keep, 1, Stock.MAX_KEEP)); }

    void send(int index, net.minecraft.resources.ResourceLocation item, int keep) {
        PacketDistributor.sendToServer(new Screens.StockEdit(view.pos(), index, item, keep));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + PAD, top + PAD, TITLE);
        int y = top + PAD + LINE + 6;
        if (view.rows().isEmpty()) {
            g.drawWordWrap(font, Component.translatable("screen.serfdom.stock.empty"), left + PAD, y + 2, WIDTH - 2 * PAD, TEXT);
            return;
        }
        int textRight = left + WIDTH - PAD - 3 * (BUTTON + 2) - 4;
        for (int i = scroll; i < Math.min(view.rows().size(), scroll + visible); i++) {
            var row = view.rows().get(i);
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(row.item()));
            g.renderItem(stack, left + PAD, y + (ROW - 16) / 2);
            int tx = left + PAD + 20;
            var count = Component.translatable("screen.serfdom.stock.count", row.have(), row.keep());
            int cw = font.width(count);
            g.drawString(font, count, textRight - cw, y + 3, COUNT);
            g.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), textRight - cw - 6 - tx), tx, y + 3, TEXT);
            var detail = row.detail();
            if (font.width(detail) > textRight - tx) {
                g.drawString(font, font.substrByWidth(detail, textRight - tx - font.width("…")).getString() + "…", tx, y + 13, detail.getStyle().getColor() == null ? TEXT : detail.getStyle().getColor().getValue());
                if (mouseX >= tx && mouseX < textRight && mouseY >= y + 12 && mouseY < y + 22) g.renderTooltip(font, detail, mouseX, mouseY);
            } else g.drawString(font, detail, tx, y + 13, TEXT);
            y += ROW;
        }
        if (view.rows().size() > visible)
            g.drawCenteredString(font, Component.literal((scroll + 1) + "–" + Math.min(view.rows().size(), scroll + visible) + " / " + view.rows().size()), left + WIDTH / 2, top + panel - PAD - SMALL - 9, COUNT);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, view.rows().size() - visible);
        int next = Math.clamp(scroll - (int) Math.signum(dy), 0, max);
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }

    /** effects: the dimmed world, then the panel, under the buttons. */
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + panel, 0xC0101010);
        g.renderOutline(left, top, WIDTH, panel, 0xFF505050);
    }

    @Override public boolean isPauseScreen() { return false; }
}
