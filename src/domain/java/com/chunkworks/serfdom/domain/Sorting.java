/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Where a worker puts what it carries (D-0001): a container already holding the item, else the one
 * whose items share the most tags with it, else the overflow container, the one nearest the post.
 * Ties go to the container nearest the post. */
public final class Sorting {
    private Sorting() {}

    /** One container as seen for one item: whether it already holds that item, how many tags its
     * items share with it at most, and how many more of it fit. */
    public record Bin(Cell at, boolean holds, int sharedTags, int room) {
        public Bin { Objects.requireNonNull(at); if (sharedTags < 0 || room < 0) throw new IllegalArgumentException(at + ": " + sharedTags + ", " + room); }
    }

    /** effects: the containers to try for one item, best first, each at most once and only with
     * room: those holding it, nearest the post first; then those sharing tags with it, most shared
     * first and nearest on a tie; then {@code overflow} if it has room and is not listed already.
     * Empty when the item has nowhere to go. */
    public static List<Cell> order(Cell post, List<Bin> bins, Optional<Cell> overflow) {
        Comparator<Bin> nearest = Comparator.<Bin>comparingLong(b -> b.at().distanceSq(post))
                .thenComparingInt(b -> b.at().y()).thenComparingInt(b -> b.at().x()).thenComparingInt(b -> b.at().z());
        var out = new ArrayList<Cell>();
        bins.stream().filter(b -> b.room() > 0 && b.holds()).sorted(nearest).forEach(b -> out.add(b.at()));
        bins.stream().filter(b -> b.room() > 0 && !b.holds() && b.sharedTags() > 0)
                .sorted(Comparator.comparingInt(Bin::sharedTags).reversed().thenComparing(nearest))
                .forEach(b -> out.add(b.at()));
        overflow.flatMap(o -> bins.stream().filter(b -> b.at().equals(o)).findFirst())
                .filter(b -> b.room() > 0 && !out.contains(b.at()))
                .ifPresent(b -> out.add(b.at()));
        return out;
    }

    /** effects: the container nearest {@code post} of {@code containers}, ties broken by y, x then
     * z; empty when there are none. */
    public static Optional<Cell> overflow(Cell post, List<Cell> containers) {
        return containers.stream().min(Comparator.<Cell>comparingLong(c -> c.distanceSq(post))
                .thenComparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z));
    }
}
