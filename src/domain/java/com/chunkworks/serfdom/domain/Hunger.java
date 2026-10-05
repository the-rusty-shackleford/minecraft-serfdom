/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** How fed a worker is (D-0005): points from 0 to {@link #MAX}, as a player's. It drains by the hour
 * awake and at half that asleep, meals fill it, and below {@link #HALF} the worker's actions slow,
 * down to a floor just above 0; at 0 it works no more. It never kills. Immutable.
 *
 * <p>Rep invariant: 0 &le; points &le; MAX. */
public record Hunger(double points) {
    public static final double MAX = 20.0, HALF = 10.0;
    /** A waking hour, in ticks. */
    public static final int HOUR = 1200;
    /** A new worker's: full. */
    public static final Hunger FULL = new Hunger(MAX);

    public Hunger {
        if (!(points >= 0 && points <= MAX)) throw new IllegalArgumentException("points in [0, 20]: " + points);
    }

    /** requires: ticks &ge; 0, perHour &ge; 0.
     * effects: this after {@code ticks} ticks: {@code perHour} points a waking hour, half that
     * asleep, never below 0. */
    public Hunger drain(int ticks, boolean asleep, double perHour) {
        if (ticks < 0 || !(perHour >= 0)) throw new IllegalArgumentException("ticks " + ticks + ", perHour " + perHour);
        return new Hunger(Math.max(0, points - ticks * perHour / HOUR * (asleep ? 0.5 : 1.0)));
    }

    /** requires: nutrition &ge; 0. effects: this after eating something that fills {@code nutrition},
     * never above MAX. */
    public Hunger eat(int nutrition) {
        if (nutrition < 0) throw new IllegalArgumentException("nutrition " + nutrition);
        return new Hunger(Math.min(MAX, points + nutrition));
    }

    /** effects: whether the worker would start on something that fills {@code nutrition}: it does
     * fill, the worker is not full, and it would not overshoot by more than half its worth. */
    public boolean wants(int nutrition) {
        return nutrition > 0 && points < MAX && points + nutrition - MAX <= nutrition / 2.0;
    }

    /** effects: below half: actions slow, the hungry icon shows, a meal is due at any waking hour. */
    public boolean hungry() { return points < HALF; }

    /** effects: at 0: the worker works no more. */
    public boolean starved() { return points <= 0; }

    /** requires: 0 &lt; floor &le; 1.
     * effects: how fast the worker's actions go: 1 from half up, falling straight to {@code floor}
     * just above 0, and 0 at 0. */
    public double workSpeed(double floor) {
        if (!(floor > 0 && floor <= 1)) throw new IllegalArgumentException("floor " + floor);
        if (points >= HALF) return 1.0;
        if (points <= 0) return 0.0;
        return floor + (1 - floor) * points / HALF;
    }

    /** effects: the points shown as half drumsticks, 0 to 20, rounded up so that a worker not yet
     * starved shows at least half of one. */
    public int halves() { return (int) Math.ceil(points); }
}
