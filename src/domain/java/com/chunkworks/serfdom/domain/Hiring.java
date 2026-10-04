/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** Who can be hired, and for how much (D-0001). */
public final class Hiring {
    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;
    private Hiring() {}

    /** Why a villager can or cannot be hired, the first reason that applies, in this order. */
    public enum Verdict {
        /** A child: not hireable, and offered nothing. */
        BABY,
        /** Already somebody's worker. */
        OWNED,
        /** Unemployed or a nitwit: only a villager with a trade can be hired. */
        NO_PROFESSION,
        /** In a bought village whose owner has not trusted this player. */
        NOT_YOURS,
        HIRE
    }

    /** effects: the verdict for a villager with these facts. {@code villageAllows} is false only
     * in a village someone bought that this player neither owns nor is trusted in. */
    public static Verdict verdict(boolean adult, boolean owned, boolean employed, boolean villageAllows) {
        if (!adult) return Verdict.BABY;
        if (owned) return Verdict.OWNED;
        if (!employed) return Verdict.NO_PROFESSION;
        if (!villageAllows) return Verdict.NOT_YOURS;
        return Verdict.HIRE;
    }

    /** requires: perLevel &ge; 0.
     * effects: the fee in emeralds: {@code perLevel} for each profession level, the level held to
     * [1, 5] (vanilla's novice to master). */
    public static int fee(int level, int perLevel) {
        if (perLevel < 0) throw new IllegalArgumentException("perLevel < 0: " + perLevel);
        return perLevel * Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
    }
}
