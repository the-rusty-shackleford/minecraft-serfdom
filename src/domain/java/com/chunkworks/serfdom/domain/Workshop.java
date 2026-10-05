/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.*;

/** What a workshop worker (a cook or a blacksmith) does next (D-0002), asked between tasks. In
 * order: collect a station whose load is done; the stock list's rows, in order, each by the first
 * step of its plan that can run now; charcoal when fuel runs low; raw metal into ingots; a repair;
 * else rest, showing what the list lacks. */
public final class Workshop {
    /** How deep a plan nests what it makes to make what it needs. */
    public static final int DEPTH = 8;
    /** Logs burnt under a charcoal bootstrap of {@link Fuel#BOOTSTRAP}: 2 logs burn 600 ticks, three
     * furnace items. */
    public static final int BOOTSTRAP_FUEL = 2;
    private Workshop() {}

    /** The work a job does beyond its list. */
    public enum Duty {
        /** Logs into charcoal when fuel runs low. */
        CHARCOAL("charcoal"),
        /** Raw metal into ingots. */
        RAW_METAL("raw_metal"),
        /** Worn gear mended at an anvil. */
        REPAIR("repair");
        private final String name;
        Duty(String name) { this.name = name; }
        public String named() { return name; }
        /** effects: the duty a job file names, ignoring case. */
        public static Optional<Duty> named(String name) { return Arrays.stream(values()).filter(d -> d.name.equalsIgnoreCase(name)).findFirst(); }
    }

    /** A station as a worker finds it. */
    public enum State {
        /** Empty and free to use. */
        FREE,
        /** Cooking what this post loaded, or in another worker's hands. */
        BUSY,
        /** Holding a finished load of this post's, or one stalled without fuel. */
        READY,
        /** Unusable: a player's things in it, unlit, or cold. */
        OFF
    }

    /** One station in the area: where, what kind, its state, and how many items a load may hold.
     * Immutable. RI: room &ge; 0. */
    public record Site(Cell at, Station kind, State state, int room) {
        public Site {
            Objects.requireNonNull(at); Objects.requireNonNull(kind); Objects.requireNonNull(state);
            if (room < 0) throw new IllegalArgumentException("room " + room);
        }
    }

    /** What the worker knows when it chooses, read off the post's chests and stations.
     * @param stock the post's list
     * @param stored item counts in the post's chests
     * @param coming item counts on their way: in stations this post loaded, in other workers' hands
     * @param stations the area's stations, nearest the post first
     * @param rules the job's rule book: every rule at any of its stations
     * @param duties the job's duties
     * @param fuel fuel items in the chests
     * @param fuelComing fuel items on their way (charcoal cooking)
     * @param log the burnable log the chests hold most of, if any
     * @param raw the raw metals in the chests
     * @param worn the worn items in the chests, with their stored material
     * @param knife whether a knife is held or stored */
    public record Facts(Stock stock, Map<String, Integer> stored, Map<String, Integer> coming, List<Site> stations, Recipes.Rules rules,
                        Set<Duty> duties, int fuel, int fuelComing, Optional<String> log, List<String> raw, List<Repair.Worn> worn, boolean knife) {
        public Facts {
            stored = Map.copyOf(stored); coming = Map.copyOf(coming); stations = List.copyOf(stations);
            duties = duties.isEmpty() ? Set.of() : Set.copyOf(duties); raw = List.copyOf(raw); worn = List.copyOf(worn);
            Objects.requireNonNull(stock); Objects.requireNonNull(rules); Objects.requireNonNull(log);
            if (fuel < 0 || fuelComing < 0) throw new IllegalArgumentException("fuel");
        }
        /** effects: stored and coming counts added together. */
        Map<String, Integer> counted() {
            var out = new HashMap<>(stored);
            coming.forEach((k, n) -> out.merge(k, n, Integer::sum));
            return out;
        }
        /** effects: the station kinds a worker can use here: those with a station not OFF. */
        Set<Station> usable() {
            var out = EnumSet.noneOf(Station.class);
            for (var s : stations) if (s.state() != State.OFF) out.add(s.kind());
            return out;
        }
        /** effects: the first free station of the kind. */
        Optional<Site> free(Station kind) {
            return stations.stream().filter(s -> s.kind() == kind && s.state() == State.FREE).findFirst();
        }
    }

    /** What the worker does. */
    public sealed interface Choice permits Collect, Load, Make, Mend, Rest {}
    /** Take the finished load (and anything stalled) out of the station at {@code at}. */
    public record Collect(Cell at) implements Choice {}
    /** Fetch {@code step}'s picks for {@code step.times()} items and load them, with fuel when the
     * station burns it (logs when {@code logFuel}), into the station at {@code at}. */
    public record Load(Cell at, Recipes.Step step, boolean logFuel) implements Choice {}
    /** Fetch the picks and make {@code step} there and then at the station at {@code at}. */
    public record Make(Cell at, Recipes.Step step) implements Choice {}
    /** Fetch worn item {@code worn} of the facts and its material and mend it at the anvil at
     * {@code at}. */
    public record Mend(Cell at, int worn) implements Choice {}
    /** Nothing to do now; the need shows what is lacking, empty when nothing is. */
    public record Rest(Optional<Need> need) implements Choice {}

