/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;

/** A worker's bed is the one it was given (D-0001), checked every two seconds in its core package:
 * the bed memory sleep walks to is put back whenever vanilla let it go (an unreachable night, a
 * restart), a bed that is gone becomes a need, and the tool goes out of the hand once the shift is
 * over. */
public final class KeepBed extends Behavior<Villager> {
    private static final int EVERY = 40;
    private long next;

    public KeepBed() { super(ImmutableMap.of()); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return level.getGameTime() >= next; }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        next = gameTime + EVERY;
        var worker = Workers.of(villager);
        if (!worker.owned()) return;
        if (!villager.getBrain().isActive(Serfdom.WORK.get())) Workers.stash(villager);
        var bed = worker.bed();
        if (bed.isPresent() && bed.get().dimension() == level.dimension() && level.isLoaded(bed.get().pos())) {
            if (level.getPoiManager().getType(bed.get().pos()).filter(t -> t.is(PoiTypes.HOME)).isEmpty()) {
                Workers.bedLost(level, villager);
                return;
            }
            var home = villager.getBrain().getMemory(MemoryModuleType.HOME);
            if (home.isEmpty() || !home.get().equals(bed.get())) villager.getBrain().setMemory(MemoryModuleType.HOME, bed.get());
        }
        Workers.showNeed(villager);
    }
}
