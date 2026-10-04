/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A farmer's harvest (D-0001): a crop is ripe at its greatest age, and it is replanted with one
 * seed taken out of what it dropped. */
public final class Harvest {
    private Harvest() {}

    /** Some count of one item, by its id. */
    public record Stack(String item, int count) {
        public Stack { Objects.requireNonNull(item); if (count < 1) throw new IllegalArgumentException(item + " x" + count); }
    }

    /** What a harvest leaves: whether a seed was found to replant, and the drops less that seed. */
    public record Split(boolean replant, List<Stack> rest) {
        public Split { rest = List.copyOf(rest); }
    }

    /** requires: maxAge &ge; 0. effects: true iff a crop at {@code age} is ripe. */
    public static boolean ripe(int age, int maxAge) {
        if (maxAge < 0) throw new IllegalArgumentException("maxAge < 0: " + maxAge);
        return age >= maxAge;
    }

    /** effects: {@code drops} with one {@code seed} taken out for replanting, from the first stack
     * that holds it; replant is false and the drops unchanged when there is none. */
    public static Split replantFrom(List<Stack> drops, String seed) {
        var rest = new ArrayList<Stack>(drops.size());
        boolean taken = false;
        for (var s : drops) {
            if (!taken && s.item().equals(seed)) {
                taken = true;
                if (s.count() > 1) rest.add(new Stack(s.item(), s.count() - 1));
            } else {
                rest.add(s);
            }
        }
        return new Split(taken, rest);
    }
}
