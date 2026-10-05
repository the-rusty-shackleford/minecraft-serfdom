/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** Taking a free villager with the chain (D-0003): who can be taken, and the hold that takes it.
 * The player holds use with the chain on the villager for {@link #HOLD} ticks, within
 * {@link #REACH} blocks and looking at it; letting go, stepping off or looking away ends it. */
public final class Capture {
    /** The default hold, in ticks: two seconds. */
    public static final int HOLD = 40;
    /** The default reach, in blocks, from the player's feet to the villager's. */
    public static final double REACH = 2.0;
    private Capture() {}

    /** Why a villager can or cannot be taken, the first reason that applies, in this order. */
    public enum Verdict {
        /** A child. */
        BABY,
        /** Already somebody's: the chain cuffs it instead. */
        OWNED,
        /** Unemployed or a nitwit: only a villager with a trade is worth taking (the spec's §3). */
        NO_PROFESSION,
        /** In a bought village whose owner has not trusted this player. */
        NOT_YOURS,
        TAKE
    }

    /** effects: the verdict for a villager with these facts. {@code villageAllows} is false only in
     * a village someone bought that this player neither owns nor is trusted in. */
    public static Verdict verdict(boolean adult, boolean owned, boolean employed, boolean villageAllows) {
        if (!adult) return Verdict.BABY;
        if (owned) return Verdict.OWNED;
        if (!employed) return Verdict.NO_PROFESSION;
        if (!villageAllows) return Verdict.NOT_YOURS;
        return Verdict.TAKE;
    }

    /** Where a hold stands after a tick. */
    public enum Step {
        /** Still holding, short of the time. */
        HOLDING,
        /** Held for the whole time: the villager is taken. */
        DONE,
        /** The villager is gone (dead, unloaded) or no longer free. */
        GONE,
        /** The player let go of use, or put the chain away. */
        LET_GO,
        /** The villager is farther than the reach. */
        TOO_FAR,
        /** The player looked off the villager. */
        LOOKED_AWAY
    }

    /** requires: held &ge; 0, hold &ge; 1, distance &ge; 0, reach &gt; 0.
     * effects: the hold after {@code held} ticks of it: the first failure that applies, in the order
     * GONE, LET_GO, TOO_FAR, LOOKED_AWAY; else DONE at or past {@code hold} ticks, else HOLDING. */
    public static Step tick(int held, int hold, boolean present, boolean using, double distance, double reach, boolean looking) {
        if (held < 0 || hold < 1 || distance < 0 || reach <= 0)
            throw new IllegalArgumentException("held " + held + ", hold " + hold + ", distance " + distance + ", reach " + reach);
        if (!present) return Step.GONE;
        if (!using) return Step.LET_GO;
        if (distance > reach) return Step.TOO_FAR;
        if (!looking) return Step.LOOKED_AWAY;
        return held >= hold ? Step.DONE : Step.HOLDING;
    }
}
