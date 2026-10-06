/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Farm.Offer;
import com.chunkworks.serfdom.domain.Farm.Plot;
import com.chunkworks.serfdom.domain.Farm.Post;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>touching: areas overlapping, touching exactly (r1 + r2 + 1 apart), one block apart; along x,
 * along z, along y (within reach and past it), at a corner; radii unequal, either way round; the
 * same spot; another owner; another job;</li>
 * <li>a farm: one post alone, a chain of three whose ends don't touch, a post left out, the input's
 * order kept, a start not among the posts;</li>
 * <li>plots: x and z at 0, 7, 8, -1, -8, -9; a cell's plot;</li>
 * <li>the next plot: its own area's before a nearer plot elsewhere, the nearest within its own, the
 * rest of the farm when its own has none, a plot held by another skipped (its own and elsewhere), a
 * plot the predicate passes (its own hold) kept, a tie broken by the plot, no offers, all held;</li>
 * <li>refused: a negative radius, a null owner, a negative distance.</li>
 * </ul> */
final class FarmTest {
    private static Post post(int x, int y, int z, int radius) { return new Post(new Cell(x, y, z), radius, "rusty", "serfdom:farming"); }
    private static Offer offer(int px, int pz, long d, boolean own) { return new Offer(new Plot(px, pz), d, own); }

    @Test void areasThatOverlapOrTouchAreOneFarm() {
        var a = post(0, 64, 0, 8);
        assertTrue(Farm.touches(a, post(10, 64, 0, 8)), "overlapping");
        assertTrue(Farm.touches(a, post(17, 64, 0, 8)), "touching exactly: 8 + 8 + 1 apart");
        assertFalse(Farm.touches(a, post(18, 64, 0, 8)), "one block between them");
        assertTrue(Farm.touches(a, post(0, 64, -17, 8)), "touching along z");
        assertFalse(Farm.touches(a, post(0, 64, -18, 8)), "apart along z");
        assertTrue(Farm.touches(a, post(17, 64, 17, 8)), "touching at a corner");
        assertTrue(Farm.touches(a, a), "the same post");
    }

    @Test void heightCountsAsTheOtherAxesDo() {
        var a = post(0, 64, 0, 4);
        assertTrue(Farm.touches(a, post(0, 73, 0, 4)), "a terrace 9 above touches");
        assertFalse(Farm.touches(a, post(0, 74, 0, 4)), "10 above is apart");
    }

    @Test void unequalRadiiReachAsFarAsTheirSum() {
        var small = post(0, 64, 0, 4);
        var large = post(21, 64, 0, 16);
        assertTrue(Farm.touches(small, large), "4 + 16 + 1 = 21 apart");
        assertTrue(Farm.touches(large, small), "either way round");
        assertFalse(Farm.touches(small, post(22, 64, 0, 16)));
    }

    @Test void anotherOwnerOrJobIsNeverLinked() {
        var a = post(0, 64, 0, 8);
        assertFalse(Farm.touches(a, new Post(new Cell(4, 64, 0), 8, "someone", "serfdom:farming")));
        assertFalse(Farm.touches(a, new Post(new Cell(4, 64, 0), 8, "rusty", "serfdom:woodcutting")));
    }

    @Test void aFarmIsEveryPostLinkedThroughOthers() {
        var a = post(0, 64, 0, 8);
        var b = post(17, 64, 0, 8);
        var c = post(34, 64, 0, 8);
        var apart = post(0, 64, 40, 8);
        var stranger = new Post(new Cell(5, 64, 0), 8, "someone", "serfdom:farming");
        assertFalse(Farm.touches(a, c), "the ends of the chain don't touch");
        var posts = List.of(c, apart, stranger, b, a);
        assertEquals(List.of(c, b, a), Farm.of(posts, a), "the chain, in the input's order");
        assertEquals(List.of(c, b, a), Farm.of(posts, c));
        assertEquals(List.of(apart), Farm.of(posts, apart), "a post alone");
        assertEquals(List.of(stranger), Farm.of(posts, stranger), "another owner's post, though it overlaps");
        assertThrows(IllegalArgumentException.class, () -> Farm.of(List.of(b), a));
    }

    @Test void plotsAreTheWorldGridsEightByEightColumns() {
        assertEquals(new Plot(0, 0), Plot.of(0, 7));
        assertEquals(new Plot(1, 0), Plot.of(8, 0));
        assertEquals(new Plot(-1, -1), Plot.of(-1, -8), "rounded down, not toward zero");
        assertEquals(new Plot(-2, -2), Plot.of(-9, -9));
        assertEquals(new Plot(-1, 0), Plot.of(new Cell(-3, 70, 5)));
    }

    @Test void itsOwnAreaComesFirstThenTheNearest() {
        var own = offer(5, 5, 400, true);
        var nearer = offer(1, 1, 4, false);
        assertEquals(Optional.of(own.plot()), Farm.next(List.of(nearer, own), p -> false), "its own area's though another is nearer");
        var closeOwn = offer(6, 5, 100, true);
        assertEquals(Optional.of(closeOwn.plot()), Farm.next(List.of(own, nearer, closeOwn), p -> false), "the nearest of its own");
        var far = offer(9, 9, 900, false);
        assertEquals(Optional.of(nearer.plot()), Farm.next(List.of(far, nearer), p -> false), "with none of its own, the nearest elsewhere");
    }

    @Test void plotsHeldByAnotherAreSkipped() {
        var own = offer(5, 5, 400, true);
        var otherOwn = offer(6, 6, 500, true);
        var elsewhere = offer(1, 1, 4, false);
        var offers = List.of(own, otherOwn, elsewhere);
        assertEquals(Optional.of(otherOwn.plot()), Farm.next(offers, Set.of(own.plot())::contains));
        assertEquals(Optional.of(elsewhere.plot()), Farm.next(offers, Set.of(own.plot(), otherOwn.plot())::contains),
                "its own area all held: it helps elsewhere");
        assertEquals(Optional.empty(), Farm.next(offers, p -> true), "everything held");
        assertEquals(Optional.of(own.plot()), Farm.next(offers, p -> false), "a plot it holds itself is passed by the predicate");
        assertEquals(Optional.empty(), Farm.next(List.of(), p -> false), "no offers");
    }

    @Test void aTieGoesToTheLowerPlot() {
        var a = offer(3, 1, 25, false);
        var b = offer(2, 9, 25, false);
        var c = offer(2, 4, 25, false);
        assertEquals(Optional.of(c.plot()), Farm.next(List.of(a, b, c), p -> false));
        assertEquals(Optional.of(c.plot()), Farm.next(List.of(c, b, a), p -> false), "whatever the list's order");
    }

    @Test void badValuesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> post(0, 0, 0, -1));
        assertThrows(NullPointerException.class, () -> new Post(new Cell(0, 0, 0), 4, null, "serfdom:farming"));
        assertThrows(IllegalArgumentException.class, () -> offer(0, 0, -1, true));
    }
}
