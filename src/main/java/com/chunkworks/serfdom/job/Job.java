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
     * out those {@code skip} rejects (targets it could not reach lately); empty when there is none. */
    Optional<Task> find(ServerLevel level, Villager worker, WorkPostBlockEntity post, Predicate<BlockPos> skip);

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
