/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.WorkerMenu;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.WorkerLayout;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Worker Screen (D-0001, D-0003, D-0004): the worker drawn beside its four wearing slots, its
 * profession and level, its status (hired, captive, in chains, escaping, a child), bed, job and
 * need, four buttons, and the player's inventory below, laid out as {@link WorkerLayout} says. The
 * menu opens first and the view follows it; until the view comes only the slots show. Hunger shows
 * as the HUD's ten drumsticks (D-0005), the purse as an emerald and a number (D-0006). Assign job is
 * greyed until the worker has a bed, and for a child; Clear job until it has a job. Assign bed and
 * Assign job close the screen so the player can click the bed or the post. Set free asks again
 * before it lets the worker go. */
public final class WorkerScreen extends AbstractContainerScreen<WorkerMenu> {
    private static final int TITLE = 0xFFD37F, LABEL = 0xA0A0A0, TEXT = 0xE0E0E0, NEED = 0xFF7F7F, CAPTIVE = 0xD8A060;
    private static final int SLOT_DARK = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF, SLOT_FILL = 0xFF8B8B8B;
    private static final net.minecraft.resources.ResourceLocation FOOD_EMPTY = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_empty"),
            FOOD_HALF = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_half"),
            FOOD_FULL = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/food_full");
    /** True while the portrait is drawn, so the worker's need icon is not drawn over it. */
    private static boolean portrait;
    @Nullable private Screens.WorkerView view;
    private Button bed, job, clear, free;
    private boolean sure;

    public WorkerScreen(WorkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WorkerLayout.WIDTH;
        imageHeight = WorkerLayout.HEIGHT;
        titleLabelX = WorkerLayout.PAD;
        titleLabelY = WorkerLayout.PAD;
        inventoryLabelX = WorkerLayout.INVENTORY_X - 1;
        inventoryLabelY = WorkerLayout.INVENTORY_LABEL_Y;
    }

    /** effects: shows the view on the open Worker Screen, binding its menu to the villager it names. */
    public static void accept(Screens.WorkerView view) {
        if (Minecraft.getInstance().screen instanceof WorkerScreen screen) screen.show(view);
    }

    /** effects: whether a worker's portrait is being drawn now. */
    public static boolean drawingPortrait() { return portrait; }

    private void show(Screens.WorkerView v) {
        view = v;
        if (minecraft != null && minecraft.level != null && minecraft.level.getEntity(v.entity()) instanceof Villager villager) menu.bind(villager);
        sure = false;
        rebuildWidgets();
    }

    @Nullable public Screens.WorkerView view() { return view; }
    public Button bedButton() { return bed; }
    public Button jobButton() { return job; }
    public Button clearButton() { return clear; }
    public Button freeButton() { return free; }

