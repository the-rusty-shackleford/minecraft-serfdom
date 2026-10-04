/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.google.common.collect.ImmutableMap;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** A worker opens the fence gates on its path and closes them behind it (D-0001), as vanilla's
 * {@code InteractWithDoor} does doors: a gate at the node it is leaving or the next one is swung
 * open away from it; a gate it opened is shut once it is on neither of those nodes and no one
 * stands in the gateway (standing against it is fine, as for vanilla's doors). Runs in an owned
 * villager's core package. */
public final class OpenGates extends Behavior<Villager> {
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private final Set<BlockPos> opened = new HashSet<>();

    public OpenGates() { super(ImmutableMap.of(MemoryModuleType.PATH, MemoryStatus.REGISTERED)); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) { return true; }

    @Override protected void start(ServerLevel level, Villager villager, long gameTime) {
        var path = villager.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
        BlockPos from = null, to = null;
        if (path != null && !path.notStarted() && !path.isDone()) {
            from = path.getPreviousNode().asBlockPos();
            to = path.getNextNode().asBlockPos();
            open(level, villager, from);
            open(level, villager, to);
        }
        closeBehind(level, villager, from, to);
    }

    private void open(ServerLevel level, Villager villager, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN)) return;
        var facing = villager.getDirection();
        if (state.getValue(FenceGateBlock.FACING) == facing.getOpposite()) state = state.setValue(FenceGateBlock.FACING, facing);
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 10);
        level.playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
        level.gameEvent(villager, GameEvent.BLOCK_OPEN, pos);
        opened.add(pos.immutable());
        if (TRACE) LOG.info("Serfdom trace: {} opened the gate at {} ({} remembered)", villager.getId(), pos.toShortString(), opened.size());
    }

    private void closeBehind(ServerLevel level, Villager villager, BlockPos from, BlockPos to) {
        var it = opened.iterator();
        while (it.hasNext()) {
            var pos = it.next();
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof FenceGateBlock) || !state.getValue(FenceGateBlock.OPEN)) { it.remove(); continue; }
            if (pos.equals(from) || pos.equals(to)) continue;
            if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) continue;
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 10);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.1F + 0.9F);
            level.gameEvent(villager, GameEvent.BLOCK_CLOSE, pos);
            it.remove();
        }
    }
}
