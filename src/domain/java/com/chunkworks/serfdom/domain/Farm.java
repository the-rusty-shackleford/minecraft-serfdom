/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** A shared farm (D-0008): farming posts of one owner and one job whose areas touch or overlap,
 * linked through one another; the plots its field is cut into; and the plot a farmer works next. */
public final class Farm {
    /** A plot's side in blocks: plots are the 8 by 8 columns of the world grid. */
    public static final int PLOT = 8;
    private Farm() {}

    /** A post as a farm sees it: where it stands, its radius, its owner and its job.
     *
     * <p>Rep invariant: radius &ge; 0; nothing null. */
    public record Post(Cell at, int radius, String owner, String job) {
        public Post {
            Objects.requireNonNull(at);
            Objects.requireNonNull(owner);
            Objects.requireNonNull(job);
            if (radius < 0) throw new IllegalArgumentException("radius " + radius);
        }

        /** effects: true iff {@code cell} lies in this post's area, the cube of its radius around it. */
        public boolean holds(Cell cell) { return Radius.contains(at, radius, cell); }
    }

    /** effects: true iff {@code a} and {@code b} are of one farm: the same owner and job, and areas
     * that touch or overlap, which is along each axis at most a's radius + b's radius + 1 apart. */
    public static boolean touches(Post a, Post b) {
        if (!a.owner().equals(b.owner()) || !a.job().equals(b.job())) return false;
        int reach = a.radius() + b.radius() + 1;
        return Math.abs(a.at().x() - b.at().x()) <= reach && Math.abs(a.at().y() - b.at().y()) <= reach
                && Math.abs(a.at().z() - b.at().z()) <= reach;
    }

    /** requires: {@code start} is one of {@code posts}.
     * effects: {@code start}'s farm: {@code start} and every post of {@code posts} linked to it through
     * posts that touch, in the order of {@code posts}. */
    public static List<Post> of(List<Post> posts, Post start) {
        int first = posts.indexOf(start);
        if (first < 0) throw new IllegalArgumentException("the post is not among the posts: " + start);
        var in = new boolean[posts.size()];
        in[first] = true;
        var queue = new ArrayDeque<Integer>();
        queue.add(first);
        while (!queue.isEmpty()) {
            var at = posts.get(queue.poll());
            for (int i = 0; i < posts.size(); i++)
                if (!in[i] && touches(at, posts.get(i))) { in[i] = true; queue.add(i); }
        }
        var out = new ArrayList<Post>();
        for (int i = 0; i < posts.size(); i++) if (in[i]) out.add(posts.get(i));
        return out;
    }

    /** A plot: one 8 by 8 column of the world grid, named by its cells' coordinates divided by 8,
     * rounded down. */
    public record Plot(int x, int z) {
        /** effects: the plot the column ({@code x}, {@code z}) lies in. */
        public static Plot of(int x, int z) { return new Plot(Math.floorDiv(x, PLOT), Math.floorDiv(z, PLOT)); }
        /** effects: the plot {@code cell} lies in. */
        public static Plot of(Cell cell) { return of(cell.x(), cell.z()); }
    }

    /** What a plot offers a farmer looking for work: how near its nearest work is to the farmer
     * (squared, in blocks), and whether any of its work lies in the farmer's own post's area.
     *
     * <p>Rep invariant: distanceSq &ge; 0; plot not null. */
    public record Offer(Plot plot, long distanceSq, boolean own) {
        public Offer {
            Objects.requireNonNull(plot);
            if (distanceSq < 0) throw new IllegalArgumentException("distanceSq " + distanceSq);
        }
    }

    /** The order a farmer looks at offers in: its own post's area first, then the nearest, then the
     * lower plot (x, then z), so the choice never depends on the list's order. */
    private static final Comparator<Offer> ORDER = Comparator.comparing((Offer o) -> !o.own())
            .thenComparingLong(Offer::distanceSq).thenComparingInt(o -> o.plot().x()).thenComparingInt(o -> o.plot().z());

    /** effects: the plot a farmer works next: of the offers whose plot no other worker holds, the
     * first in {@link #ORDER}'s order (its own area's before the rest of the farm's, the nearest
     * first); empty when there is no offer or every one is held. */
    public static Optional<Plot> next(List<Offer> offers, Predicate<Plot> heldByAnother) {
        return offers.stream().filter(o -> !heldByAnother.test(o.plot())).min(ORDER).map(Offer::plot);
    }
}
