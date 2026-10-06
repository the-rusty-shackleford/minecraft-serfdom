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
 * {@link OpenGates} opens it on the way; and that a Work Post is a fence to any path that does not
 * end at it (D-0008). Its pole is no full block, so vanilla takes it for open ground and plans
 * straight over it; a villager walking past one stood stuck against the pole. A path to the post
 * itself is planned as vanilla plans it, since a worker's arrival at its post leans on reaching
 * above the pole (D-0006).
 *
 * <p>It replaces the navigation the villager's constructor made, so it takes over what that
 * constructor set on it: doors, and floating. Without floating every villager planned its paths along
 * the bottom of any water, while its brain's {@code Swim} held it at the surface, and a villager in
 * water two deep never climbed out (D-0010). */
public final class WorkerNavigation extends GroundPathNavigation {
    public WorkerNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
        setCanFloat(true);
    }

    @Override protected net.minecraft.world.level.pathfinder.Path createPath(java.util.Set<net.minecraft.core.BlockPos> targets, int regionOffset, boolean offsetUpward,
                                                                           int accuracy, float followRange) {
        var gates = (GateEvaluator) nodeEvaluator;
        gates.ends = targets;
        try { return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange); }
        finally { gates.ends = java.util.Set.of(); }
    }

    @Override protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new GateEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    /** Vanilla's walking evaluator, with a closed fence gate a closed wooden door for a worker, and a
     * Work Post a fence unless the path ends at it. */
    static final class GateEvaluator extends WalkNodeEvaluator {
        private boolean worker;
        /** Where the path being planned may end (a solid target is lifted to the block above it). */
        private java.util.Set<net.minecraft.core.BlockPos> ends = java.util.Set.of();

        @Override public void prepare(PathNavigationRegion region, Mob mob) {
            worker = mob instanceof Villager v && Workers.owned(v);
            super.prepare(region, mob);
        }

        @Override public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            var state = context.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
            if (worker && state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) return PathType.DOOR_WOOD_CLOSED;
            if (state.getBlock() instanceof com.chunkworks.serfdom.post.WorkPostBlock && !endsAt(x, y, z)) return PathType.FENCE;
            return super.getPathType(context, x, y, z);
        }

        /** effects: true iff the path may end at the block (x, y, z) or on top of it. */
        private boolean endsAt(int x, int y, int z) {
            for (var e : ends) if (e.getX() == x && e.getZ() == z && (e.getY() == y || e.getY() == y + 1)) return true;
            return false;
        }
    }
}
