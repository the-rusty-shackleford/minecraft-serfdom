/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/** The chain lead (D-0001, Rusty's idea; D-0003): the one way to take a villager somewhere far, and
 * the capture. On an owned villager it cuffs it and leads it, and the cuffs stay on until the owner
 * takes them off: a chain that snaps past ten blocks leaves the villager standing in them. Held on
 * a free villager it is the capture ({@link Captures}), which is why it can be held in use; used on
 * nothing, it does nothing. Vanilla's lead still fails on villagers. The gestures are
 * {@link com.chunkworks.serfdom.domain.Chain}'s, carried out in {@link Workers}. */
public final class ChainLead extends Item {
    /** Long enough that only the capture's own end, or the player letting go, stops a hold. */
    private static final int USE_TICKS = 72000;

    public ChainLead(Properties properties) { super(properties); }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return USE_TICKS; }

    /** effects: no animation while the capture lasts: a bow's drawing pulled the chain into the
     * middle of the view as a grey block (the booth's first photograph). The hold is told on the
     * action bar instead. */
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
}
