/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.domain.Ledger;
import com.chunkworks.serfdom.domain.Purse;
import com.chunkworks.serfdom.market.ForSaleMenu;
import com.chunkworks.serfdom.market.Stalls;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** The For Sale block's screen (D-0006). Its Stall tab: the slot of the item sold (click an item on
 * it to sell that, an empty click to sell nothing), how many a sale and a sale's price with their
 * minus and plus (shift for eight), what the stall says it sells, its stock, its proceeds, and the
 * player's inventory. Its Ledger tab, wider, with the slots hidden: each of the last seven days'
 * sales, emeralds and the visits that came to nothing by why, and the latest visits, newest first.
 * The ledger comes from the server as the screen opens and as visits are written. */
public final class ForSaleScreen extends AbstractContainerScreen<ForSaleMenu> {
    private static final int TITLE = 0xFFD37F, LABEL = 0xA0A0A0, TEXT = 0xE0E0E0, GOOD = 0x80E080, BAD = 0xFF8080;
    private static final int SLOT_DARK = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF, SLOT_FILL = 0xFF8B8B8B;
    /** The ledger's width, wider than the stall's. */
    public static final int LEDGER_WIDTH = 360;
    @Nullable private static Stalls.LedgerView ledger;
    private boolean ledgerTab;
    private Button stallButton, ledgerButton;

    public ForSaleScreen(ForSaleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ForSaleMenu.WIDTH;
        imageHeight = ForSaleMenu.HEIGHT;
        inventoryLabelY = ForSaleMenu.INVENTORY_Y - 11;
    }

    /** effects: the ledger the server sent, shown on the open screen. */
    public static void accept(Stalls.LedgerView view) { ledger = view; }

    /** effects: the ledger the screen shows; nothing until the server has sent it. */
    @Nullable public static Stalls.LedgerView ledger() { return ledger; }

    public boolean ledgerShown() { return ledgerTab; }

    /** effects: shows the ledger, or the stall. */
    public void showLedger(boolean on) {
        ledgerTab = on;
        menu.ledgerShown = on;
        imageWidth = on ? LEDGER_WIDTH : ForSaleMenu.WIDTH;
        rebuildWidgets();
    }

    @Override protected void init() {
        super.init();
        int tabsX = leftPos + imageWidth - 8 - 2 * 44;
        stallButton = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.stall.tab"), b -> showLedger(false)).bounds(tabsX, topPos + 4, 42, 13).build());
        ledgerButton = addRenderableWidget(Button.builder(Component.translatable("screen.serfdom.ledger.tab"), b -> showLedger(true)).bounds(tabsX + 44, topPos + 4, 42, 13).build());
        stallButton.active = ledgerTab;
        ledgerButton.active = !ledgerTab;
        if (ledgerTab) return;
        addRenderableWidget(step("-", ForSaleMenu.QTY_DOWN, ForSaleMenu.QTY_DOWN_8, 76, 18));
        addRenderableWidget(step("+", ForSaleMenu.QTY_UP, ForSaleMenu.QTY_UP_8, 158, 18));
        addRenderableWidget(step("-", ForSaleMenu.PRICE_DOWN, ForSaleMenu.PRICE_DOWN_8, 76, 32));
        addRenderableWidget(step("+", ForSaleMenu.PRICE_UP, ForSaleMenu.PRICE_UP_8, 158, 32));
    }

