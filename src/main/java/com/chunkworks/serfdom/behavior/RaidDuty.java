/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.defence.Defenders;
import com.chunkworks.serfdom.domain.Defence;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;

/** A worker's call to arms, in its core package (D-0007): every {@link #EVERY} ticks, when a raid is
 * on where it stands and it musters, its brain turns to the defence ({@link Defend}): to arm, to stand
 * by, to fight; after the raid, to put back what it took, once nothing more pressing (a meal, a shop,
 * sleep, a trade) holds it. One that has tried to arm and found nothing to fight with is left to
 * vanilla's raid, and hides. */
public final class RaidDuty extends Behavior<Villager> {
    static final int EVERY = 20;
    private long next = -1;

    public RaidDuty() { super(ImmutableMap.of()); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return level.getGameTime() >= next; }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        next = gameTime + (next < 0 ? 1 + Math.floorMod(villager.getId(), EVERY) : EVERY);
        var brain = villager.getBrain();
        if (!Defenders.on() || villager.isBaby() || brain.isActive(Serfdom.DEFEND.get()) || villager.isPassenger()) return;
        var step = Defence.next(Defenders.facts(level, villager, false));
        switch (step) {
            case ARM, STAND, FIGHT -> brain.setActiveActivityIfPossible(Serfdom.DEFEND.get());
            case PUT_BACK -> { if (!ShopTime.busy(villager)) brain.setActiveActivityIfPossible(Serfdom.DEFEND.get()); }
            case HIDE, NONE -> {}
        }
    }
}