    /** Where a row stands. */
    public enum Status {
        /** As many stored and on their way as it keeps. */
        MET,
        /** Being made, or waiting on what is being made or on a busy station. */
        MAKING,
        /** Short of materials. */
        SHORT,
        /** Makeable, but not with the stations in the area that can be used. */
        NO_STATION,
        /** Waiting on fuel. */
        NO_FUEL,
        /** Waiting on a knife. */
        NO_TOOL
    }

    /** A row's standing: its status, how many are stored or on their way, what it is short of
     * (SHORT), and the stations it lacks (NO_STATION). Immutable. */
    public record RowState(Status status, int have, List<Recipes.Shortage> shortages, Set<Station> missing) {
        public RowState { shortages = List.copyOf(shortages); missing = missing.isEmpty() ? Set.of() : Set.copyOf(missing); }
        static RowState of(Status status, int have) { return new RowState(status, have, List.of(), Set.of()); }
        /** effects: the need this standing shows, if any. */
        public Optional<Need> need() {
            return switch (status) {
                case MET, MAKING -> Optional.empty();
                case SHORT -> Optional.of(Need.NO_MATERIALS);
                case NO_STATION -> Optional.of(Need.NO_STATION);
                case NO_FUEL -> Optional.of(Need.NO_FUEL);
                case NO_TOOL -> Optional.of(Need.NO_TOOL);
            };
        }
    }

    private record Evaluated(RowState state, Optional<Choice> choice) {}

    /** effects: every row's standing, in the list's order. */
    public static List<RowState> rows(Facts f) {
        var out = new ArrayList<RowState>(f.stock().rows().size());
        var counted = f.counted();
        var book = f.rules().only(r -> f.usable().contains(r.station()));
        for (int i = 0; i < f.stock().rows().size(); i++) out.add(evaluate(f, i, counted, book).state());
        return out;
    }

    /** effects: what the worker does next, by the first rule that applies:
     * <ol>
     * <li>a READY station is collected;</li>
     * <li>each row in order, while short: its plan over the usable stations; the first step whose
     *     picks are in the chests (as far as other rows' keeps allow), at a free station of its
     *     kind or of another kind with a rule making the same from the same picks, with fuel for a
     *     station that burns and a knife for a cutting board;</li>
     * <li>charcoal (a duty) when fuel stored and coming is under {@link Fuel#LOW} or a row waits on
     *     fuel, unless none is stored and some is on its way: logs into a free station that burns,
     *     by the first rule that takes them;</li>
     * <li>raw metal (a duty): each in turn into a free station that burns, by the first rule that
     *     takes it, with fuel stored;</li>
     * <li>a repair (a duty): {@link Repair#first} at a free anvil;</li>
     * <li>rest, showing the first need of the rows and duties ({@link Need#shown}).</li>
     * </ol> */
    public static Choice next(Facts f) {
        for (var s : f.stations()) if (s.state() == State.READY) return new Collect(s.at());
        var counted = f.counted();
        var usable = f.usable();
        var book = f.rules().only(r -> usable.contains(r.station()));
        var needs = EnumSet.noneOf(Need.class);
        boolean fuelWanted = false;
        for (int i = 0; i < f.stock().rows().size(); i++) {
            var e = evaluate(f, i, counted, book);
            if (e.choice().isPresent()) return e.choice().get();
            e.state().need().ifPresent(needs::add);
            fuelWanted |= e.state().status() == Status.NO_FUEL;
        }
        var spare = f.stock().spare(f.stored());
        // With nothing stored but charcoal on its way, wait for it rather than burn logs to start
        // another fire.
        boolean waiting = f.fuel() == 0 && f.fuelComing() > 0;
        if (f.duties().contains(Duty.CHARCOAL) && f.log().isPresent() && !waiting && (f.fuel() + f.fuelComing() < Fuel.LOW || fuelWanted)) {
            var log = f.log().get();
            int load = Fuel.charcoalLoad(spare.getOrDefault(log, 0), f.fuel(), BOOTSTRAP_FUEL);
            if (load > 0) {
                var at = heat(f, book, log, load);
                if (at.isPresent()) return new Load(at.get().site().at(), at.get().step(), f.fuel() == 0);
            }
        }
        if (f.duties().contains(Duty.RAW_METAL)) for (var raw : f.raw()) {
            int n = spare.getOrDefault(raw, 0);
            if (n < 1) continue;
            if (f.fuel() == 0) { if (book.all().stream().anyMatch(r -> r.station().burns() && takes(r, raw))) needs.add(Need.NO_FUEL); continue; }
            var at = heat(f, book, raw, n);
            if (at.isPresent()) return new Load(at.get().site().at(), at.get().step(), false);
        }
        if (f.duties().contains(Duty.REPAIR)) {
            var first = Repair.first(f.worn());
            var anvil = f.free(Station.ANVIL);
            if (first.isPresent() && anvil.isPresent()) return new Mend(anvil.get().at(), first.get());
        }
        return new Rest(Need.shown(needs));
    }

