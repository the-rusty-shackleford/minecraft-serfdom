/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** What a villager makes of a stall, or of a villager selling (D-0006). It will pay its base value for
 * an item times the bonuses that apply: its climate's (1.5 for an item from another climate), its
 * taste's (0.5 to 1.5), and a need's (1.5). At or under that it buys; above it, its chance of buying falls
 * in a straight line to nothing at {@link #CEILING} times that; and a purse short of one sale can't
 * afford it. Buying, it takes as many sales as it wants, while it can pay, the stall has them and the
 * day's purchases allow. An item at or under its base value is a bargain. */
public final class Verdict {
    /** How far over what it will pay a villager may still buy: never at 150% of it. */
    public static final double CEILING = 1.5;
    private Verdict() {}

    /** How a villager reacts, and so what the stall's ledger says. */
    public enum Reaction {
        BARGAIN, BOUGHT, TOO_PRICEY, CANT_AFFORD, NOT_INTERESTED;
        public boolean bought() { return this == BARGAIN || this == BOUGHT; }
    }

    /** The facts at the stall: the item's base value and the bonuses on it, the stall (open), the
     * villager's emeralds, the sales it wants and may still make today, and a draw in [0, 1) for the
     * chance. RI: bonus &gt; 0; wanted &ge; 1; left &ge; 1; 0 &le; draw &lt; 1; the stall is open. */
    public record Facts(double base, double bonus, Stall stall, int emeralds, int wanted, int left, double draw) {
        public Facts {
            Objects.requireNonNull(stall);
            if (!(bonus > 0) || wanted < 1 || left < 1 || !(draw >= 0 && draw < 1) || emeralds < 0)
                throw new IllegalArgumentException("bonus " + bonus + ", wanted " + wanted + ", left " + left + ", draw " + draw + ", emeralds " + emeralds);
            if (!stall.open()) throw new IllegalArgumentException("a closed stall is not judged");
        }
    }

    /** The outcome: the reaction and the sales made. RI: sales &gt; 0 iff the reaction bought. */
    public record Outcome(Reaction reaction, int sales) {
        public Outcome {
            Objects.requireNonNull(reaction);
            if ((sales > 0) != reaction.bought() || sales < 0) throw new IllegalArgumentException(reaction + " with " + sales + " sales");
        }
    }

    /** effects: what a villager will pay for an item: its base value times the bonuses. */
    public static double willing(double base, double bonus) { return base * bonus; }

    /** requires: willing &gt; 0. effects: the chance a villager buys an item at {@code each}: 1 at
     * or under {@code willing}, falling in a straight line to 0 at {@link #CEILING} times it. */
    public static double chance(double each, double willing) {
        if (!(willing > 0)) throw new IllegalArgumentException("willing " + willing);
        if (each <= willing) return 1.0;
        return Math.max(0.0, 1.0 - (each - willing) / ((CEILING - 1.0) * willing));
    }

    /** effects: what the villager does, as the class describes; an item with no value is not
     * interesting. */
    public static Outcome judge(Facts f) {
        if (!(f.base() > 0)) return new Outcome(Reaction.NOT_INTERESTED, 0);
        var s = f.stall();
        if (f.emeralds() < s.price()) return new Outcome(Reaction.CANT_AFFORD, 0);
        if (f.draw() >= chance(s.each(), willing(f.base(), f.bonus()))) return new Outcome(Reaction.TOO_PRICEY, 0);
        int sales = Math.min(Math.min(f.wanted(), f.left()), Math.min(s.sales(), f.emeralds() / s.price()));
        return new Outcome(s.each() <= f.base() ? Reaction.BARGAIN : Reaction.BOUGHT, sales);
    }
}
