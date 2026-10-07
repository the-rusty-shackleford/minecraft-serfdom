/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.defence.Armouries;
import com.chunkworks.serfdom.domain.Grip;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;

/** What a villager's hand grips (D-0011): a tool (anything tiered: an axe, a hoe, a pickaxe, a shovel,
 * a sword, a knife; a trident; a mace) or a weapon of the defence's kinds, the Ranged Weapons
 * protocol's guns among them. Anything else is shown on the folded arms, as vanilla shows it. */
public final class Grips {
    private Grips() {}

    /** effects: the hold for {@code stack}. */
    public static Grip.Hold of(ItemStack stack) {
        if (stack.isEmpty()) return Grip.Hold.NONE;
        var item = stack.getItem();
        return Grip.of(item instanceof TieredItem || item instanceof TridentItem || item instanceof MaceItem, Armouries.kind(stack));
    }

    /** effects: true iff the villager's arms are out of the fold, gripping what it holds: a grown
     * villager holding a tool or a weapon. */
    public static boolean out(Villager villager) { return !villager.isBaby() && of(villager.getMainHandItem()) != Grip.Hold.NONE; }
}
