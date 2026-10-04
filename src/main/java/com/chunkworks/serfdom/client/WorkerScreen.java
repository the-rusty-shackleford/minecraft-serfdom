/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.domain.Need;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Worker Screen (D-0001): a plain panel with the worker's profession and level, its status,
 * bed, job and need, and three buttons. Assign job is greyed until the worker has a bed, Clear job
 * until it has a job. Assign bed and Assign job close the screen so the player can click the bed
 * or the post. */
public final class WorkerScreen extends Screen {
    private static final int WIDTH = 236, PAD = 8, LINE = 11, BUTTON_W = 70, BUTTON_H = 20;
    private static final int TITLE = 0xFFD37F, LABEL = 0xA0A0A0, TEXT = 0xE0E0E0, NEED = 0xFF7F7F;
    private final Screens.WorkerView view;
    private int left, top, height;
    private Button bed, job, clear;

    public WorkerScreen(Screens.WorkerView view) {
        super(view.name());
        this.view = view;
    }

    /** effects: shows the view, replacing an open Worker Screen. */
    public static void accept(Screens.WorkerView view) { Minecraft.getInstance().setScreen(new WorkerScreen(view)); }

    public Screens.WorkerView view() { return view; }
    public Button bedButton() { return bed; }
    public Button jobButton() { return job; }
    public Button clearButton() { return clear; }

    @Override protected void init() {
        height = PAD + LINE + 4 + 5 * LINE + 6 + BUTTON_H + PAD;
        left = (width - WIDTH) / 2;
        top = (super.height - height) / 2;
        int y = top + height - PAD - BUTTON_H;
        int gap = (WIDTH - 2 * PAD - 3 * BUTTON_W) / 2;
        bed = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.assign_bed"), b -> press(Screens.WorkerButton.ASSIGN_BED, true))
                .bounds(left + PAD, y, BUTTON_W, BUTTON_H).build());
        job = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.assign_job"), b -> press(Screens.WorkerButton.ASSIGN_JOB, true))
                .bounds(left + PAD + BUTTON_W + gap, y, BUTTON_W, BUTTON_H).build());
        clear = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.clear_job"), b -> press(Screens.WorkerButton.CLEAR_JOB, false))
                .bounds(left + PAD + 2 * (BUTTON_W + gap), y, BUTTON_W, BUTTON_H).build());
        job.active = view.hasBed();
        clear.active = view.hasPost();
    }

    private void press(Screens.WorkerButton button, boolean close) {
        PacketDistributor.sendToServer(new Screens.WorkerAction(view.entity(), button));
        if (close) onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = top + PAD;
        g.drawString(font, view.name(), left + PAD, y, TITLE);
        y += LINE + 4;
        row(g, y, "screen.serfdom.profession", Component.translatable("screen.serfdom.level", view.profession(), view.level()), TEXT);
        row(g, y += LINE, "screen.serfdom.status", Component.translatable("screen.serfdom.hired"), TEXT);
        row(g, y += LINE, "screen.serfdom.bed", view.bed(), TEXT);
        row(g, y += LINE, "screen.serfdom.job", view.job(), TEXT);
        var need = Need.of(view.need());
        row(g, y += LINE, "screen.serfdom.need", need.<Component>map(n -> Component.translatable("need.serfdom." + n.name().toLowerCase()))
                .orElse(Component.translatable("screen.serfdom.need.none")), need.isPresent() ? NEED : TEXT);
    }

    private void row(GuiGraphics g, int y, String label, Component value, int colour) {
        var l = Component.translatable(label);
        g.drawString(font, l, left + PAD, y, LABEL);
        g.drawString(font, value, left + PAD + 64, y, colour);
    }

    /** effects: the dimmed world, then the panel, under the buttons. */
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + WIDTH, top + height, 0xC0101010);
        g.renderOutline(left, top, WIDTH, height, 0xFF505050);
    }

    @Override public boolean isPauseScreen() { return false; }
}
