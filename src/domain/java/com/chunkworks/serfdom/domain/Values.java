/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** What an item is worth to a villager, in emeralds an item (D-0006): its base value, the first of
 * these that has one:
 * <ol>
 * <li>data: an entry naming the item, else the first entry whose tag holds it;</li>
 * <li>the median price an item over every simple offer in every profession's price list, a villager
 * buying it or selling it;</li>
 * <li>for food, bread's value for each point it fills;</li>
 * <li>none: nobody wants it.</li>
 * </ol>
 * A data value of 0 or less is none. */
public final class Values {
    public static final String BREAD = "minecraft:bread";
    private Values() {}

    /** A data entry for a tag: the items it holds and their value. Immutable. RI: tag not blank. */
    public record Tagged(String tag, Set<String> items, double value) {
        public Tagged {
            Objects.requireNonNull(tag);
            if (tag.isBlank()) throw new IllegalArgumentException("blank tag");
            items = Set.copyOf(items);
        }
    }

    /** What the values are worked out from: data by item, data by tag in file order, the prices
     * seen in offers by item, and how much each food fills. */
    public record Facts(Map<String, Double> items, List<Tagged> tags, Map<String, List<Double>> prices, Map<String, Integer> nutrition) {
        public Facts {
            items = Map.copyOf(items);
            tags = List.copyOf(tags);
            prices = Map.copyOf(prices);
            nutrition = Map.copyOf(nutrition);
        }
    }

    /** A price seen in an offer: {@code item} at {@code each} emeralds an item. */
    public record Seen(String item, double each) {}

    /** effects: the price an offer shows, when it is simple: a villager buying one kind of item for
     * emeralds, or selling one kind for emeralds; empty for any other (a second cost, emeralds both
     * ways, nothing counted). */
    public static Optional<Seen> seen(String costA, int countA, String costB, int countB, String result, int resultCount) {
        boolean second = costB != null && !costB.isBlank() && countB > 0;
        if (second || countA <= 0 || resultCount <= 0 || costA.equals(result)) return Optional.empty();
        if (Till.EMERALD.equals(result)) return Optional.of(new Seen(costA, resultCount / (double) countA));
        if (Till.EMERALD.equals(costA)) return Optional.of(new Seen(result, countA / (double) resultCount));
        return Optional.empty();
    }

    /** effects: the median of {@code xs}; empty for none. */
    public static OptionalDouble median(List<Double> xs) {
        if (xs.isEmpty()) return OptionalDouble.empty();
        var s = new ArrayList<>(xs);
        s.sort(Double::compare);
        int n = s.size();
        return OptionalDouble.of(n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2);
    }

    /** effects: every item with a base value, and its value, as the class describes. */
    public static Map<String, Double> table(Facts f) {
        var out = new HashMap<String, Double>();
        var refused = new HashSet<String>();
        // 1. Data: items, then tags in order; a value of 0 or less refuses the item outright.
        f.items().forEach((item, v) -> { if (v > 0) out.put(item, v); else refused.add(item); });
        for (var t : f.tags())
            for (var item : t.items()) {
                if (out.containsKey(item) || refused.contains(item)) continue;
                if (t.value() > 0) out.put(item, t.value()); else refused.add(item);
            }
        // 2. The price lists.
        f.prices().forEach((item, seen) -> {
            if (!out.containsKey(item) && !refused.contains(item)) median(seen).ifPresent(m -> { if (m > 0) out.put(item, m); });
        });
        // 3. Food, at bread's rate.
        var bread = out.get(BREAD);
        var breadFills = f.nutrition().get(BREAD);
        if (bread != null && breadFills != null && breadFills > 0) {
            double perPoint = bread / breadFills;
            f.nutrition().forEach((item, fills) -> {
                if (fills > 0 && !out.containsKey(item) && !refused.contains(item)) out.put(item, fills * perPoint);
            });
        }
        return out;
    }
}
