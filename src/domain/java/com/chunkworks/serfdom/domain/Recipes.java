/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.*;
import java.util.function.Predicate;

/** What a workshop worker can make in steps (D-0002), Warehouse Manager's planner (its D-0012 and
 * D-0013) with a station on every rule. An item whose ingredients are not all on hand may still be
 * made when the missing ones can themselves be made from what is on hand: a pickaxe from ingots,
 * the ingots from raw iron in a furnace. Counts are exact: a material spent on one part is not
 * counted again for another, and what a step makes beyond the need stays available to the parts
 * after it. Choices are greedy, in the recipe's order, and never revisited: a cell takes the first
 * of its alternatives that is on hand or can be made in full, and an item is made by the first of
 * its rules that works. So a plan that is found is exact and executable; a plan that is not found
 * may exist (a later alternative would have left more for an earlier cell).
 *
 * <p>A cell is one ingredient of one craft, as the recipe's pattern has it: three ingot cells for a
 * pickaxe, not one cell of three. Each cell is filled with one kind for every craft asked for. */
public final class Recipes {
    /** One way to make an item: the station, the recipe's cells (one option list per non-empty
     * ingredient, in the recipe's order, each the item ids it accepts), what it makes and how many
     * per craft. Immutable. RI: id and result non-blank; no cell empty; yield &ge; 1. */
    public record Rule(String id, Station station, List<List<String>> cells, String result, int yield) {
        public Rule {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
            Objects.requireNonNull(station, "station");
            if (result == null || result.isBlank()) throw new IllegalArgumentException("result");
            if (yield < 1) throw new IllegalArgumentException("yield");
            cells = copyCells(cells);
        }
    }

    /** What could neither be found nor made: {@code missing} more of any one of {@code options}.
     * Immutable. RI: options non-empty; missing &ge; 1. */
    public record Shortage(List<String> options, int missing) {
        public Shortage {
            options = List.copyOf(options);
            if (options.isEmpty()) throw new IllegalArgumentException("options");
            if (missing <= 0) throw new IllegalArgumentException("missing");
        }
    }

    /** The rule book: every rule by what it makes, the rules for one item ordered by yield
     * ascending, then station in {@link Station}'s order, then id. So three steel come from iron
     * and coal (three a craft) before a block is broken into nine, and cooked beef comes from a
     * smoker before a furnace. Immutable. */
    public static final class Rules {
        public static final Rules NONE = new Rules(Map.of(), List.of());
        private final Map<String, List<Rule>> byResult;
        private final List<Rule> all;
        private Rules(Map<String, List<Rule>> byResult, List<Rule> all) { this.byResult = byResult; this.all = all; }
        /** effects: the rule book over the rules, in the order described above. */
        public static Rules of(Collection<Rule> rules) {
            var sorted = new ArrayList<>(rules);
            sorted.sort(Comparator.comparingInt(Rule::yield).thenComparing(Rule::station).thenComparing(Rule::id));
            var by = new HashMap<String, List<Rule>>();
            for (var r : sorted) by.computeIfAbsent(r.result(), k -> new ArrayList<>()).add(r);
            by.replaceAll((k, v) -> List.copyOf(v));
            return new Rules(Map.copyOf(by), List.copyOf(sorted));
        }
        /** effects: the rules that make the item, in the book's order; empty for an item no rule
         * makes. */
        public List<Rule> making(String item) { return byResult.getOrDefault(item, List.of()); }
        /** effects: the items some rule makes. */
        public Set<String> results() { return byResult.keySet(); }
        /** effects: every rule, in the book's order. */
        public List<Rule> all() { return all; }
        /** effects: the book reduced to the rules {@code keep} accepts. */
        public Rules only(Predicate<Rule> keep) { return of(all.stream().filter(keep).toList()); }
        public int size() { return all.size(); }
        @Override public String toString() { return "Rules(" + all.size() + ")"; }
    }

