/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A villager's taste (D-0006, 4b): a multiplier for each of four categories of goods, which it
 * multiplies an item's value by. It is drawn once and for all from the villager's UUID, so it never
 * changes and no two villagers are alike, and it leans by profession: a lean of +1 pulls a category's
 * draw toward the top of the range, -1 toward the bottom, 0 leaves it even. The category it rates
 * highest is its favourite; it likes a category it rates above 1, and wants a few of anything with a
 * value in one. An item in several categories is valued by the one it rates highest; an item in none
 * is not a matter of taste (1).
 *
 * <p>Rep invariant: every multiplier is finite and &gt; 0. Abstraction function: the villager values an
 * item of category c at {@code of(c)} times what it would otherwise. */
public record Taste(double food, double tools, double decor, double luxury) {
    /** The categories, in the order a tie for favourite goes to. */
    public enum Category { FOOD, TOOLS, DECOR, LUXURY }
    /** No lean, and every multiplier 1: taste that changes nothing. */
    public static final Taste EVEN = new Taste(1, 1, 1, 1);

    public Taste {
        for (double m : new double[]{food, tools, decor, luxury})
            if (!(m > 0) || Double.isInfinite(m)) throw new IllegalArgumentException("a multiplier of " + m);
    }

    /** effects: the multiplier for {@code c}. */
    public double of(Category c) {
        return switch (c) {
            case FOOD -> food;
            case TOOLS -> tools;
            case DECOR -> decor;
            case LUXURY -> luxury;
        };
    }

    /** requires: 0 &le; spread &lt; 1; every lean in [-1, 1]. effects: the taste of the villager whose
     * UUID is ({@code most}, {@code least}): for each category a draw u in [0, 1) from the UUID, and
     * the multiplier {@link #lean}(u, its profession's lean, spread). A category without a lean has 0. */
    public static Taste of(long most, long least, Map<Category, Double> leans, double spread) {
        var m = new EnumMap<Category, Double>(Category.class);
        for (var c : Category.values()) m.put(c, lean(draw(most, least, c), leans.getOrDefault(c, 0.0), spread));
        return new Taste(m.get(Category.FOOD), m.get(Category.TOOLS), m.get(Category.DECOR), m.get(Category.LUXURY));
    }

    /** requires: 0 &le; u &lt; 1; -1 &le; lean &le; 1; 0 &le; spread &lt; 1. effects: the multiplier
     * for a draw {@code u}: 1 - spread + 2 spread u^(2^-lean). Even (lean 0) it is spread evenly over
     * [1 - spread, 1 + spread); a lean bends the draw toward one end without leaving the range. */
    public static double lean(double u, double lean, double spread) {
        if (!(u >= 0 && u < 1) || !(lean >= -1 && lean <= 1) || !(spread >= 0 && spread < 1))
            throw new IllegalArgumentException("u " + u + ", lean " + lean + ", spread " + spread);
        return 1 - spread + 2 * spread * Math.pow(u, Math.pow(2, -lean));
    }

    /** effects: a draw in [0, 1) for category {@code c} of the UUID ({@code most}, {@code least}):
     * SplitMix64's finalizer over the UUID's halves and the category, written out here so the draw
     * never changes with the JDK. */
    static double draw(long most, long least, Category c) {
        long h = mix(most ^ mix(least ^ mix(0x9E3779B97F4A7C15L * (c.ordinal() + 1))));
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** effects: the category it rates highest; a tie to the first in {@link Category}'s order. */
    public Category favourite() {
        var best = Category.FOOD;
        for (var c : Category.values()) if (of(c) > of(best)) best = c;
        return best;
    }

    /** effects: true iff it rates {@code c} above 1. */
    public boolean likes(Category c) { return of(c) > 1; }

    /** effects: what it multiplies the value of an item in {@code categories} by: the highest of their
     * multipliers; 1 for an item in none. */
    public double factor(Set<Category> categories) {
        double best = 0;
        for (var c : categories) best = Math.max(best, of(Objects.requireNonNull(c)));
        return categories.isEmpty() ? 1.0 : best;
    }

    /** effects: true iff an item in {@code categories} is from its favourite. */
    public boolean favourite(Set<Category> categories) { return categories.contains(favourite()); }

    /** requires: has &ge; 0; most &ge; 0. effects: how many more of an item in {@code categories} it
     * wants, holding {@code has} of it: up to {@code most} of anything with a value in a category it
     * likes; none of anything else. */
    public int wants(Set<Category> categories, boolean valued, int has, int most) {
        if (has < 0 || most < 0) throw new IllegalArgumentException("has " + has + ", most " + most);
        if (!valued || categories.stream().noneMatch(this::likes)) return 0;
        return Math.max(0, most - has);
    }
}
