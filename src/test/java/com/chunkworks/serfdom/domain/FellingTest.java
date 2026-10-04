/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Felling.Block;
import com.chunkworks.serfdom.domain.Felling.Kind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: a lone oak; a log pillar with no leaves; a crowned pillar on stone; a placed log in
 * the trunk; a placed log feeding leaves; two canopies interlocked; a 2&times;2 trunk with a
 * diagonal branch; another species touching the trunk; a block of logs past the limit; leaves at
 * exactly the reach and one past it; persistent leaves passing on distance and never cleared; a
 * canopy past the looking limit; baseBelow from the base, mid-trunk, over stone, from a non-log,
 * past its depth. */
final class FellingTest {
    static final Block OAK = new Block(Kind.LOG, "oak");
    static final Block BIRCH = new Block(Kind.LOG, "birch");
    static final Block PLACED = new Block(Kind.PLACED_LOG, "oak");
    static final Block LEAF = Block.of(Kind.LEAVES);
    static final Block KEPT_LEAF = Block.of(Kind.PERSISTENT_LEAVES);
    static final Block SOIL = Block.of(Kind.SOIL);

    /** A world of blocks set by hand; everything else is OTHER. */
    static final class World implements Felling.Forest {
        final Map<Cell, Block> blocks = new HashMap<>();
        public Block at(Cell c) { return blocks.getOrDefault(c, Block.OTHER); }
        World set(int x, int y, int z, Block b) { blocks.put(new Cell(x, y, z), b); return this; }
        World ground(int from, int to) { for (int x = from; x <= to; x++) for (int z = from; z <= to; z++) set(x, 63, z, SOIL); return this; }
        World trunk(int x, int z, int from, int to, Block log) { for (int y = from; y <= to; y++) set(x, y, z, log); return this; }
        /** A cube of leaves of half-width r around (x, y, z), leaving logs in place. */
        World crown(int x, int y, int z, int r, Block leaf) {
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
                var c = new Cell(x + dx, y + dy, z + dz);
                if (!at(c).isLog()) blocks.put(c, leaf);
            }
            return this;
        }
    }
    static Cell c(int x, int y, int z) { return new Cell(x, y, z); }
    static Set<Cell> cells(List<Cell> list) { return new HashSet<>(list); }

    @Test void aLoneOakIsFelledTopDownWithAllItsLeaves() {
        var w = new World().ground(-8, 8).trunk(0, 0, 64, 68, OAK).crown(0, 67, 0, 2, LEAF);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertEquals(List.of(c(0, 68, 0), c(0, 67, 0), c(0, 66, 0), c(0, 65, 0), c(0, 64, 0)), tree.logs());
        assertEquals(List.of(c(0, 64, 0)), tree.replant());
        assertEquals("oak", tree.species());
        assertEquals(5 * 5 * 5 - 4, tree.leaves().size(), "the whole crown but the four logs inside it");
        for (int i = 1; i < tree.leaves().size(); i++) assertTrue(tree.leaves().get(i - 1).y() >= tree.leaves().get(i).y(), "leaves top first");
    }
    @Test void aPillarWithoutLeavesIsNoTree() {
        var w = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK);
        assertEquals(Optional.empty(), Felling.tree(w, c(0, 64, 0)));
    }
    @Test void aTreeOnStoneIsNoTree() {
        var w = new World().trunk(0, 0, 64, 68, OAK).crown(0, 67, 0, 2, LEAF);
        assertEquals(Optional.empty(), Felling.tree(w, c(0, 64, 0)), "the base must stand on soil: a house's pillar on cobblestone");
    }
    @Test void aPlacedLogCutsTheTrunk() {
        var w = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK).set(0, 65, 0, PLACED).crown(0, 68, 0, 1, LEAF);
        assertEquals(Optional.empty(), Felling.tree(w, c(0, 64, 0)), "the log above the base is a player's: the base alone is no tree");
    }
    @Test void aPlacedLogKeepsTheLeavesItFeeds() {
        var w = new World().ground(-8, 8).trunk(0, 0, 64, 68, OAK).crown(0, 67, 0, 2, LEAF).set(3, 67, 0, PLACED);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertFalse(tree.logs().contains(c(3, 67, 0)));
        assertFalse(tree.leaves().contains(c(2, 67, 0)), "touching the player's log: it lives");
        assertTrue(tree.leaves().contains(c(-2, 65, -2)), "9 from the player's log along the leaves: it goes");
    }
    @Test void interlockedCanopiesKeepTheNeighboursLeaves() {
        var w = new World().ground(-8, 16).trunk(0, 0, 64, 68, OAK).crown(0, 67, 0, 2, LEAF)
                .trunk(5, 0, 64, 68, OAK).crown(5, 67, 0, 2, LEAF);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertEquals(5, tree.logs().size(), "the trunks are 5 apart: two trees");
        assertTrue(tree.leaves().stream().noneMatch(l -> l.x() >= 3), "every leaf from x 3 on is within reach of the other trunk");
        assertFalse(tree.leaves().contains(c(2, 67, 0)), "3 from the other trunk");
        assertTrue(tree.leaves().contains(c(-2, 68, -2)), "9 from the other trunk, around the felled one");
    }
    @Test void aTwoByTwoTrunkReplantsFourAndTakesItsDiagonalBranch() {
        var w = new World().ground(-8, 8);
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) w.trunk(x, z, 64, 69, OAK);
        w.set(2, 70, 2, OAK).crown(2, 71, 2, 1, LEAF).crown(0, 70, 0, 1, LEAF);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertEquals(List.of(c(0, 64, 0), c(0, 64, 1), c(1, 64, 0), c(1, 64, 1)), tree.replant());
        assertTrue(tree.logs().contains(c(2, 70, 2)), "the branch touches the trunk only at a corner");
        assertEquals(c(2, 70, 2), tree.logs().getFirst(), "the highest log comes first");
        assertEquals(25, tree.logs().size());
    }
    @Test void anotherSpeciesIsAnotherTree() {
        var w = new World().ground(-4, 4).trunk(0, 0, 64, 68, OAK).crown(0, 67, 0, 2, LEAF).trunk(1, 0, 64, 64, BIRCH);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertFalse(tree.logs().contains(c(1, 64, 0)));
    }
    @Test void tooManyLogsIsABuildingNotATree() {
        var w = new World().ground(-8, 8);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) w.trunk(x, z, 64, 70, OAK);
        w.crown(4, 71, 4, 1, LEAF);
        assertTrue(9 * 9 * 7 > Felling.MAX_LOGS);
        assertEquals(Optional.empty(), Felling.tree(w, c(0, 64, 0)));
    }
    @Test void leavesDieExactlyPastTheReachOfTheRemainingLogs() {
        // A line of leaves from the oak's top (x 1) to a birch log at x 11. After felling, a leaf
        // at x is 11 - x from the birch: it lives at 6 or closer, so x 1 to 4 die.
        var w = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK).set(11, 68, 0, BIRCH);
        for (int x = 1; x <= 10; x++) w.set(x, 68, 0, LEAF);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertEquals(Set.of(c(1, 68, 0), c(2, 68, 0), c(3, 68, 0), c(4, 68, 0)), cells(tree.leaves()));
    }
    @Test void leavesPastTheTreesReachAreNotItsToClear() {
        // The same line with no birch: only the six within the oak's own reach go with it; the
        // rest were never fed by it and are left as they are.
        var w = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK);
        for (int x = 1; x <= 10; x++) w.set(x, 68, 0, LEAF);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertEquals(Set.of(c(1, 68, 0), c(2, 68, 0), c(3, 68, 0), c(4, 68, 0), c(5, 68, 0), c(6, 68, 0)), cells(tree.leaves()));
    }
    @Test void persistentLeavesPassOnDistanceAndStay() {
        var w = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK).set(1, 68, 0, LEAF).set(2, 68, 0, KEPT_LEAF).set(3, 68, 0, LEAF).set(4, 68, 0, BIRCH);
        var tree = Felling.tree(w, c(0, 64, 0)).orElseThrow();
        assertEquals(List.of(), tree.leaves(), "x 1 is 3 from the birch through the player's leaf; x 2 is never cleared");
        var lone = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK).set(1, 68, 0, LEAF).set(2, 68, 0, KEPT_LEAF);
        assertEquals(List.of(c(1, 68, 0)), Felling.tree(lone, c(0, 64, 0)).orElseThrow().leaves());
    }
    @Test void aCanopyPastTheLimitIsLeftToDecay() {
        var w = new World().ground(-8, 8).trunk(0, 0, 64, 68, OAK).crown(0, 67, 0, 2, LEAF);
        var logs = Set.of(c(0, 64, 0), c(0, 65, 0), c(0, 66, 0), c(0, 67, 0), c(0, 68, 0));
        assertEquals(121, Felling.doomedLeaves(w, logs, 121).size());
        assertEquals(Set.of(), Felling.doomedLeaves(w, logs, 120), "one leaf past the limit: none cleared");
    }
    @Test void baseBelowWalksDownToTheSoil() {
        var w = new World().ground(-2, 2).trunk(0, 0, 64, 68, OAK).trunk(5, 5, 64, 68, OAK);
        assertEquals(Optional.of(c(0, 64, 0)), Felling.baseBelow(w, c(0, 64, 0), 32));
        assertEquals(Optional.of(c(0, 64, 0)), Felling.baseBelow(w, c(0, 68, 0), 32));
        assertEquals(Optional.empty(), Felling.baseBelow(w, c(0, 68, 0), 3), "four logs down is past a depth of three");
        assertEquals(Optional.empty(), Felling.baseBelow(w, c(5, 66, 5), 32), "x 5 is beyond the soil");
        assertEquals(Optional.empty(), Felling.baseBelow(w, c(0, 70, 0), 32), "not a log");
        w.set(0, 66, 0, PLACED);
        assertEquals(Optional.empty(), Felling.baseBelow(w, c(0, 66, 0), 32), "a player's log is never a trunk");
        assertEquals(Optional.empty(), Felling.baseBelow(w, c(0, 68, 0), 32), "the player's log under it stops the walk short of the soil");
    }
}
