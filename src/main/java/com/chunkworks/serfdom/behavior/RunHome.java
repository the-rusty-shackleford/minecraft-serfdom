/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Worker;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Escape;
import com.google.common.collect.ImmutableMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

/** A captive on its way home (D-0003): it walks toward the village it was taken from, a stretch of
 * up to sixteen blocks at a time, day and night, and once it stands inside that village (or near
 * where it was taken, when it knows no village) it is free. Its owner, if online, is told. Anyone
 * may cuff it on the way, which ends the escape. */
public final class RunHome extends Behavior<Villager> {
    private static final float SPEED = 0.6F;
    private static final int EVERY = 20;
    private int wait;

    public RunHome() { super(ImmutableMap.of(), Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2); }

    @Override protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return villager.getBrain().isActive(Serfdom.ESCAPE.get());
    }

    @Override protected void tick(ServerLevel level, Villager villager, long gameTime) {
        if (--wait > 0) return;
        wait = EVERY;
        var worker = Workers.of(villager);
        if (!worker.escaping() || worker.takenSpot().isEmpty()) return;
        var here = Worker.spot(GlobalPos.of(level.dimension(), villager.blockPosition()));
        if (Escape.home(here, worker.home(), worker.takenSpot().get())) {
            var owner = worker.owner().map(u -> level.getServer().getPlayerList().getPlayer(u)).orElse(null);
            if (owner != null) owner.sendSystemMessage(Component.translatable("message.serfdom.escaped", Workers.name(villager)).withStyle(ChatFormatting.RED));
            Workers.free(level, villager, com.chunkworks.serfdom.domain.Parting.Way.ESCAPES);
            return;
        }
        var goal = Escape.goal(worker.home(), worker.takenSpot().get());
        if (!goal.dimension().equals(here.dimension())) return;
        var brain = villager.getBrain();
        if (brain.getMemory(MemoryModuleType.WALK_TARGET).isPresent()) return;
        var toward = Vec3.atBottomCenterOf(new BlockPos(goal.cell().x(), goal.cell().y(), goal.cell().z()));
        var step = DefaultRandomPos.getPosTowards(villager, 16, 7, toward, Math.PI / 2);
        if (step == null) step = LandRandomPos.getPosTowards(villager, 10, 7, toward);
        if (step != null) brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(BlockPos.containing(step)), SPEED, 1));
    }
}
