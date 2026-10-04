/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A woodcutter's tree (D-0001): which logs make a natural tree, the order to fell them, the leaves
 * that go with it and where to replant.
 *
 * <p>A tree is natural when its base log stands on soil, the logs connected to the base (by face,
 * edge or corner, of the base's kind, none placed by a player) number at most {@link #MAX_LOGS},
 * and one of its highest logs touches a natural leaf. Player builds and village houses' log
 * pillars fail one of those. The leaves that go with it are exactly those vanilla would let decay
 * once the logs are gone: natural leaves no longer within {@link #LEAF_REACH} of a remaining log
 * along a path of leaves. */
public final class Felling {
    public static final int MAX_LOGS = 512;
    /** Vanilla's leaf distance: a leaf further than this from every log decays. */
    public static final int LEAF_REACH = 6;
    /** The most leaves looked at for one tree; a canopy past this keeps its leaves, to decay as
     * vanilla's do. */
    public static final int MAX_LEAVES_SEEN = 32768;
    private Felling() {}

    public enum Kind {
        /** A log no player placed. */
        LOG,
        /** A log a player placed: never part of a tree, but it feeds leaves like any log. */
        PLACED_LOG,
        /** Leaves that decay away from logs. */
        LEAVES,
        /** Leaves that never decay (placed by a player): they pass on distance, they are never cleared. */
        PERSISTENT_LEAVES,
        /** Ground a tree grows from ({@code #minecraft:dirt}). */
        SOIL,
        OTHER
    }

    /** One block as the woodcutter sees it; {@code species} names the log's kind and is "" for
     * everything else. */
    public record Block(Kind kind, String species) {
        public static final Block OTHER = new Block(Kind.OTHER, "");
        public Block { Objects.requireNonNull(kind); Objects.requireNonNull(species); }
        public static Block of(Kind kind) { return new Block(kind, ""); }
        public boolean isLog() { return kind == Kind.LOG || kind == Kind.PLACED_LOG; }
        public boolean isLeaves() { return kind == Kind.LEAVES || kind == Kind.PERSISTENT_LEAVES; }
    }

    /** The world as the woodcutter reads it. */
    public interface Forest { Block at(Cell cell); }

    /** A tree to fell: its logs top first, the leaves that go with it top first, and the base
     * cells to replant, all disjoint. */
    public record Tree(Cell base, String species, List<Cell> logs, List<Cell> leaves, List<Cell> replant) {
        public Tree { logs = List.copyOf(logs); leaves = List.copyOf(leaves); replant = List.copyOf(replant); }
    }

    /** requires: maxDepth &ge; 0.
     * effects: the trunk base under {@code log}: the lowest of the logs of its kind straight
     * below it, at most {@code maxDepth} down, when that one stands on soil; empty otherwise. */
    public static Optional<Cell> baseBelow(Forest forest, Cell log, int maxDepth) {
        var at = forest.at(log);
        if (at.kind() != Kind.LOG) return Optional.empty();
        var cell = log;
        for (int i = 0; i < maxDepth; i++) {
            var below = forest.at(cell.below());
            if (below.kind() == Kind.LOG && below.species().equals(at.species())) cell = cell.below();
            else break;
        }
        return forest.at(cell.below()).kind() == Kind.SOIL ? Optional.of(cell) : Optional.empty();
    }

    /** effects: the natural tree standing on {@code base}, or empty when {@code base} is no natural
     * tree's base log. */
    public static Optional<Tree> tree(Forest forest, Cell base) {
        var root = forest.at(base);
        if (root.kind() != Kind.LOG || forest.at(base.below()).kind() != Kind.SOIL) return Optional.empty();
        var logs = connectedLogs(forest, base, root.species());
        if (logs == null) return Optional.empty();
        int top = logs.stream().mapToInt(Cell::y).max().orElseThrow();
        boolean crowned = logs.stream().filter(c -> c.y() == top).anyMatch(c -> touches(forest, c, Kind.LEAVES));
        if (!crowned) return Optional.empty();
        var replant = logs.stream().filter(c -> c.y() == base.y() && forest.at(c.below()).kind() == Kind.SOIL).sorted(ORDER).toList();
        var fell = logs.stream().sorted(Comparator.comparingInt(Cell::y).reversed()
                .thenComparingLong(c -> c.distanceSq(base)).thenComparing(ORDER)).toList();
        var leaves = doomedLeaves(forest, logs, MAX_LEAVES_SEEN).stream().sorted(Comparator.comparingInt(Cell::y).reversed()
                .thenComparingLong(c -> c.distanceSq(base)).thenComparing(ORDER)).toList();
        return Optional.of(new Tree(base, root.species(), fell, leaves, replant));
    }

    private static final Comparator<Cell> ORDER = Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z);

    /** effects: the logs of {@code species} connected to {@code base} by face, edge or corner, no
     * player's among them; null past {@link #MAX_LOGS}. */
    private static Set<Cell> connectedLogs(Forest forest, Cell base, String species) {
        var seen = new HashSet<Cell>();
        var queue = new ArrayDeque<Cell>();
        seen.add(base);
        queue.add(base);
        while (!queue.isEmpty()) {
            var c = queue.poll();
            for (int[] d : Cell.AROUND) {
                var n = c.offset(d[0], d[1], d[2]);
                if (seen.contains(n)) continue;
                var b = forest.at(n);
                if (b.kind() != Kind.LOG || !b.species().equals(species)) continue;
                seen.add(n);
                if (seen.size() > MAX_LOGS) return null;
                queue.add(n);
            }
        }
        return seen;
    }

    private static boolean touches(Forest forest, Cell c, Kind kind) {
        for (int[] d : Cell.FACES) if (forest.at(c.offset(d[0], d[1], d[2])).kind() == kind) return true;
        return false;
    }

    /** effects: the natural leaves within {@link #LEAF_REACH} of {@code logs} along leaves that are
     * further than that from every other log once {@code logs} are gone; empty when more than
     * {@code limit} leaves would have to be looked at. A leaf that could be fed by a remaining log
     * lies within twice the reach of {@code logs}, so nothing further is looked at. */
    static Set<Cell> doomedLeaves(Forest forest, Set<Cell> logs, int limit) {
        var fromTree = new HashMap<Cell, Integer>();
        var queue = new ArrayDeque<Cell>();
        for (var log : logs) for (int[] d : Cell.FACES) {
            var n = log.offset(d[0], d[1], d[2]);
            if (!fromTree.containsKey(n) && forest.at(n).isLeaves()) { fromTree.put(n, 1); queue.add(n); }
        }
        var remaining = new ArrayDeque<Cell>();
        var fromRest = new HashMap<Cell, Integer>();
        while (!queue.isEmpty()) {
            var c = queue.poll();
            int depth = fromTree.get(c);
            for (int[] d : Cell.FACES) {
                var n = c.offset(d[0], d[1], d[2]);
                var b = forest.at(n);
                if (b.isLog() && !logs.contains(n)) {
                    if (!fromRest.containsKey(c)) { fromRest.put(c, 1); remaining.add(c); }
                } else if (b.isLeaves() && !fromTree.containsKey(n) && depth < 2 * LEAF_REACH) {
                    fromTree.put(n, depth + 1);
                    if (fromTree.size() > limit) return Set.of();
                    queue.add(n);
                }
            }
        }
        while (!remaining.isEmpty()) {
            var c = remaining.poll();
            int depth = fromRest.get(c);
            if (depth >= LEAF_REACH) continue;
            for (int[] d : Cell.FACES) {
                var n = c.offset(d[0], d[1], d[2]);
                if (fromTree.containsKey(n) && !fromRest.containsKey(n)) { fromRest.put(n, depth + 1); remaining.add(n); }
            }
        }
        var doomed = new HashSet<Cell>();
        for (Map.Entry<Cell, Integer> e : fromTree.entrySet())
            if (e.getValue() <= LEAF_REACH && !fromRest.containsKey(e.getKey()) && forest.at(e.getKey()).kind() == Kind.LEAVES)
                doomed.add(e.getKey());
        return doomed;
    }
}
