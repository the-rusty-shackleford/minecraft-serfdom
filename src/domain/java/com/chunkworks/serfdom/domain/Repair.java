/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Optional;

/** A blacksmith's repairs at the anvil (D-0002): vanilla's material repair, where each unit of the
 * item's repair material mends a quarter of its durability, done only in whole quarters so no
 * material is wasted. Two items are never merged. */
public final class Repair {
    private Repair() {}

    /** requires: maxDamage &ge; 0, 0 &le; damage &le; maxDamage. effects: the units of material
     * that mend the item without waste: one per whole quarter of its durability it has lost; 0 when
     * it has lost less than a quarter or a quarter of it is nothing. */
    public static int units(int maxDamage, int damage) {
        if (maxDamage < 0 || damage < 0 || damage > maxDamage) throw new IllegalArgumentException(maxDamage + ", " + damage);
        int quarter = maxDamage / 4;
        return quarter == 0 ? 0 : damage / quarter;
    }

    /** requires: as {@link #units}. effects: the damage left after {@code units} units, each
     * mending a quarter. */
    public static int after(int maxDamage, int damage, int units) {
        return Math.max(0, damage - units * (maxDamage / 4));
    }

    /** One worn item in the chests: its durability, what it has lost, and how much of its repair
     * material is stored. Immutable. */
    public record Worn(String item, int maxDamage, int damage, int material) {
        public Worn {
            if (maxDamage < 0 || damage < 0 || damage > maxDamage || material < 0) throw new IllegalArgumentException(item + ": " + maxDamage + ", " + damage + ", " + material);
        }
        /** effects: the units a repair now spends: what mends it without waste, as far as the
         * stored material goes. */
        public int spend() { return Math.min(units(maxDamage, damage), material); }
    }

    /** effects: the index of the item to mend first: of those a repair would spend at least one
     * unit on, the most worn by the share of its durability lost, the earliest on a tie; empty when
     * none. */
    public static Optional<Integer> first(List<Worn> worn) {
        int best = -1;
        for (int i = 0; i < worn.size(); i++) {
            var w = worn.get(i);
            if (w.spend() < 1) continue;
            if (best < 0 || (long) w.damage() * worn.get(best).maxDamage() > (long) worn.get(best).damage() * w.maxDamage()) best = i;
        }
        return best < 0 ? Optional.empty() : Optional.of(best);
    }
}
