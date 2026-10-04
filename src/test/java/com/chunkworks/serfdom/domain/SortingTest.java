/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: holders, tag sharers and overflow present and absent; full holders; holders at
 * different distances; sharers with different counts and equal counts; the overflow already a
 * holder; the overflow full; nothing anywhere; the overflow chosen among containers, with ties. */
final class SortingTest {
    static final Cell POST = new Cell(0, 64, 0);
    static Sorting.Bin bin(int x, boolean holds, int shared, int room) { return new Sorting.Bin(new Cell(x, 64, 0), holds, shared, room); }
    static Cell at(int x) { return new Cell(x, 64, 0); }

    @Test void holdersFirstThenSharersThenOverflow() {
        var bins = List.of(bin(1, false, 0, 64), bin(5, false, 2, 64), bin(9, true, 3, 10));
        assertEquals(List.of(at(9), at(5), at(1)), Sorting.order(POST, bins, Optional.of(at(1))));
    }
    @Test void aFullContainerIsSkipped() {
        var bins = List.of(bin(2, true, 3, 0), bin(4, true, 3, 5), bin(1, false, 0, 0));
        assertEquals(List.of(at(4)), Sorting.order(POST, bins, Optional.of(at(1))), "the full holder and the full overflow are left out");
    }
    @Test void nearerHoldersComeFirst() {
        var bins = List.of(bin(7, true, 0, 1), bin(-3, true, 0, 1), bin(3, true, 0, 1));
        assertEquals(List.of(at(-3), at(3), at(7)), Sorting.order(POST, bins, Optional.empty()), "-3 and 3 tie on distance; x breaks the tie");
    }
    @Test void moreSharedTagsBeatNearness() {
        var bins = List.of(bin(1, false, 1, 9), bin(8, false, 4, 9), bin(2, false, 4, 9));
        assertEquals(List.of(at(2), at(8), at(1)), Sorting.order(POST, bins, Optional.empty()));
    }
    @Test void theOverflowIsListedOnce() {
        var bins = List.of(bin(1, true, 0, 9));
        assertEquals(List.of(at(1)), Sorting.order(POST, bins, Optional.of(at(1))));
    }
    @Test void nowhereToGoIsEmpty() {
        assertEquals(List.of(), Sorting.order(POST, List.of(bin(1, false, 0, 64)), Optional.empty()), "a container that neither holds nor shares, and no overflow");
        assertEquals(List.of(), Sorting.order(POST, List.of(), Optional.of(at(3))), "an overflow no bin describes");
    }
    @Test void theOverflowIsTheNearestContainer() {
        assertEquals(Optional.of(at(2)), Sorting.overflow(POST, List.of(at(5), at(2), at(-4))));
        assertEquals(Optional.of(new Cell(0, 62, 0)), Sorting.overflow(POST, List.of(new Cell(0, 66, 0), new Cell(0, 62, 0))), "a tie goes to the lower");
        assertEquals(Optional.empty(), Sorting.overflow(POST, List.of()));
    }
    @Test void badBinsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> bin(0, false, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> bin(0, false, 0, -1));
    }
}