    @Override protected void init() {
        super.init();
        if (view == null) return;
        var f = WorkerLayout.free();
        free = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.set_free"), b -> {
                    if (!sure) {
                        sure = true;
                        b.setMessage(Component.translatable("screen.serfdom.set_free.sure"));
                        return;
                    }
                    press(Screens.WorkerButton.SET_FREE, true);
                }).bounds(leftPos + f.x(), topPos + f.y(), f.w(), f.h()).build());
        bed = addRenderableWidget(button(0, "screen.serfdom.assign_bed", Screens.WorkerButton.ASSIGN_BED, true));
        job = addRenderableWidget(button(1, "screen.serfdom.assign_job", Screens.WorkerButton.ASSIGN_JOB, true));
        clear = addRenderableWidget(button(2, "screen.serfdom.clear_job", Screens.WorkerButton.CLEAR_JOB, false));
        job.active = view.hasBed() && view.status() != Screens.Status.CHILD;
        clear.active = view.hasPost();
    }

    private Button button(int index, String label, Screens.WorkerButton which, boolean close) {
        var r = WorkerLayout.button(index);
        return Button.builder(Component.translatable(label), b -> press(which, close)).bounds(leftPos + r.x(), topPos + r.y(), r.w(), r.h()).build();
    }

    private void press(Screens.WorkerButton button, boolean close) {
        if (view == null) return;
        PacketDistributor.sendToServer(new Screens.WorkerAction(view.entity(), button));
        if (close) onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** effects: the panel, every slot's frame, the portrait's frame and the worker in it. */
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF505050);
        for (var slot : menu.slots) frame(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        var p = WorkerLayout.PORTRAIT;
        int x0 = leftPos + p.x(), y0 = topPos + p.y();
        g.fill(x0, y0, x0 + p.w(), y0 + p.h(), 0xFF000000);
        g.renderOutline(x0 - 1, y0 - 1, p.w() + 2, p.h() + 2, 0xFF505050);
        var villager = menu.villager();
        if (villager != null) {
            portrait = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0, x0 + p.w(), y0 + p.h(), 28, 0.0625F, mouseX, mouseY, villager);
            } finally {
                portrait = false;
            }
        }
    }

    private static void frame(GuiGraphics g, int x, int y) {
        int s = WorkerLayout.SLOT;
        g.fill(x, y, x + s, y + s, SLOT_FILL);
        g.fill(x, y, x + s - 1, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + s - 1, SLOT_DARK);
        g.fill(x + 1, y + s - 1, x + s, y + s, SLOT_LIGHT);
        g.fill(x + s - 1, y + 1, x + s, y + s, SLOT_LIGHT);
    }

    /** effects: the name, the details' rows and the inventory's label, in the panel's frame. */
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, TITLE);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL);
        if (view == null) return;
        int y = WorkerLayout.DETAIL_Y;
        row(g, y, "screen.serfdom.profession", Component.translatable("screen.serfdom.level", view.profession(), view.level()), TEXT);
        var status = view.status();
        row(g, y += WorkerLayout.LINE, "screen.serfdom.status", Component.translatable("screen.serfdom.status." + status.name().toLowerCase(java.util.Locale.ROOT)),
                status == Screens.Status.HIRED || status == Screens.Status.CHILD ? TEXT : CAPTIVE);
        row(g, y += WorkerLayout.LINE, "screen.serfdom.bed", view.bed(), TEXT);
        row(g, y += WorkerLayout.LINE, "screen.serfdom.job", view.job(), TEXT);
        y += WorkerLayout.LINE;
        if (view.hunger() < 0) row(g, y, "screen.serfdom.hunger", Component.translatable("screen.serfdom.hunger.none"), TEXT);
        else drumsticks(g, y, view.hunger());
        y += WorkerLayout.LINE;
        if (view.purse() < 0) row(g, y, "screen.serfdom.purse", Component.translatable("screen.serfdom.purse.none"), TEXT);
        else purse(g, y, view.purse());
        var need = Need.of(view.need());
        row(g, y += WorkerLayout.LINE, "screen.serfdom.need", need.<Component>map(n -> Component.translatable("need.serfdom." + n.name().toLowerCase()))
                .orElse(Component.translatable("screen.serfdom.need.none")), need.isPresent() ? NEED : TEXT);
    }

    /** effects: the hunger row: ten of the HUD's drumsticks for {@code halves} half points. */
    private void drumsticks(GuiGraphics g, int y, int halves) {
        g.drawString(font, Component.translatable("screen.serfdom.hunger"), WorkerLayout.DETAIL_X, y, LABEL);
        for (int i = 0; i < 10; i++) {
            int x = WorkerLayout.VALUE_X + i * 8;
            g.blitSprite(FOOD_EMPTY, x, y - 1, 9, 9);
            if (halves >= 2 * i + 2) g.blitSprite(FOOD_FULL, x, y - 1, 9, 9);
            else if (halves == 2 * i + 1) g.blitSprite(FOOD_HALF, x, y - 1, 9, 9);
        }
    }

    /** effects: the purse row: an emerald and how many the worker holds (D-0006). */
    private void purse(GuiGraphics g, int y, int emeralds) {
        g.drawString(font, Component.translatable("screen.serfdom.purse"), WorkerLayout.DETAIL_X, y, LABEL);
        g.pose().pushPose();
        g.pose().translate(WorkerLayout.VALUE_X, y - 2, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.renderItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), 0, 0);
        g.pose().popPose();
        g.drawString(font, Component.translatable("screen.serfdom.purse.value", emeralds), WorkerLayout.VALUE_X + 14, y, TEXT);
    }

    private void row(GuiGraphics g, int y, String label, Component value, int colour) {
        g.drawString(font, Component.translatable(label), WorkerLayout.DETAIL_X, y, LABEL);
        var lines = font.split(value, WorkerLayout.WIDTH - WorkerLayout.PAD - WorkerLayout.VALUE_X);
        if (!lines.isEmpty()) g.drawString(font, lines.get(0), WorkerLayout.VALUE_X, y, colour);
    }

    @Override public boolean isPauseScreen() { return false; }
}
