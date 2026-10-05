/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Work Post's screen (D-0001, D-0002): the job, stepped through the jobs the server knows; the
 * radius, stepped within that job's bounds; the outline switch; for a cook or a blacksmith, the
 * way to its stock list; and the workers on the post. Each change is sent at once, so the outline
 * shows the new area while the screen is open. */
public final class PostScreen extends Screen {
    private static final int WIDTH = 236, PAD = 8, LINE = 11, ROW = 22, SMALL = 20;
    private static final int TITLE = 0xFFD37F, LABEL = 0xA0A0A0, TEXT = 0xE0E0E0;
    private final Screens.PostView view;
    private int job, radius;
    private boolean outline;
    private int left, top, height;
    private Button outlineButton;

    public PostScreen(Screens.PostView view) {
        super(Component.translatable("block.serfdom.work_post"));
        this.view = view;
        this.radius = view.radius();
        this.outline = view.outline();
        for (int i = 0; i < view.jobs().size(); i++) if (view.jobs().get(i).id().equals(view.job())) job = i;
    }

    /** effects: shows the view: refreshes the stock list or the picker already open on this post,
     * otherwise opens the post's screen afresh. */
    public static void accept(Screens.PostView view) {
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof StockScreen stock && stock.pos().equals(view.pos())) { stock.refresh(view); return; }
        if (mc.screen instanceof PickerScreen picker && picker.pos().equals(view.pos())) { picker.refresh(view); return; }
        mc.setScreen(new PostScreen(view));
    }

    private boolean workshop() { return !view.jobs().isEmpty() && view.jobs().get(job).workshop() && view.jobs().get(job).id().equals(view.job()); }

    public Screens.PostView view() { return view; }
    public int radius() { return radius; }
    public boolean outline() { return outline; }

    @Override protected void init() {
        int workers = Math.max(1, view.workers().size());
        height = PAD + LINE + 6 + (workshop() ? 4 : 3) * ROW + LINE + workers * LINE + PAD;
        left = (width - WIDTH) / 2;
        top = (super.height - height) / 2;
        int x = left + PAD + 64, y = top + PAD + LINE + 6;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> stepJob(-1)).bounds(x, y, SMALL, SMALL).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> stepJob(1)).bounds(left + WIDTH - PAD - SMALL, y, SMALL, SMALL).build());
        y += ROW;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> stepRadius(hasShiftDown() ? -4 : -1)).bounds(x, y, SMALL, SMALL).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> stepRadius(hasShiftDown() ? 4 : 1)).bounds(left + WIDTH - PAD - SMALL, y, SMALL, SMALL).build());
        y += ROW;
        outlineButton = addRenderableWidget(Button.builder(outlineText(), b -> { outline = !outline; b.setMessage(outlineText()); send(); })
                .bounds(x, y, WIDTH - 2 * PAD - 64, SMALL).build());
        active();
        if (workshop()) {
            y += ROW;
            addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.stock", view.rows().size()), b -> minecraft.setScreen(new StockScreen(view)))
                    .bounds(x, y, WIDTH - 2 * PAD - 64, SMALL).build());
        }
    }

    private Component outlineText() { return Component.translatable(outline ? "screen.serfdom.outline.on" : "screen.serfdom.outline.off"); }

    private void active() {
        boolean any = !view.jobs().isEmpty();
        for (var child : children()) if (child instanceof Button b && b != outlineButton) b.active = any;
    }

    private void stepJob(int by) {
        if (view.jobs().isEmpty()) return;
        job = Math.floorMod(job + by, view.jobs().size());
        radius = view.jobs().get(job).standard();
        send();
    }

    private void stepRadius(int by) {
        if (view.jobs().isEmpty()) return;
        var j = view.jobs().get(job);
        radius = Math.clamp(radius + by, j.min(), j.max());
        send();
    }

    private void send() {
        if (view.jobs().isEmpty()) return;
        PacketDistributor.sendToServer(new Screens.PostEdit(view.pos(), view.jobs().get(job).id(), radius, outline));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = top + PAD;
        g.drawString(font, title, left + PAD, y, TITLE);
        y += LINE + 6;
        int mid = left + PAD + 64 + (WIDTH - 2 * PAD - 64) / 2;
        g.drawString(font, Component.translatable("screen.serfdom.job"), left + PAD, y + 6, LABEL);
        var jobName = view.jobs().isEmpty() ? Component.translatable("screen.serfdom.no_jobs") : Screens.jobName(view.jobs().get(job).id());
        g.drawCenteredString(font, jobName, mid, y + 6, TEXT);
        y += ROW;
        g.drawString(font, Component.translatable("screen.serfdom.radius"), left + PAD, y + 6, LABEL);
        if (!view.jobs().isEmpty()) {
            var j = view.jobs().get(job);
            g.drawCenteredString(font, Component.translatable("screen.serfdom.radius_value", radius, j.min(), j.max()), mid, y + 6, TEXT);
        }
        y += ROW;
        g.drawString(font, Component.translatable("screen.serfdom.outline"), left + PAD, y + 6, LABEL);
        y += ROW;
        if (workshop()) { g.drawString(font, Component.translatable("screen.serfdom.stock.label"), left + PAD, y + 6, LABEL); y += ROW; }
        g.drawString(font, Component.translatable("screen.serfdom.workers", view.workers().size()), left + PAD, y, LABEL);
        y += LINE;
        if (view.workers().isEmpty()) g.drawString(font, Component.translatable("screen.serfdom.none"), left + PAD + 8, y, TEXT);
        for (var w : view.workers()) { g.drawString(font, w, left + PAD + 8, y, TEXT); y += LINE; }
    }

    /** effects: the dimmed world, then the panel, under the buttons. */
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + height, 0xC0101010);
        g.renderOutline(left, top, WIDTH, height, 0xFF505050);
    }

    @Override public boolean isPauseScreen() { return false; }
}
