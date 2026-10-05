/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;
import java.util.OptionalLong;

/** When a worker eats (D-0005): breakfast (0 to 2000) and dinner (10000 to 12000), once each,
 * unless it is full; and at any other waking hour once it is hungry (below half), breaking off what
 * it is doing. Never asleep, in chains, on its way home, or without a bed (its home chest is the one
 * nearest its bed). After a meal, eaten or not, it waits {@link #RETRY} ticks before the next. */
public final class Meals {
    public static final int BREAKFAST_START = 0, BREAKFAST_END = WorkDay.WORK_START;
    public static final int DINNER_START = WorkDay.MEET_END, DINNER_END = WorkDay.SLEEP_START;
    /** Ticks between one meal and the next look for food. */
    public static final int RETRY = 600;
    /** No window eaten yet. */
    public static final long NEVER = Long.MIN_VALUE;
    private Meals() {}

    /** A worker's meal times: the last window it ate in, and when it may next look for food.
     * Immutable. */
    public record Times(long ateWindow, long retryAt) {
        public static final Times NONE = new Times(NEVER, NEVER);
    }

    /** The facts of one moment. */
    public record Facts(long dayTime, long gameTime, Hunger hunger, Times times, boolean awake, boolean cuffed, boolean escaping, boolean hasBed) {
        public Facts { Objects.requireNonNull(hunger); Objects.requireNonNull(times); }
    }

    /** effects: the meal window {@code dayTime} falls in, numbered from the first day (breakfast
     * 2d, dinner 2d + 1 on day d); empty between meals. */
    public static OptionalLong window(long dayTime) {
        long day = Math.floorDiv(dayTime, (long) WorkDay.DAY);
        int t = WorkDay.tickOfDay(dayTime);
        if (t >= BREAKFAST_START && t < BREAKFAST_END) return OptionalLong.of(2 * day);
        if (t >= DINNER_START && t < DINNER_END) return OptionalLong.of(2 * day + 1);
        return OptionalLong.empty();
    }

    /** effects: whether a meal is due now: the worker can eat (awake, not in chains, not on its way
     * home, with a bed), its wait since the last meal is over, and either it is in a window it has
     * not eaten in and is not full, or it is hungry. */
    public static boolean due(Facts f) {
        if (!f.awake() || f.cuffed() || f.escaping() || !f.hasBed()) return false;
        if (f.gameTime() < f.times().retryAt()) return false;
        if (f.hunger().hungry()) return true;
        var w = window(f.dayTime());
        return w.isPresent() && w.getAsLong() != f.times().ateWindow() && f.hunger().points() < Hunger.MAX;
    }

    /** effects: the meal times after a meal ending at {@code dayTime}, {@code gameTime}: the window
     * counted eaten when the worker ate in it, and the next look for food {@link #RETRY} ticks on. */
    public static Times after(Times before, long dayTime, long gameTime, boolean ate) {
        var w = window(dayTime);
        long eaten = ate && w.isPresent() ? w.getAsLong() : before.ateWindow();
        return new Times(eaten, gameTime + RETRY);
    }
}
