/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** A hired villager without a bed follows its owner on foot (D-0001): it sets off when the owner is
 * more than {@link #START} blocks away, stops within {@link #STOP}, and gives up past
 * {@link #LOST} or across dimensions, staying where it is until the owner comes back. The gap
 * between START and STOP keeps it from starting and stopping on every step. */
public final class Follow {
    public static final double START = 6;
    public static final double STOP = 3;
    public static final double LOST = 48;
    private Follow() {}

    public enum Step { STAY, WALK, LOST }

    /** requires: distance &ge; 0.
     * effects: LOST across dimensions or past {@link #LOST}; WALK past {@link #START}, or past
     * {@link #STOP} while already walking; STAY otherwise. */
    public static Step step(boolean sameDimension, double distance, boolean walking) {
        if (distance < 0) throw new IllegalArgumentException("distance < 0: " + distance);
        if (!sameDimension || distance > LOST) return Step.LOST;
        if (distance > START || (walking && distance > STOP)) return Step.WALK;
        return Step.STAY;
    }
}
