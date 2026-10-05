/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Captives in Vanilla Wheels' trailer (D-0003, Vanilla Wheels' D-0029). A villager in chains rides
 * in the cargo (the chains are synced, so both sides agree); one the loader holds may board through
 * the open doors, with an empty hand or the chain in it; it boards still cuffed, with nobody
 * holding its chain, and steps out the same way. A villager out of chains is no cargo. Needs
 * Vanilla Wheels 1.11's cargo rules; with an older one, or none, nothing boards. */
public final class WheelsCompat {
    private WheelsCompat() {}

    /** effects: registers the villagers' cargo rule, when Vanilla Wheels has cargo rules. */
    public static void register() {
        if (ModList.get() == null || !ModList.get().isLoaded("vanillawheels")) return;
        try {
            Class.forName("com.chunkworks.vanillawheels.api.CargoRules", false, WheelsCompat.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return;
        }
        Inner.register();
    }

    private static final class Inner {
        static void register() {
            com.chunkworks.vanillawheels.api.CargoRules.register(new com.chunkworks.vanillawheels.api.CargoRules.Rule() {
                @Override public boolean rides(Entity entity) { return entity instanceof Villager v && Workers.cuffed(v); }
                @Override public boolean loads(Entity entity, Player player) {
                    return entity instanceof Villager v && v.getLeashHolder() == player && Workers.cuffed(v);
                }
                @Override public boolean leadsWith(ItemStack stack) { return stack.is(Serfdom.CHAIN_LEAD.get()); }
                @Override public void boarding(Entity entity, Player player) { ((Villager) entity).dropLeash(true, false); }
            });
        }
    }
}