    /** A step to perform: {@code rule} {@code times} times, with {@code picks} the item chosen for
     * each of the rule's cells, the same for every one of those crafts. Immutable.
     * RI: times &ge; 1; one pick per cell, each among its cell's options. */
    public record Step(Rule rule, int times, List<String> picks) {
        public Step {
            Objects.requireNonNull(rule);
            if (times < 1) throw new IllegalArgumentException("times");
            picks = List.copyOf(picks);
            if (picks.size() != rule.cells().size()) throw new IllegalArgumentException("picks");
            for (int i = 0; i < picks.size(); i++) if (!rule.cells().get(i).contains(picks.get(i))) throw new IllegalArgumentException("pick " + picks.get(i));
        }
        /** effects: how many of the result the step makes. */
        public int makes() { return times * rule.yield(); }
        /** effects: how many of each pick one craft takes, in the picks' order. */
        public Map<String, Integer> perCraft() {
            var out = new LinkedHashMap<String, Integer>();
            for (var p : picks) out.merge(p, 1, Integer::sum);
            return out;
        }
        /** effects: this step done {@code n} times instead. requires: n &ge; 1. */
        public Step times(int n) { return new Step(rule, n, picks); }
    }

    /** The answer for one request: the steps to perform, leaves first (every step's picks are on
     * hand or made by an earlier step); {@code picks} the item chosen per top-level cell, meaningful
     * only when covered; {@code shortages} what could neither be found nor made, merged per distinct
     * option list in the recipe's order, empty exactly when the request is covered. Immutable. */
    public record Plan(List<Step> steps, List<String> picks, List<Shortage> shortages) {
        public Plan { steps = List.copyOf(steps); picks = List.copyOf(picks); shortages = List.copyOf(shortages); }
        public boolean covered() { return shortages.isEmpty(); }
    }
    private Recipes() {}

    /** requires: need &ge; 0; counts in {@code available} non-negative; depth &ge; 0.
     * effects: the plan for making {@code need} more of {@code item}: by the first of its rules
     * whose cells can be covered from {@code available} and what {@code rules} can make of it,
     * as many times as its yield needs, sub-steps nested at most {@code depth} deep counting this
     * one (0: nothing can be made). The item is on the chain from the start, so nothing below
     * spends it or makes it again. Covered with no steps when need is 0. When no rule covers it,
     * the shortages of the first rule tried; when no rule makes it at all, short of the item
     * itself. {@code available} is not modified. */
    public static Plan making(String item, int need, Map<String, Integer> available, Rules rules, int depth) {
        Objects.requireNonNull(item, "item");
        if (need < 0) throw new IllegalArgumentException("need");
        if (depth < 0) throw new IllegalArgumentException("depth");
        if (need == 0) return new Plan(List.of(), List.of(), List.of());
        Plan first = null;
        if (depth > 0) for (var rule : rules.making(item)) {
            int times = (need + rule.yield() - 1) / rule.yield();
            var p = plan(item, rule.cells(), times, available, rules, depth - 1);
            if (p.covered()) {
                var steps = new ArrayList<>(p.steps());
                steps.add(new Step(rule, times, p.picks()));
                return new Plan(merged(steps), p.picks(), List.of());
            }
            if (first == null) first = p;
        }
        if (first == null) return new Plan(List.of(), List.of(), List.of(new Shortage(List.of(item), need)));
        return new Plan(List.of(), List.of(), first.shortages());
    }

    /** requires: result non-null; no cell empty; crafts &ge; 0; counts in {@code available}
     * non-negative; depth &ge; 0;
     * effects: the plan for {@code crafts} crafts of a recipe that makes {@code result} with the
     * given cells, from {@code available} and what {@code rules} can make of it, sub-crafts nested
     * at most {@code depth} deep (0: nothing is made). Each cell takes the first of its options of
     * which {@code crafts} are on hand, or else the first that can be made up to that number; an
     * item is made by the first of its rules whose cells can in turn be covered, as many times as
     * its yield needs. The items being made form a chain from {@code result} down: no item on the
     * chain is made again below itself, and no cell takes an item that is on the chain above the
     * rule it belongs to, from what is on hand or by making it (an ingot is not broken into nuggets
     * to make the ingot). A rule may still spend its own result from what is on hand. A cell that
     * fails is reported short by the crafts its best option cannot cover, and the plan goes on to
     * the next cell so every shortage is named. {@code available} is not modified. */
    public static Plan plan(String result, List<List<String>> cells, int crafts, Map<String, Integer> available, Rules rules, int depth) {
        Objects.requireNonNull(result, "result");
        if (crafts < 0) throw new IllegalArgumentException("crafts");
        if (depth < 0) throw new IllegalArgumentException("depth");
        Objects.requireNonNull(rules);
        var left = new HashMap<String, Integer>();
        available.forEach((k, n) -> { if (n < 0) throw new IllegalArgumentException("available"); left.put(k, n); });
        var steps = new ArrayList<Step>();
        var picks = new ArrayList<String>();
        var shortages = new LinkedHashMap<List<String>, Integer>();
        var chain = new ArrayList<String>(List.of(result));
        for (var cell : cells) {
            if (cell.isEmpty()) throw new IllegalArgumentException("cell");
            if (crafts == 0) { picks.add(cell.get(0)); continue; }
            var pick = cover(cell, crafts, left, steps, rules, depth, chain);
            if (pick == null) {
                int best = 0;
                for (var option : cell) best = Math.max(best, left.getOrDefault(option, 0));
                shortages.merge(List.copyOf(cell), crafts - best, Integer::sum);
                picks.add(cell.get(0));
            } else picks.add(pick);
        }
        var out = new ArrayList<Shortage>();
        shortages.forEach((options, missing) -> out.add(new Shortage(options, missing)));
        return new Plan(merged(steps), picks, out);
    }

