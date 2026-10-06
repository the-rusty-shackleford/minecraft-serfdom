/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;

/** A free villager selling to other villagers (D-0006, 4b): of each item its profession sells, what
 * it carries beyond what it keeps, at the item's base value. Purses hold whole emeralds, so it sells
 * in lots worth that: an item worth an emerald or more one at a time, at its value rounded to whole
 * emeralds; a cheaper item as many as round to an emerald's worth, for one. It never refuses a sale
 * for want of room: what its purse takes past its cap is lost, as with a player's trade. */
public final class Peddler {
    /** A peddler's proceeds never fill. */
    public static final int ROOM = Integer.MAX_VALUE;
    private Peddler() {}

    /** A lot: how many items, for how many emeralds. RI: both in [1, {@link Stall#MOST}]. */
    public record Lot(int quantity, int price) {
        public Lot {
            if (quantity < 1 || quantity > Stall.MOST || price < 1 || price > Stall.MOST) throw new IllegalArgumentException(quantity + " for " + price);
        }
    }

    /** effects: the lot an item of base value {@code value} is sold in, as the class describes; empty
     * for an item with no value. */
    public static Optional<Lot> lot(double value) {
        if (!(value > 0) || Double.isInfinite(value)) return Optional.empty();
        if (value >= 1) return Optional.of(new Lot(1, (int) Math.clamp(Math.round(value), 1, Stall.MOST)));
        return Optional.of(new Lot((int) Math.clamp(Math.round(1 / value), 1, Stall.MOST), 1));
    }

    /** requires: has, keep &ge; 0. effects: how many it may sell, carrying {@code has} and keeping
     * {@code keep}. */
    public static int spare(int has, int keep) {
        if (has < 0 || keep < 0) throw new IllegalArgumentException("has " + has + ", keep " + keep);
        return Math.max(0, has - keep);
    }

    /** effects: what it offers of {@code item} as a stall: its lot, its spare stock, and proceeds that
     * never fill. */
    public static Stall stall(String item, Lot lot, int spare) { return new Stall(item, lot.quantity(), lot.price(), spare, ROOM); }
}
