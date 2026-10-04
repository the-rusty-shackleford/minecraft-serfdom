/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: ripe below, at and above the greatest age; seeds in a stack of one, of several, in
 * two stacks, absent; no drops; bad stacks. */
final class HarvestTest {
    static Harvest.Stack s(String item, int count) { return new Harvest.Stack(item, count); }

    @Test void ripeAtTheGreatestAge() {
        assertFalse(Harvest.ripe(6, 7));
        assertTrue(Harvest.ripe(7, 7));
        assertTrue(Harvest.ripe(8, 7));
        assertTrue(Harvest.ripe(0, 0));
        assertThrows(IllegalArgumentException.class, () -> Harvest.ripe(0, -1));
    }
    @Test void oneSeedIsTakenFromTheFirstStackHoldingIt() {
        var split = Harvest.replantFrom(List.of(s("wheat", 1), s("wheat_seeds", 3), s("wheat_seeds", 2)), "wheat_seeds");
        assertTrue(split.replant());
        assertEquals(List.of(s("wheat", 1), s("wheat_seeds", 2), s("wheat_seeds", 2)), split.rest());
    }
    @Test void aSingleSeedLeavesNothingOfItsStack() {
        var split = Harvest.replantFrom(List.of(s("carrot", 1)), "carrot");
        assertTrue(split.replant());
        assertEquals(List.of(), split.rest());
    }
    @Test void noSeedNoReplant() {
        var drops = List.of(s("beetroot", 1));
        var split = Harvest.replantFrom(drops, "beetroot_seeds");
        assertFalse(split.replant());
        assertEquals(drops, split.rest());
        assertFalse(Harvest.replantFrom(List.of(), "x").replant());
    }
    @Test void badStacksAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> s("wheat", 0));
    }
}
