/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.SplittableRandom;

/** A captive's escape (the spec's §3, D-0003). Asleep in its bed at midnight, a captive rolls once
 * a night; on a success it gets up at some moment before two in the morning and walks toward the
 * village it was taken from, and inside that village it is free. A night it is not loaded for, or
 * not in bed for, is not rolled, and never made up.
 *
 * <p>Times are the level's day time in ticks: midnight is 18000 of each 24000-tick day. */
public final class Escape {
    /** Midnight: the first tick a night's roll may be made. */
    public static final int WINDOW_START = 18000;
    /** Two in the morning: a roll not made by now waits for the next night. */
    public static final int WINDOW_END = 20000;
    /** The default chance a night's roll succeeds. */
    public static final double CHANCE = 0.05;
    /** How near the place it was taken a captive with no known village must come to be home. */
    public static final int NEAR_TAKEN = 16;
    private Escape() {}

    /** effects: the day {@code dayTime} falls in: 0 for the first day, counting back before it. */
    public static long day(long dayTime) { return Math.floorDiv(dayTime, (long) WorkDay.DAY); }

    /** effects: true iff a roll is due at {@code dayTime}: inside the night's window, on a day after
     * {@code lastRolledDay}, with the captive asleep in its bed. */
    public static boolean due(long dayTime, long lastRolledDay, boolean asleepInBed) {
        int t = WorkDay.tickOfDay(dayTime);
        return asleepInBed && t >= WINDOW_START && t < WINDOW_END && day(dayTime) > lastRolledDay;
    }

    /** requires: 0 &le; chance &le; 1.
     * effects: the outcome of the roll for the captive with {@code seed} on {@code day}: the tick of
     * the day, in [WINDOW_START, WINDOW_END), at which it gets up, or empty when it stays. The same
     * seed and day give the same outcome; over many days a fraction {@code chance} succeed. */
    public static OptionalInt roll(long seed, long day, double chance) {
        if (!(chance >= 0 && chance <= 1)) throw new IllegalArgumentException("chance " + chance);
        var random = new SplittableRandom(seed * 0x9E3779B97F4A7C15L + day);
        if (random.nextDouble() >= chance) return OptionalInt.empty();
        return OptionalInt.of(WINDOW_START + random.nextInt(WINDOW_END - WINDOW_START));
    }

    /** effects: true iff a captive standing at {@code at} has come home: inside its village's
     * bounds, seen from above, when it knows its village; else within {@link #NEAR_TAKEN} blocks of
     * where it was taken, seen from above. */
    public static boolean home(Spot at, Optional<Area> village, Spot takenFrom) {
        if (village.isPresent()) return village.get().coversColumn(at);
        if (!at.dimension().equals(takenFrom.dimension())) return false;
        long dx = at.cell().x() - takenFrom.cell().x(), dz = at.cell().z() - takenFrom.cell().z();
        return dx * dx + dz * dz <= (long) NEAR_TAKEN * NEAR_TAKEN;
    }

    /** effects: where a captive walks home to: its village's middle when it knows its village, else
     * where it was taken. */
    public static Spot goal(Optional<Area> village, Spot takenFrom) { return village.map(Area::centre).orElse(takenFrom); }
}
