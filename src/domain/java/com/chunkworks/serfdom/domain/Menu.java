/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** What a worker has for one bite of its meal (D-0005), the first that applies:
 * <ol>
 * <li>ready food from its home chest (the container nearest its bed);</li>
 * <li>ready food from the chests of its owner's Work Posts near its bed, nearest post first
 * (Rusty's call: the cook's kitchen is the canteen);</li>
 * <li>a dish it cooks from its home chest at a free station near home, as much as this meal needs
 * and at most {@link #MOST} at a time;</li>
 * <li>raw food from its home chest, when no free station can cook it;</li>
 * <li>nothing: it goes hungry.</li>
 * </ol>
 * Food is anything that fills, unless it is refused (a harmful effect, or the refusal tag: golden
 * apples, chorus fruit). Ready food is food that no heat recipe (furnace, smoker, campfire) turns
 * into food that fills more: bread, cooked meat, carrots, Farmer's Delight's meals, not raw beef
 * or potatoes. Of the ready food it eats the most plentiful first, so bread goes before golden
 * carrots, and nothing it would overshoot on by more than half its worth ({@link Hunger#wants}).
 * At a crafting table it makes only the table meals (bread, stews), never a golden carrot out of
 * your gold. The meal is eaten a bite at a time, choosing again after each. */
public final class Menu {
    /** The most crafts of one dish cooked for one meal: a campfire's four places. */
    public static final int MOST = 4;
    private Menu() {}

    /** An item as food: how much it fills, whether it is refused, what eating it leaves behind
     * (a bowl). Immutable. RI: nutrition &ge; 0. */
    public record Food(int nutrition, boolean refused, Optional<String> leaves) {
        public Food {
            if (nutrition < 0) throw new IllegalArgumentException("nutrition " + nutrition);
            Objects.requireNonNull(leaves);
        }
    }

    /** The facts of one bite: how fed the worker is; what its home chest holds and what each of its
     * owner's posts near its bed holds, nearest first; what fills; every heat recipe, which says what
     * food is still raw; the recipes of the free stations near home; and the crafting table's meals. */
    public record Facts(Hunger hunger, Map<String, Integer> home, List<Map<String, Integer>> posts, Map<String, Food> foods,
                        Recipes.Rules heat, Recipes.Rules kitchen, Set<String> tableMeals) {
        public Facts {
            Objects.requireNonNull(hunger);
            home = Map.copyOf(home);
            posts = posts.stream().map(Map::copyOf).toList();
            foods = Map.copyOf(foods);
            Objects.requireNonNull(heat);
            Objects.requireNonNull(kitchen);
            tableMeals = Set.copyOf(tableMeals);
        }
    }

    public enum Kind { EAT_HOME, EAT_POST, COOK, EAT_RAW, NOTHING }

    /** One bite: eat {@code item} from home, from post {@code post}, or raw; or cook {@code rule}
     * {@code times} times with {@code picks} (one per cell) from home; or nothing.
     * RI: an eating choice names its item, and a post choice its post (&ge; 0); a cook choice names
     * its rule, times &ge; 1 and one pick per cell; nothing names nothing. */
    public record Choice(Kind kind, String item, int post, Optional<Recipes.Rule> rule, int times, List<String> picks) {
        public static final Choice NOTHING = new Choice(Kind.NOTHING, "", -1, Optional.empty(), 0, List.of());
        public Choice {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(item);
            Objects.requireNonNull(rule);
            picks = List.copyOf(picks);
            boolean eats = kind == Kind.EAT_HOME || kind == Kind.EAT_POST || kind == Kind.EAT_RAW;
            if (eats != !item.isBlank()) throw new IllegalArgumentException("an eating choice names its item");
            if ((kind == Kind.EAT_POST) != (post >= 0)) throw new IllegalArgumentException("a post choice names its post");
            if ((kind == Kind.COOK) != rule.isPresent()) throw new IllegalArgumentException("a cook choice names its rule");
            if (kind == Kind.COOK && (times < 1 || picks.size() != rule.get().cells().size())) throw new IllegalArgumentException("times and picks");
            if (kind != Kind.COOK && (times != 0 || !picks.isEmpty())) throw new IllegalArgumentException("only cooking has times and picks");
        }
        static Choice eat(Kind kind, String item, int post) { return new Choice(kind, item, post, Optional.empty(), 0, List.of()); }
        static Choice cook(Recipes.Rule rule, int times, List<String> picks) { return new Choice(Kind.COOK, "", -1, Optional.of(rule), times, picks); }
    }

    /** effects: true iff {@code item} is food the worker eats: it fills and is not refused. */
    public static boolean edible(String item, Map<String, Food> foods) {
        var f = foods.get(item);
        return f != null && f.nutrition() > 0 && !f.refused();
    }

    /** effects: true iff some heat recipe takes {@code item} and makes food that fills more. */
    public static boolean raw(String item, Facts f) {
        int worth = f.foods().get(item) == null ? 0 : f.foods().get(item).nutrition();
        for (var rule : f.heat().all()) {
            if (!edible(rule.result(), f.foods()) || f.foods().get(rule.result()).nutrition() <= worth) continue;
            for (var cell : rule.cells()) if (cell.contains(item)) return true;
        }
        return false;
    }

    /** effects: true iff {@code item} is ready food: edible and not raw. */
    public static boolean ready(String item, Facts f) { return edible(item, f.foods()) && !raw(item, f); }

    /** effects: what the worker has next, as the class describes. */
    public static Choice choose(Facts f) {
        var home = plentiful(f.home(), f, true);
        if (home.isPresent()) return Choice.eat(Kind.EAT_HOME, home.get(), -1);
        for (int i = 0; i < f.posts().size(); i++) {
            var there = plentiful(f.posts().get(i), f, true);
            if (there.isPresent()) return Choice.eat(Kind.EAT_POST, there.get(), i);
        }
        var dish = dish(f);
        if (dish.isPresent()) return dish.get();
        var uncooked = plentiful(f.home(), f, false).filter(item -> f.kitchen().all().stream().noneMatch(r -> r.cells().stream().anyMatch(c -> c.contains(item))));
        return uncooked.map(item -> Choice.eat(Kind.EAT_RAW, item, -1)).orElse(Choice.NOTHING);
    }

    /** effects: the most plentiful food in {@code stock} that is ready (or, when not {@code ready},
     * raw) and that the worker wants; ties go to the one that fills more, then by id. */
    private static Optional<String> plentiful(Map<String, Integer> stock, Facts f, boolean ready) {
        String best = null;
        for (var e : stock.entrySet()) {
            String item = e.getKey();
            if (e.getValue() <= 0 || !edible(item, f.foods()) || raw(item, f) == ready) continue;
            if (!f.hunger().wants(f.foods().get(item).nutrition())) continue;
            if (best == null || better(item, e.getValue(), best, stock.get(best), f)) best = item;
        }
        return Optional.ofNullable(best);
    }

    private static boolean better(String a, int countA, String b, int countB, Facts f) {
        if (countA != countB) return countA > countB;
        int na = f.foods().get(a).nutrition(), nb = f.foods().get(b).nutrition();
        if (na != nb) return na > nb;
        return a.compareTo(b) < 0;
    }

    /** effects: the dish to cook from home, of the kitchen's recipes for food the worker wants
     * (at a table only a table meal): the one that fills most, then the book's order; as many crafts
     * as make what the worker will eat of it (bite by bite, as {@link Hunger#wants} lets it), at most
     * {@link #MOST}, as many as home holds. */
    private static Optional<Choice> dish(Facts f) {
        Choice best = null;
        int bestWorth = -1;
        for (var rule : f.kitchen().all()) {
            if (!edible(rule.result(), f.foods())) continue;
            if (rule.station() == Station.TABLE && !f.tableMeals().contains(rule.result())) continue;
            int worth = f.foods().get(rule.result()).nutrition();
            if (!f.hunger().wants(worth) || worth <= bestWorth) continue;
            int eats = 0;
            for (var h = f.hunger(); h.wants(worth) && eats < MOST * rule.yield(); h = h.eat(worth)) eats++;
            int want = Math.min(MOST, (int) Math.ceil(eats / (double) rule.yield()));
            for (int times = want; times >= 1; times--) {
                var picks = picks(rule, times, f.home());
                if (picks.isPresent()) {
                    best = Choice.cook(rule, times, picks.get());
                    bestWorth = worth;
                    break;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** effects: one pick per cell of {@code rule}, the first option in each that home still holds
     * {@code times} of after the cells before it; empty when some cell has none. */
    private static Optional<List<String>> picks(Recipes.Rule rule, int times, Map<String, Integer> home) {
        var left = new HashMap<>(home);
        var out = new ArrayList<String>();
        for (var cell : rule.cells()) {
            String pick = null;
            for (var option : cell) if (left.getOrDefault(option, 0) >= times) { pick = option; break; }
            if (pick == null) return Optional.empty();
            left.merge(pick, -times, Integer::sum);
            out.add(pick);
        }
        return Optional.of(out);
    }
}
