/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Appetite;
import com.chunkworks.serfdom.Serfdom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/** A worker's appetite, in its core package (D-0005): every {@link Appetite#EVERY} ticks its hunger
 * drains, and every second, when a meal is due and nothing more pressing holds it (a panic, a raid,
 * a trade), its brain turns to the meal, as vanilla's panic takes over a villager. The meal turns
 * it back to its schedule when it is done, so the idle hours (breeding, gifts) and the meeting stay
 * as they are. Hunger drains only in the worker's own tick, so it pauses where the world is not
 * loaded and is never caught up. */
public final class MealTime extends Behavior<Villager> {
    private static final int CHECK = 20;
    private long nextCheck, nextDrain;

    public MealTime() { super(ImmutableMap.of()); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return level.getGameTime() >= nextCheck; }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        nextCheck = gameTime + CHECK;
        if (gameTime >= nextDrain) {
            // The first pass after loading only sets the clock: nothing is caught up.
            if (nextDrain != 0) Appetite.drain(villager, Appetite.EVERY);
            nextDrain = gameTime + Appetite.EVERY;
        }
        var brain = villager.getBrain();
        if (brain.isActive(Serfdom.MEAL.get()) || brain.isActive(Activity.PANIC) || brain.isActive(Activity.RAID) || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.HIDE) || brain.isActive(Serfdom.DEFEND.get()) || villager.isTrading()) return;
        if (Appetite.due(level, villager)) brain.setActiveActivityIfPossible(Serfdom.MEAL.get());
    }
}
