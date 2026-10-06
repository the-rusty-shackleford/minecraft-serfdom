/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** A worker in a raid (D-0007): who musters, and what it does next.
 * <ul>
 * <li><b>Who:</b> owned (hired or captive), grown, not in chains, not on its way home.</li>
 * <li><b>Next:</b> with a raid on and mustering: unarmed and not yet having tried this raid, it goes
 * to arm; with nothing it can fight with, it hides; otherwise it fights a raider within reach, or
 * stands by. With no raid (or no longer mustering), anything it took goes back; else nothing.</li>
 * <li><b>Which hand:</b> its ranged weapon while it can fire (rounds loaded or ammo carried), else its
 * melee weapon, else nothing.</li>
 * <li><b>Reach:</b> a raider it can see within sight of itself, and within reach of where it belongs
 * (its post or its bed).</li>
 * <li><b>Reload:</b> loose rounds from what it carries, as many as fit, a round's reload each.</li>
 * </ul> */
public final class Defence {
    /** How far a defender looks for raiders. */
    public static final double SIGHT = 32;
    private Defence() {}

    /** Who a villager is, as the defence asks. */
    public record Who(boolean owned, boolean grown, boolean cuffed, boolean escaping) {}

    /** effects: true iff the villager arms in a raid. */
    public static boolean musters(Who w) { return w.owned() && w.grown() && !w.cuffed() && !w.escaping(); }

    /** What it does next. */
    public enum Step { ARM, FIGHT, STAND, HIDE, PUT_BACK, NONE }

    /** The facts: a raid on where it stands; whether it musters; whether it carries anything it took;
     * whether it has tried arming this raid; whether its ranged weapon can fire; whether it has a melee
     * weapon; whether a raider is within reach. */
    public record Facts(boolean raid, boolean musters, boolean carries, boolean tried, boolean rangedReady, boolean melee, boolean target) {}

    /** effects: the next step, as the class describes. */
    public static Step next(Facts f) {
        if (!f.raid() || !f.musters()) return f.carries() ? Step.PUT_BACK : Step.NONE;
        if (!f.carries() && !f.tried()) return Step.ARM;
        if (!f.rangedReady() && !f.melee()) return Step.HIDE;
        return f.target() ? Step.FIGHT : Step.STAND;
    }

    /** Which weapon is in its hand. */
    public enum Hand { RANGED, MELEE, NONE }

    /** effects: the weapon it holds, as the class describes. */
    public static Hand hand(boolean rangedReady, boolean melee) {
        if (rangedReady) return Hand.RANGED;
        return melee ? Hand.MELEE : Hand.NONE;
    }

    /** requires: every distance &ge; 0. effects: true iff a raider {@code toTarget} blocks away and
     * {@code fromHome} blocks from where the defender belongs is within reach. */
    public static boolean inReach(double toTarget, double fromHome, double sight, double reach) {
        if (!(toTarget >= 0) || !(fromHome >= 0)) throw new IllegalArgumentException(toTarget + ", " + fromHome);
        return toTarget <= sight && fromHome <= reach;
    }

    /** A reload: the rounds loaded and the ticks it takes. */
    public record Reload(int rounds, int ticks) {}

    /** requires: capacity &ge; 1; 0 &le; loaded &le; capacity; carried, perRound &ge; 0. effects: the
     * reload, as the class describes; none when it is full or carries nothing. */
    public static Reload reload(int capacity, int loaded, int carried, int perRound) {
        if (capacity < 1 || loaded < 0 || loaded > capacity || carried < 0 || perRound < 0)
            throw new IllegalArgumentException(capacity + ", " + loaded + ", " + carried + ", " + perRound);
        int n = Math.min(capacity - loaded, carried);
        return new Reload(n, n * perRound);
    }

    /** requires: speed &gt; 0. effects: the ticks between blows of a melee weapon of {@code speed}
     * attacks a second, at least one. */
    public static int cooldown(double speed) {
        if (!(speed > 0)) throw new IllegalArgumentException("speed " + speed);
        return Math.max(1, (int) Math.ceil(20 / speed));
    }
}
