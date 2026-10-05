/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** A vanilla trade as a villager's purse sees it (D-0006): {@code out}, the emeralds the villager
 * pays the player (the offer's result, when that is emeralds), and {@code in}, the emeralds it takes
 * (its costs that are emeralds, after any discount). A trade both ways is netted. Immutable.
 *
 * <p>Rep invariant: out &ge; 0, in &ge; 0. */
public record Till(int out, int in) {
    public static final String EMERALD = "minecraft:emerald";
    public static final Till NONE = new Till(0, 0);

    public Till {
        if (out < 0 || in < 0) throw new IllegalArgumentException("out " + out + ", in " + in);
    }

    /** effects: the till of an offer that costs {@code countA} of {@code costA} and {@code countB}
     * of {@code costB} (blank or 0 when there is no second cost) and gives {@code resultCount} of
     * {@code result}. */
    public static Till of(String costA, int countA, String costB, int countB, String result, int resultCount) {
        int in = (EMERALD.equals(costA) ? countA : 0) + (EMERALD.equals(costB) ? countB : 0);
        return new Till(EMERALD.equals(result) ? resultCount : 0, in);
    }

    /** effects: true iff the purse can make this trade: it holds what the trade pays less what it
     * takes. */
    public boolean open(Purse p) { return p.emeralds() + in >= out; }

    /** requires: {@link #open}. effects: the purse after the trade: what it takes less what it pays,
     * what passes the cap lost. */
    public Purse after(Purse p, Purse.Rules r) {
        if (!open(p)) throw new IllegalArgumentException("the purse can't pay " + out + " with " + p.emeralds() + " and " + in + " in");
        return in >= out ? p.takeIn(in - out, r) : p.pay(out - in);
    }

    /** effects: true iff the trade moves no emeralds through the purse. */
    public boolean none() { return out == 0 && in == 0; }
}
