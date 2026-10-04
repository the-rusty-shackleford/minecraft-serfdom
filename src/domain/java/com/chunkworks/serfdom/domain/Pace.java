/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** How long a worker's action takes (D-0001): the time a player standing on the ground takes to
 * break the block with the same tool, never less than a floor that keeps work visible, divided by
 * the worker's speed (1.25 for a matching profession). */
public final class Pace {
    /** The speed of a worker whose profession matches its job. */
    public static final double BONUS = 1.25;
    private Pace() {}

    /** effects: the destroy speed of a tool whose base speed is {@code speed} with Efficiency at
     * {@code efficiency}: vanilla adds level² + 1 to a tool that is faster than a hand. */
    public static float withEfficiency(float speed, int efficiency) {
        return efficiency > 0 && speed > 1.0F ? speed + efficiency * efficiency + 1 : speed;
    }

    /** requires: hardness &ge; 0, toolSpeed &gt; 0, floor &ge; 1, speed &gt; 0.
     * effects: the ticks the action takes, at least 1: the player's break time (hardness &times;
     * 30 &divide; toolSpeed when the block drops for this tool, vanilla's
     * {@code hasCorrectToolForDrops}, &times; 100 when it does not), raised to {@code floor},
     * divided by {@code speed} and rounded up. */
    public static int ticks(float hardness, float toolSpeed, boolean drops, int floor, double speed) {
        if (hardness < 0 || toolSpeed <= 0 || floor < 1 || speed <= 0)
            throw new IllegalArgumentException("hardness " + hardness + ", toolSpeed " + toolSpeed + ", floor " + floor + ", speed " + speed);
        double breakTicks = Math.ceil(hardness * (drops ? 30.0 : 100.0) / toolSpeed);
        return Math.max(1, (int) Math.ceil(Math.max(breakTicks, floor) / speed));
    }
}
