/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;

/** What a worker does next while it is free to choose (D-0001). Asked between actions, never while
 * one is under way. */
public final class Shift {
    /** The ticks before the shift ends at which a worker starts no more work and takes what it
     * carries to the chests: the walk back and the deposit fit inside them. */
    public static final int WIND_DOWN = 600;
    private Shift() {}

    public enum Step {
        /** Take what is carried to the chests. */
        DEPOSIT,
        /** Fetch the job's tool from the chests. */
        FETCH_TOOL,
        /** Find a target and work it. */
        WORK,
        /** Stand by the post until something changes; {@link Plan#need} says why. */
        WAIT,
        /** The shift is over and nothing is carried. */
        REST
    }

    /** What the worker knows at the moment of choosing.
     * @param shiftLeft ticks left in the shift (0 outside working hours)
     * @param carrying whether it carries anything
     * @param full whether its inventory has no free slot
     * @param needsTool whether its job takes a tool
     * @param holdsTool whether it holds one that serves the job
     * @param toolStored whether the post's chests hold one
     * @param room whether something it carries has somewhere to go */
    public record Facts(int shiftLeft, boolean carrying, boolean full, boolean needsTool, boolean holdsTool, boolean toolStored, boolean room) {}

    /** The choice, and the need it shows (only with WAIT). */
    public record Plan(Step step, Optional<Need> need) {
        static Plan of(Step step) { return new Plan(step, Optional.empty()); }
        static Plan waitFor(Need need) { return new Plan(Step.WAIT, Optional.of(need)); }
    }

    /** effects: the next step, the first rule that applies:
     * <ol>
     * <li>in the last {@link #WIND_DOWN} ticks (or after): deposit what is carried, waiting with
     *     "chest full" if none of it fits; with nothing carried, rest;</li>
     * <li>with no free slot: deposit, or wait with "chest full";</li>
     * <li>without the tool the job takes: fetch one, or wait with "no tool";</li>
     * <li>otherwise work.</li>
     * </ol> */
    public static Plan next(Facts f) {
        if (f.shiftLeft() <= WIND_DOWN) {
            if (!f.carrying()) return Plan.of(Step.REST);
            return f.room() ? Plan.of(Step.DEPOSIT) : Plan.waitFor(Need.CHEST_FULL);
        }
        if (f.full()) return f.room() ? Plan.of(Step.DEPOSIT) : Plan.waitFor(Need.CHEST_FULL);
        if (f.needsTool() && !f.holdsTool()) return f.toolStored() ? Plan.of(Step.FETCH_TOOL) : Plan.waitFor(Need.NO_TOOL);
        return Plan.of(Step.WORK);
    }
}
