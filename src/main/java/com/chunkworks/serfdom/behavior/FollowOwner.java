/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Follow;
import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/** A hired villager without a bed follows its owner on foot ({@link Follow}), looked at twice a
 * second. On the chain lead the chain does the leading. */
public final class FollowOwner extends Behavior<Villager> {
    private static final float SPEED = 0.7F;
    private boolean walking;
    private int wait;

    public FollowOwner() { super(ImmutableMap.of(), Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2); }

    @Override protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.getBrain().isActive(Serfdom.FOLLOW.get());
    }

    @Override protected void tick(ServerLevel level, Villager villager, long gameTime) {
        if (--wait > 0) return;
        wait = 10;
        var owner = Workers.of(villager).owner().map(u -> level.getServer().getPlayerList().getPlayer(u)).orElse(null);
        if (villager.isLeashed() || owner == null || owner.isSpectator()) { halt(villager); return; }
        boolean same = owner.level() == level;
        var step = Follow.step(same, same ? villager.distanceTo(owner) : 0, walking);
        if (step == Follow.Step.WALK) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(owner, false), SPEED, (int) Follow.STOP));
            walking = true;
        } else {
            halt(villager);
        }
    }

    @Override protected void stop(ServerLevel level, Villager villager, long gameTime) { halt(villager); }

    private void halt(Villager villager) {
        if (walking) villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        walking = false;
    }
}
