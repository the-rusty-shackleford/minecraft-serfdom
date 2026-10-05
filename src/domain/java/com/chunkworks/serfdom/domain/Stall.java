/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** A For Sale block's offer (D-0006): the item it sells (blank when none is set), how many a sale,
 * the price of a sale in emeralds, how many of the item its stock holds, and how many emeralds its
 * proceeds can still take. Immutable.
 *
 * <p>Rep invariant: 1 &le; quantity &le; {@link #MOST}; 1 &le; price &le; {@link #MOST}; stock &ge; 0;
 * room &ge; 0. */
public record Stall(String item, int quantity, int price, int stock, int room) {
    /** The most a sale holds, and the most a sale costs. */
    public static final int MOST = 64;

    public Stall {
        Objects.requireNonNull(item);
        if (quantity < 1 || quantity > MOST || price < 1 || price > MOST || stock < 0 || room < 0)
            throw new IllegalArgumentException("quantity " + quantity + ", price " + price + ", stock " + stock + ", room " + room);
    }

    /** effects: true iff it sells something now: an item is set, its stock holds a sale, and its
     * proceeds can take a sale's price. */
    public boolean open() { return !item.isBlank() && stock >= quantity && room >= price; }

    /** effects: the price of one item: a sale's price over how many it holds. */
    public double each() { return price / (double) quantity; }

    /** effects: how many sales it can make in a row, before its stock or its proceeds run out. */
    public int sales() { return item.isBlank() ? 0 : Math.min(stock / quantity, room / price); }
}
