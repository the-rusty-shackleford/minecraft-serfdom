/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/** A captive's idle hours (D-0003): it stands about within {@link #RANGE} blocks of where it stood
 * as they began, now and then taking a few steps. It does not wander off, follow, meet or chat. */
public final class Stay extends Behavior<Villager> {
    static final int RANGE = 3;
    private static final float SPEED = 0.4F;
    private BlockPos anchor;
    private int wait;

    public Stay() { super(ImmutableMap.of(), Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2); }

    @Override protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.getBrain().isActive(Serfdom.STAY.get());
    }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        if (anchor == null || anchor.distManhattan(villager.blockPosition()) > 4 * RANGE) anchor = villager.blockPosition();
        wait = 0;
    }

    @Override protected void tick(ServerLevel level, Villager villager, long gameTime) {
        if (--wait > 0) return;
        wait = 20;
        var brain = villager.getBrain();
        if (brain.getMemory(MemoryModuleType.WALK_TARGET).isPresent()) return;
        if (villager.blockPosition().distManhattan(anchor) > RANGE + 1) {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(anchor), SPEED, 1));
        } else if (villager.getRandom().nextInt(8) == 0) {
            var to = anchor.offset(villager.getRandom().nextInt(2 * RANGE + 1) - RANGE, 0, villager.getRandom().nextInt(2 * RANGE + 1) - RANGE);
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(to), SPEED, 0));
        }
    }
}