    private record Heat(Site site, Recipes.Step step) {}

    /** effects: the first rule in the book's order that burns fuel and takes {@code item} alone,
     * with a free station of its kind, as a load of up to {@code n} into that station. */
    private static Optional<Heat> heat(Facts f, Recipes.Rules book, String item, int n) {
        for (var r : book.all()) {
            if (!r.station().burns() || !takes(r, item)) continue;
            var spot = f.free(r.station());
            if (spot.isPresent() && spot.get().room() > 0) return Optional.of(new Heat(spot.get(), new Recipes.Step(r, Math.min(n, spot.get().room()), List.of(item))));
        }
        return Optional.empty();
    }

    /** effects: true iff the rule's cells, one for one, accept {@code picks}. */
    private static boolean accepts(Recipes.Rule r, List<String> picks) {
        if (r.cells().size() != picks.size()) return false;
        for (int c = 0; c < picks.size(); c++) if (!r.cells().get(c).contains(picks.get(c))) return false;
        return true;
    }

    /** effects: true iff the rule has one cell and it accepts {@code item}. */
    private static boolean takes(Recipes.Rule r, String item) { return r.cells().size() == 1 && r.cells().get(0).contains(item); }

    private static Evaluated evaluate(Facts f, int i, Map<String, Integer> counted, Recipes.Rules book) {
        var row = f.stock().rows().get(i);
        int have = counted.getOrDefault(row.item(), 0);
        int wanted = Stock.shortBy(row, f.stored(), f.coming());
        if (wanted == 0) return new Evaluated(RowState.of(Status.MET, have), Optional.empty());
        var spendable = f.stock().spendable(i, counted);
        var plan = Recipes.making(row.item(), wanted, spendable, book, DEPTH);
        if (!plan.covered()) {
            var whole = Recipes.making(row.item(), wanted, spendable, f.rules(), DEPTH);
            if (whole.covered()) {
                // Every kind that could do a step no usable station can: a smelt names the blast
                // furnace and the furnace alike.
                var missing = EnumSet.noneOf(Station.class);
                var usable = f.usable();
                for (var s : whole.steps()) {
                    if (usable.contains(s.rule().station())) continue;
                    for (var r : f.rules().making(s.rule().result())) if (accepts(r, s.picks())) missing.add(r.station());
                }
                return new Evaluated(new RowState(Status.NO_STATION, have, List.of(), missing), Optional.empty());
            }
            return new Evaluated(new RowState(Status.SHORT, have, plan.shortages(), Set.of()), Optional.empty());
        }
        var inChests = f.stock().spendable(i, f.stored());
        Status blocked = null;
        for (var step : plan.steps()) {
            int n = step.times();
            for (var e : step.perCraft().entrySet()) n = Math.min(n, inChests.getOrDefault(e.getKey(), 0) / e.getValue());
            if (n < 1) continue;
            var kind = step.rule().station();
            if (kind.burns() && f.fuel() == 0) { blocked = Status.NO_FUEL; continue; }
            if (kind == Station.BOARD && !f.knife()) { if (blocked == null) blocked = Status.NO_TOOL; continue; }
            var at = f.free(kind);
            var rule = step.rule();
            if (at.isEmpty()) {
                var alt = alternative(f, book, step);
                if (alt.isEmpty()) continue;
                at = Optional.of(alt.get().site());
                rule = alt.get().step().rule();
                kind = rule.station();
            }
            var doing = new Recipes.Step(rule, kind.cooks() ? Math.min(n, at.get().room()) : n, step.picks());
            if (doing.times() < 1) continue;
            Choice choice = kind.cooks() ? new Load(at.get().at(), doing, false) : new Make(at.get().at(), doing);
            return new Evaluated(RowState.of(Status.MAKING, have), Optional.of(choice));
        }
        return new Evaluated(blocked == null ? RowState.of(Status.MAKING, have) : RowState.of(blocked, have), Optional.empty());
    }

    /** effects: another rule in the book making the same as {@code step}'s, from the same picks,
     * at a free station of another kind that needs nothing the facts lack (fuel, a knife). */
    private static Optional<Heat> alternative(Facts f, Recipes.Rules book, Recipes.Step step) {
        for (var r : book.making(step.rule().result())) {
            if (r.station() == step.rule().station() || r.yield() != step.rule().yield() || !accepts(r, step.picks())) continue;
            if (r.station().burns() && f.fuel() == 0) continue;
            if (r.station() == Station.BOARD && !f.knife()) continue;
            var spot = f.free(r.station());
            if (spot.isPresent()) return Optional.of(new Heat(spot.get(), new Recipes.Step(r, step.times(), step.picks())));
        }
        return Optional.empty();
    }
}
