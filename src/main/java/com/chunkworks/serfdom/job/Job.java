/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** What code knows how to do for one target kind of {@link com.chunkworks.serfdom.domain.JobScript}:
 * find a target in a post's area and work it. */
public interface Job {
    /** effects: the next target to work in {@code post}'s area, nearest the worker first, leaving
     * out those {@code skip} rejects (targets it could not reach lately); no task when there is
     * none, with what the worker lacks when that is why. */
    Search find(ServerLevel level, Villager worker, WorkPostBlockEntity post, Predicate<BlockPos> skip);

    /** What looking for work found: a task, or none and the need that keeps it from one (empty
     * when there is simply nothing to do). */
    record Search(Optional<Task> task, Optional<com.chunkworks.serfdom.domain.Need> need) {
        public static Search none() { return new Search(Optional.empty(), Optional.empty()); }
        public static Search of(Optional<Task> task) { return new Search(task, Optional.empty()); }
    }

    /** One target being worked, a tick at a time. */
    interface Task {
        /** effects: where the worker stands to work the target in hand. */
        BlockPos stand();
        /** effects: how near (in blocks, along the axes) to {@link #stand} counts as there. */
        int reach();
        /** effects: the target's key for skipping it when it cannot be reached. */
        BlockPos key();
        /** effects: does one tick of work with the tool in the worker's hand at {@code speed}
         * (1, or 1.25 for a matching profession) and says how it went. */
        Step tick(ServerLevel level, Villager worker, double speed);
        /** effects: leaves the target as it is now, wiping any crack drawn on a block. */
        void abandon(ServerLevel level, Villager worker);
        /** effects: the place this task holds against every other worker while it lasts (D-0008):
         * a farm's plot, a tree's trunk base; empty for a task that holds none. */
        default Optional<Place> hold() { return Optional.empty(); }
    }

    /** A place of work one worker holds at a time (D-0008): a farm's plot, or the trunk base of a
     * tree being felled. */
    record Place(Kind kind, long at) {
        public enum Kind { PLOT, TREE }
        /** effects: the place that is {@code plot}. */
        public static Place plot(com.chunkworks.serfdom.domain.Farm.Plot plot) { return new Place(Kind.PLOT, ((long) plot.x() << 32) | (plot.z() & 0xFFFFFFFFL)); }
        /** effects: the place that is the tree standing on {@code base}. */
        public static Place tree(BlockPos base) { return new Place(Kind.TREE, base.asLong()); }
    }

    enum Step {
        /** Still at it. */
        WORKING,
        /** The next part is elsewhere: walk to {@link Task#stand} again. */
        MOVE,
        /** The next drops would not fit: empty the inventory, then come back. */
        FULL,
        /** Something else needs seeing to first (the tool broke): plan again, then come back. */
        PAUSE,
        /** Finished. */
        DONE
    }
}
