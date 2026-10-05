/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** A villager's purse (D-0006): a whole number of emeralds, never above the cap a change is made
 * under, and the last day it was seen in a morning, which the morning deposit is paid against once.
 * The cap is a setting, so each change applies it: a purse kept under a higher cap keeps what it
 * holds until it pays, and takes nothing more until it is under the cap. Immutable.
 *
 * <p>Rep invariant: emeralds &ge; 0. */
public record Purse(int emeralds, long lastMorning) {
    /** The defaults: a cap of 64, a deposit of 2 to a purse under 4. */
    public static final int CAP = 64, DEPOSIT = 2, BELOW = 4;
    /** Never seen in a morning. */
    public static final long NEVER = Long.MIN_VALUE;
    /** A morning is from the turn of the day to the start of work. */
    public static final int MORNING_END = WorkDay.WORK_START;

    /** The settings a purse is kept by: its cap, the deposit, and the line a purse must be under to
     * get it. Immutable. RI: cap &ge; 1; deposit &ge; 0; below &ge; 0. */
    public record Rules(int cap, int deposit, int below) {
        public static final Rules DEFAULT = new Rules(CAP, DEPOSIT, BELOW);
        public Rules {
            if (cap < 1 || deposit < 0 || below < 0) throw new IllegalArgumentException("cap " + cap + ", deposit " + deposit + ", below " + below);
        }
    }

    public Purse {
        if (emeralds < 0) throw new IllegalArgumentException("emeralds " + emeralds);
    }

    /** effects: the purse a villager starts with (Rusty's call): one {@code found} in the world (it
     * was there before purses, or comes with a newly generated chunk) starts at the cap; one born,
     * cured or spawned starts at the deposit line, never above the cap. */
    public static Purse start(boolean found, Rules r) {
        Objects.requireNonNull(r);
        return new Purse(found ? r.cap() : Math.min(r.below(), r.cap()), NEVER);
    }

    /** effects: true iff the purse holds at least {@code n}. requires: n &ge; 0. */
    public boolean canPay(int n) {
        if (n < 0) throw new IllegalArgumentException("n " + n);
        return emeralds >= n;
    }

    /** requires: 0 &le; n &le; emeralds. effects: the purse after paying {@code n}. */
    public Purse pay(int n) {
        if (!canPay(n)) throw new IllegalArgumentException("pays " + n + " of " + emeralds);
        return new Purse(emeralds - n, lastMorning);
    }

    /** requires: n &ge; 0. effects: the purse after taking in {@code n}: what goes past the cap is
     * lost, and a purse already over it takes nothing. */
    public Purse takeIn(int n, Rules r) {
        if (n < 0) throw new IllegalArgumentException("n " + n);
        return new Purse(Math.min(emeralds + n, Math.max(emeralds, r.cap())), lastMorning);
    }

    /** effects: how many of {@code n} taken in would be lost to the cap. requires: n &ge; 0. */
    public int lost(int n, Rules r) { return emeralds + n - takeIn(n, r).emeralds; }

    /** effects: the day {@code dayTime} falls in, counted from the first. */
    public static long day(long dayTime) { return Math.floorDiv(dayTime, (long) WorkDay.DAY); }

    /** effects: true iff {@code dayTime} is in a morning: before the start of work. */
    public static boolean morning(long dayTime) { return WorkDay.tickOfDay(dayTime) < MORNING_END; }

    /** effects: true iff {@code dayTime} is in a morning this purse has not been seen in yet. */
    public boolean newMorning(long dayTime) { return morning(dayTime) && day(dayTime) != lastMorning; }

    /** effects: the purse as the villager is seen at {@code dayTime}: in a morning it has not been
     * seen in, it is marked seen, and when {@code eligible} (a free or hired adult) and under the
     * line it gets the deposit (what passes the cap is lost). Any other time, unchanged: a morning
     * the villager was not seen in is never paid. */
    public Purse morning(long dayTime, boolean eligible, Rules r) {
        if (!newMorning(dayTime)) return this;
        var paid = eligible && emeralds < r.below() ? takeIn(r.deposit(), r) : this;
        return new Purse(paid.emeralds, day(dayTime));
    }
}
