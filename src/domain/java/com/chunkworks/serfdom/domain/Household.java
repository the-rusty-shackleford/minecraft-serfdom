/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A free villager's household (D-0006): what it has at home, as counts of the items it bought,
 * and how far each of its needs is through using up the next one. A town house has no chest, so a
 * free villager's purchases are put here, and used up morning by morning as its needs list says,
 * which brings it back to the stalls. Immutable.
 *
 * <p>Rep invariant: every count &gt; 0; every wear in [0, 1). */
public record Household(Map<String, Integer> goods, Map<String, Double> wear) {
    public static final Household EMPTY = new Household(Map.of(), Map.of());
    /** The need every villager has, which a shopper sees to first. */
    public static final String FOOD = "food";

    /** One need of a profession's list: its name, the items that meet it, how many it keeps at home,
     * and how many it uses a day. Immutable. RI: name not blank; keep &ge; 0; perDay &ge; 0. */
    public record Need(String name, Set<String> items, int keep, double perDay) {
        public Need {
            Objects.requireNonNull(name);
            if (name.isBlank()) throw new IllegalArgumentException("blank need");
            items = Set.copyOf(items);
            if (keep < 0 || !(perDay >= 0)) throw new IllegalArgumentException(name + ": keep " + keep + ", perDay " + perDay);
        }
        public boolean food() { return FOOD.equals(name); }
    }

    public Household {
        goods = Map.copyOf(goods);
        wear = Map.copyOf(wear);
        for (var e : goods.entrySet()) if (e.getValue() <= 0) throw new IllegalArgumentException("count of " + e.getKey() + ": " + e.getValue());
        for (var e : wear.entrySet()) if (!(e.getValue() >= 0 && e.getValue() < 1)) throw new IllegalArgumentException("wear of " + e.getKey() + ": " + e.getValue());
    }

    /** effects: how many of the items that meet {@code need} the household holds. */
    public int has(Need need) {
        int n = 0;
        for (var item : need.items()) n += goods.getOrDefault(item, 0);
        return n;
    }

    /** effects: how many short of what it keeps the household is for {@code need}; 0 when it has
     * enough. */
    public int shortOf(Need need) { return Math.max(0, need.keep() - has(need)); }

    /** requires: n &ge; 0. effects: the household with {@code n} more of {@code item}. */
    public Household add(String item, int n) {
        if (n < 0) throw new IllegalArgumentException("n " + n);
        if (n == 0) return this;
        var g = new HashMap<>(goods);
        g.merge(item, n, Integer::sum);
        return new Household(g, wear);
    }

    /** effects: the household after a morning's use of {@code needs}: each need's wear grows by its
     * use a day, and for every whole one an item that meets it is used up, the most plentiful first
     * (ties to the first by id). When a need has nothing left, its wear is forgotten: nothing is
     * owed for a day it had nothing to use. */
    public Household morning(List<Need> needs) {
        var g = new HashMap<>(goods);
        var w = new HashMap<>(wear);
        for (var need : needs) {
            double worn = w.getOrDefault(need.name(), 0.0) + need.perDay();
            while (worn >= 1 && count(g, need) > 0) {
                var item = plentiful(g, need);
                g.computeIfPresent(item, (k, n) -> n > 1 ? n - 1 : null);
                worn -= 1;
            }
            if (count(g, need) == 0 || worn <= 0) w.remove(need.name());
            else w.put(need.name(), worn);
        }
        return new Household(g, w);
    }

    /** effects: the needs the household is short of, food first, then in the list's order: each
     * with how many it is short. */
    public List<Shopping.Want> wants(List<Need> needs) {
        var out = new ArrayList<Shopping.Want>();
        for (var need : needs) {
            int n = shortOf(need);
            if (n <= 0 || need.items().isEmpty()) continue;
            var want = new Shopping.Want(need.name(), need.items(), n, Shopping.Unit.ITEMS, Shopping.Dest.HOUSEHOLD);
            if (need.food()) out.addFirst(want); else out.add(want);
        }
        return out;
    }

    private static int count(Map<String, Integer> g, Need need) {
        int n = 0;
        for (var item : need.items()) n += g.getOrDefault(item, 0);
        return n;
    }

    private static String plentiful(Map<String, Integer> g, Need need) {
        String best = null;
        for (var item : need.items()) {
            int n = g.getOrDefault(item, 0);
            if (n <= 0) continue;
            if (best == null || n > g.get(best) || (n == g.get(best) && item.compareTo(best) < 0)) best = item;
        }
        return best;
    }
}