    /** A minus or plus: by one, or by eight with shift held. */
    private Button step(String label, int one, int eight, int x, int y) {
        return Button.builder(Component.literal(label), b -> {
            int id = Screen.hasShiftDown() ? eight : one;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }).bounds(leftPos + x, topPos + y, 12, 12).build();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        g.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF505050);
        if (ledgerTab) return;
        for (var slot : menu.slots) frame(g, leftPos + slot.x - 1, topPos + slot.y - 1);
    }

    private static void frame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_FILL);
        g.fill(x, y, x + 17, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 17, SLOT_DARK);
        g.fill(x + 1, y + 17, x + 18, y + 18, SLOT_LIGHT);
        g.fill(x + 17, y + 1, x + 18, y + 18, SLOT_LIGHT);
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, TITLE);
        if (ledgerTab) { renderLedger(g); return; }
        g.drawString(font, Component.translatable("screen.serfdom.stall.per_sale"), 30, 20, LABEL);
        g.drawString(font, Component.translatable("screen.serfdom.stall.price"), 30, 34, LABEL);
        centred(g, Component.literal(Integer.toString(menu.quantity())), 123, 20, TEXT);
        centred(g, Component.translatable(menu.price() == 1 ? "screen.serfdom.stall.emerald" : "screen.serfdom.stall.emeralds", menu.price()), 123, 34, TEXT);
        var sells = menu.getSlot(ForSaleMenu.SELLS).getItem();
        int stock = 0;
        for (int i = ForSaleMenu.STOCK; i < ForSaleMenu.PROCEEDS; i++) {
            var s = menu.getSlot(i).getItem();
            if (!sells.isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(sells, s)) stock += s.getCount();
        }
        boolean open = !sells.isEmpty() && stock >= menu.quantity();
        g.drawString(font, Component.translatable("screen.serfdom.stall.stock"), 8, ForSaleMenu.STOCK_Y - 10, LABEL);
        var says = Stalls.label(sells, menu.quantity(), menu.price(), open);
        int w = font.width(says);
        g.drawString(font, says, ForSaleMenu.WIDTH - 8 - Math.min(w, 120), ForSaleMenu.STOCK_Y - 10, open ? GOOD : BAD, false);
        g.drawString(font, Component.translatable("screen.serfdom.stall.proceeds"), 8, ForSaleMenu.PROCEEDS_Y - 10, LABEL);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, LABEL);
    }

    private void centred(GuiGraphics g, Component text, int x, int y, int colour) { g.drawString(font, text, x - font.width(text) / 2, y, colour); }

    /** effects: the ledger: the days' totals, then the latest visits. */
    private void renderLedger(GuiGraphics g) {
        var view = ledger;
        if (view == null) { g.drawString(font, Component.translatable("screen.serfdom.ledger.none"), 8, 24, LABEL); return; }
        var l = view.ledger();
        int[] col = {8, 70, 110, 162, 222, 290};
        String[] head = {"day", "sold", "earned", "too_pricey", "cant_afford", "not_interested"};
        g.pose().pushPose();
        g.pose().scale(0.75F, 0.75F, 1F);
        for (int i = 0; i < col.length; i++) g.drawString(font, Component.translatable("screen.serfdom.ledger." + head[i]), (int) (col[i] / 0.75F), (int) (22 / 0.75F), LABEL);
        g.pose().popPose();
        long today = minecraft.level == null ? 0 : Purse.day(minecraft.level.getDayTime());
        int y = 32;
        var days = new ArrayList<>(l.days());
        java.util.Collections.reverse(days);
        if (days.isEmpty()) g.drawString(font, Component.translatable("screen.serfdom.ledger.empty"), 8, y, LABEL);
        for (var d : days) {
            var name = d.day() == today ? Component.translatable("screen.serfdom.ledger.today") : d.day() == today - 1 ? Component.translatable("screen.serfdom.ledger.yesterday")
                    : Component.translatable("screen.serfdom.ledger.day_n", d.day() + 1);
            g.drawString(font, name, col[0], y, TEXT);
            int[] v = {d.sold(), d.earned(), d.tooPricey(), d.cantAfford(), d.notInterested()};
            for (int i = 0; i < v.length; i++) g.drawString(font, Integer.toString(v[i]), col[i + 1], y, i < 2 ? GOOD : v[i] > 0 ? BAD : TEXT);
            y += 10;
        }
        y = Math.max(y + 6, 112);
        g.drawString(font, Component.translatable("screen.serfdom.ledger.latest"), 8, y, LABEL);
        y += 11;
        var lines = new ArrayList<>(l.lines());
        java.util.Collections.reverse(lines);
        for (var line : lines) {
            if (y > imageHeight - 12) break;
            g.drawString(font, line(line), 8, y, line.reaction().bought() ? GOOD : TEXT);
            y += 10;
        }
    }

    /** effects: a visit in words: "Day 12, 14:20 — Farmer bought 8 for 1 emerald". */
    static Component line(Ledger.Line l) {
        int t = (int) Math.floorMod(l.dayTime(), 24000L);
        int hour = (t / 1000 + 6) % 24, minute = (t % 1000) * 60 / 1000;
        var id = ResourceLocation.tryParse(l.profession());
        Component who = id == null ? Component.literal(l.profession())
                : Component.translatable("entity.minecraft.villager." + ("minecraft".equals(id.getNamespace()) ? "" : id.getNamespace() + ".") + id.getPath());
        var when = Component.translatable("screen.serfdom.ledger.when", Purse.day(l.dayTime()) + 1, String.format(Locale.ROOT, "%02d:%02d", hour, minute));
        var what = switch (l.reaction()) {
            case BARGAIN, BOUGHT -> Component.translatable(l.paid() == 1 ? "screen.serfdom.ledger.bought.one" : "screen.serfdom.ledger.bought", l.items(), l.paid());
            case TOO_PRICEY -> Component.translatable("screen.serfdom.ledger.passed.too_pricey");
            case CANT_AFFORD -> Component.translatable("screen.serfdom.ledger.passed.cant_afford");
            case NOT_INTERESTED -> Component.translatable("screen.serfdom.ledger.passed.not_interested");
        };
        return Component.translatable("screen.serfdom.ledger.line", when, who, what);
    }

    @Override public void removed() {
        super.removed();
        ledger = null;
    }

    @Override public boolean isPauseScreen() { return false; }

    /** effects: the ledger's days as the screen lists them, newest first. Public for the booth. */
    public static List<Ledger.Totals> daysShown() {
        var view = ledger;
        if (view == null) return List.of();
        var days = new ArrayList<>(view.ledger().days());
        java.util.Collections.reverse(days);
        return days;
    }
}
