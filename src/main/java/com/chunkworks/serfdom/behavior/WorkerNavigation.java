/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Workers;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Every villager's navigation (D-0001): vanilla's, except that a worker plans its way through a
 * closed fence gate as vanilla plans a villager's way through a closed wooden door, and
 * {@link OpenGates} opens it on the way. A free villager's paths are vanilla's. */
public final class WorkerNavigation extends GroundPathNavigation {
    public WorkerNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
    }

    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new GateEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    /** Vanilla's walking evaluator, with a closed fence gate a closed wooden door for a worker. */
    static final class GateEvaluator extends WalkNodeEvaluator {
        private boolean worker;

        @Override public void prepare(PathNavigationRegion region, Mob mob) {
            worker = mob instanceof Villager v && Workers.owned(v);
            super.prepare(region, mob);
        }

        @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (worker) {
                var state = context.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) return PathType.DOOR_WOOD_CLOSED;
            }
            return super.getPathType(context, x, y, z);
        }
    }
}
