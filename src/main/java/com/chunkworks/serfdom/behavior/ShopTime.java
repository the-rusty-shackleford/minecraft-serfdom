/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.market.Baskets;
import com.chunkworks.serfdom.market.Purses;
import com.chunkworks.serfdom.market.Shoppers;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** A villager's purse and its shopping, in its core package, free or a worker (D-0006): every
 * {@link #EVERY} ticks, the morning it is seen in (the deposit, the household's use), and, when nothing
 * more pressing holds it (a panic, a raid, a meal, a trade, sleep, chains), the shop: goods it still
 * carries to put away, or a trip in its social time when it needs something a stall near its bed
 * sells. The trip turns it back to its schedule when it is done. Only while it is loaded: nothing is
 * caught up. */
public final class ShopTime extends Behavior<Villager> {
    static final int EVERY = 100;
    private long next = -1;

    public ShopTime() { super(ImmutableMap.of()); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return level.getGameTime() >= next; }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        // Spread over the interval by id, so a village does not shop in one tick.
        next = gameTime + (next < 0 ? 1 + Math.floorMod(villager.getId(), EVERY) : EVERY);
        if (!Purses.on()) return;
        Purses.morning(level, villager);
        var brain = villager.getBrain();
        if (busy(villager)) return;
        if (Baskets.of(villager).isPresent() || Shoppers.plan(level, villager)) brain.setActiveActivityIfPossible(Serfdom.SHOP.get());
    }

    /** effects: true iff something more pressing than shopping holds the villager. */
    static boolean busy(Villager villager) {
        var brain = villager.getBrain();
        for (var a : new Activity[]{Activity.PANIC, Activity.RAID, Activity.PRE_RAID, Activity.HIDE}) if (brain.isActive(a)) return true;
        if (brain.isActive(Serfdom.SHOP.get()) || brain.isActive(Serfdom.MEAL.get()) || brain.isActive(Serfdom.HELD.get()) || brain.isActive(Serfdom.ESCAPE.get())) return true;
        return villager.isTrading() || villager.isSleeping() || villager.isPassenger() || Workers.of(villager).cuffed();
    }
}
