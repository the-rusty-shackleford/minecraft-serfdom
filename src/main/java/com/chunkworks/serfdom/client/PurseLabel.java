/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.market.Purses;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** The villager's purse on the trade screen (D-0006): an emerald and how many it holds, just over the
 * panel's right corner, so a trade shown sold out for want of emeralds explains itself. The server
 * sends it as trading starts and after every trade; it is forgotten when the screen closes. A
 * wandering trader has none and shows none. */
public final class PurseLabel {
    @Nullable private static Purses.PurseView shown;
    private PurseLabel() {}

    public static void accept(Purses.PurseView view) { shown = view; }

    /** effects: the purse the trade screen shows; nothing when none was sent. */
    @Nullable public static Purses.PurseView shown() { return shown; }

    static void render(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen screen) || shown == null) return;
        var g = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("screen.serfdom.trade.purse", shown.emeralds());
        int right = screen.getGuiLeft() + screen.getXSize() - 2;
        int y = screen.getGuiTop() - 12;
        int x = right - font.width(text);
        g.drawString(font, text, x, y, 0xFFFFFF);
        g.pose().pushPose();
        g.pose().translate(x - 13, y - 3, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.renderItem(new ItemStack(Items.EMERALD), 0, 0);
        g.pose().popPose();
    }

    static void closing(ScreenEvent.Closing event) { if (event.getScreen() instanceof MerchantScreen) shown = null; }
}
