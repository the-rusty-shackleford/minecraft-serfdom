/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** What a worker takes from its chests when a raid comes (D-0007): the best ranged weapon it has ammo
 * for, and the best melee weapon as a backup. "Best" is the most damage a second. A launcher is never
 * taken (Rusty's call). Ties go to the lower id (the order the chests were read in).
 *
 * <p>Damage a second: melee, its damage times its attacks a second; a bow or a crossbow, a full-draw
 * arrow's damage over a draw or a charge; a gun, its damage over a whole magazine fired and reloaded. */
public final class Armoury {
    /** A bow's full draw, and a crossbow's charge, in ticks: a player's. */
    public static final int BOW_DRAW = 20, CROSSBOW_CHARGE = 25;
    /** An arrow's speed at a bow's full draw and from a crossbow: a player's. */
    public static final double BOW_SPEED = 3.0, CROSSBOW_SPEED = 3.15;
    /** An arrow's base damage. */
    public static final double ARROW_DAMAGE = 2.0;
    private Armoury() {}

    /** What kind of weapon. */
    public enum Kind {
        MELEE, BOW, CROSSBOW, GUN, LAUNCHER;
        public boolean ranged() { return this != MELEE; }
    }

    /** A weapon found in a chest: an id, its kind, its damage a second, and how many rounds or arrows it
     * could fire with what the chests hold for it (and, for a gun, what it has loaded). RI: dps finite
     * and &ge; 0; ammo &ge; 0. */
    public record Found(int id, Kind kind, double dps, int ammo) {
        public Found {
            Objects.requireNonNull(kind);
            if (!(dps >= 0) || Double.isInfinite(dps) || ammo < 0) throw new IllegalArgumentException("dps " + dps + ", ammo " + ammo);
        }
    }

    /** What it takes: a ranged weapon and a melee one, each by id, either or both perhaps none. */
    public record Choice(Optional<Integer> ranged, Optional<Integer> melee) {
        public static final Choice NOTHING = new Choice(Optional.empty(), Optional.empty());
        public boolean empty() { return ranged.isEmpty() && melee.isEmpty(); }
    }

    private static final Comparator<Found> BEST = Comparator.comparingDouble(Found::dps).reversed().thenComparingInt(Found::id);

    /** effects: the best ranged weapon, never a launcher, with ammo to fire; and the best melee weapon. */
    public static Choice choose(List<Found> found) {
        var ranged = found.stream().filter(f -> f.kind().ranged() && f.kind() != Kind.LAUNCHER && f.ammo() > 0).min(BEST).map(Found::id);
        var melee = found.stream().filter(f -> f.kind() == Kind.MELEE).min(BEST).map(Found::id);
        return new Choice(ranged, melee);
    }

    /** requires: damage &ge; 0, speed &gt; 0. effects: a melee weapon's damage a second. */
    public static double melee(double damage, double speed) {
        if (!(damage >= 0) || !(speed > 0)) throw new IllegalArgumentException("damage " + damage + ", speed " + speed);
        return damage * speed;
    }

    /** effects: a full-draw arrow's damage, as vanilla rounds it up: its base damage times its speed. */
    public static int arrow(double speed) { return (int) Math.ceil(ARROW_DAMAGE * speed); }

    /** effects: a bow's damage a second, or a crossbow's. */
    public static double bow(boolean crossbow) {
        return crossbow ? arrow(CROSSBOW_SPEED) * 20.0 / CROSSBOW_CHARGE : arrow(BOW_SPEED) * 20.0 / BOW_DRAW;
    }

    /** requires: damage &ge; 0; projectiles, fireTicks, capacity &ge; 1; reloadPerRound &ge; 0.
     * effects: a gun's damage a second over a whole magazine: each round's damage (every projectile),
     * over its shots and its reload. */
    public static double gun(double damage, int projectiles, int fireTicks, int capacity, int reloadPerRound) {
        if (!(damage >= 0) || projectiles < 1 || fireTicks < 1 || capacity < 1 || reloadPerRound < 0)
            throw new IllegalArgumentException(damage + ", " + projectiles + ", " + fireTicks + ", " + capacity + ", " + reloadPerRound);
        return damage * projectiles * capacity * 20.0 / ((double) capacity * fireTicks + (double) capacity * reloadPerRound);
    }

    /** requires: available, most &ge; 0. effects: how many rounds or arrows it carries out. */
    public static int ammo(int available, int most) {
        if (available < 0 || most < 0) throw new IllegalArgumentException("available " + available + ", most " + most);
        return Math.min(available, most);
    }
}
