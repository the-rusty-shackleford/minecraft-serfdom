/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Set;

/** A village's climate (D-0006, 4b), from the biome at a villager's bed: hot (desert, savanna,
 * badlands, jungle), cold (the snowy biomes), or temperate. An item may be tagged as from a hot or a
 * cold climate; a villager will pay more for one from a climate not its own. An untagged item is from
 * nowhere in particular and costs the same everywhere. */
public enum Climate {
    HOT, COLD, TEMPERATE;

    /** effects: the climate of a biome tagged hot, cold, both or neither: hot or cold when it is one
     * alone, temperate otherwise. */
    public static Climate of(boolean hot, boolean cold) {
        if (hot == cold) return TEMPERATE;
        return hot ? HOT : COLD;
    }

    /** requires: bonus &gt; 0. effects: what a villager of this climate's village multiplies an item's
     * value by: {@code bonus} for an item from somewhere (its {@code origins} not empty) that is not
     * from here, 1 otherwise. */
    public double factor(Set<Climate> origins, double bonus) {
        if (!(bonus > 0)) throw new IllegalArgumentException("bonus " + bonus);
        return !origins.isEmpty() && !origins.contains(this) ? bonus : 1.0;
    }
}