    /** effects: the steps with each run of neighbours that perform the same rule with the same
     * picks folded into one, except a rule that consumes its own result, whose crafts must stay in
     * order. */
    private static List<Step> merged(List<Step> steps) {
        var out = new ArrayList<Step>(steps.size());
        for (var s : steps) {
            var last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.rule().equals(s.rule()) && last.picks().equals(s.picks()) && !s.picks().contains(s.rule().result()))
                out.set(out.size() - 1, new Step(s.rule(), last.times() + s.times(), s.picks()));
            else out.add(s);
        }
        return out;
    }

    /** requires: chain is the items being made, the request's result first and the result of the
     * rule this cell belongs to last; effects: the option chosen for the cell, with {@code left}
     * debited by {@code n} of it and {@code steps} extended by whatever was made; or null with both
     * untouched. An option on the chain above the cell's rule is never chosen. */
    private static String cover(List<String> cell, int n, Map<String, Integer> left, List<Step> steps, Rules rules, int depth, List<String> chain) {
        for (var option : cell) {
            int at = chain.indexOf(option);
            if (at >= 0 && at < chain.size() - 1) continue;
            int have = left.getOrDefault(option, 0);
            if (have >= n) { left.put(option, have - n); return option; }
            var before = new HashMap<>(left);
            int stepsBefore = steps.size();
            if (make(option, n - have, left, steps, rules, depth, chain)) { left.merge(option, -n, Integer::sum); return option; }
            left.clear(); left.putAll(before);
            while (steps.size() > stepsBefore) steps.remove(steps.size() - 1);
        }
        return null;
    }

    /** effects: makes at least {@code need} of the item by the first of its rules whose cells can
     * be covered (recursively, one level shallower, with the item at the end of the chain), adding
     * what was made to {@code left} and the step after its sub-steps to {@code steps}; false with
     * both untouched when none can, the depth is spent, or the item is already on the chain. */
    private static boolean make(String item, int need, Map<String, Integer> left, List<Step> steps, Rules rules, int depth, List<String> chain) {
        if (depth <= 0 || chain.contains(item)) return false;
        for (var rule : rules.making(item)) {
            int times = (need + rule.yield() - 1) / rule.yield();
            var before = new HashMap<>(left);
            int stepsBefore = steps.size();
            var picks = new ArrayList<String>(rule.cells().size());
            boolean ok = true;
            chain.add(item);
            for (var cell : rule.cells()) {
                var pick = cover(cell, times, left, steps, rules, depth - 1, chain);
                if (pick == null) { ok = false; break; }
                picks.add(pick);
            }
            chain.remove(chain.size() - 1);
            if (ok) { steps.add(new Step(rule, times, picks)); left.merge(item, times * rule.yield(), Integer::sum); return true; }
            left.clear(); left.putAll(before);
            while (steps.size() > stepsBefore) steps.remove(steps.size() - 1);
        }
        return false;
    }

    private static List<List<String>> copyCells(List<List<String>> cells) {
        var out = new ArrayList<List<String>>(cells.size());
        for (var cell : cells) { if (cell.isEmpty()) throw new IllegalArgumentException("cell"); out.add(List.copyOf(cell)); }
        return List.copyOf(out);
    }
}
