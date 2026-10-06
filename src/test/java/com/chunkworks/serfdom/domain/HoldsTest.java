/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>taking: a free place; one another holds (at LAPSE - 1 after its renewal, at LAPSE); one the
 * worker holds (renewed: its lapse moves on);</li>
 * <li>who holds: nobody, a live hold, a lapsed one; held by another: free, its own, another's live
 * and lapsed;</li>
 * <li>letting go: by the holder, by another, of a lapsed hold, of nothing; everything one worker
 * holds, others' kept;</li>
 * <li>forgetting: lapsed holds pruned, live ones kept; more than a prune's worth of holds taken;</li>
 * <li>refused: a null place or worker.</li>
 * </ul> */
final class HoldsTest {
    private static final UUID ANN = new UUID(0, 1), BOB = new UUID(0, 2);
    private static final long T = 1_000;

    @Test void aFreePlaceIsTaken() {
        var h = new Holds<String>();
        assertTrue(h.hold("plot", ANN, T));
        assertEquals(Optional.of(ANN), h.holder("plot", T));
        assertEquals(1, h.held(T));
    }

    @Test void anotherWorkersHoldStandsUntilItLapses() {
        var h = new Holds<String>();
        h.hold("plot", ANN, T);
        assertFalse(h.hold("plot", BOB, T + Holds.LAPSE - 1), "still held one tick before the lapse");
        assertEquals(Optional.of(ANN), h.holder("plot", T + Holds.LAPSE - 1), "a refusal changes nothing");
        assertTrue(h.hold("plot", BOB, T + Holds.LAPSE), "lapsed exactly LAPSE after its renewal");
        assertEquals(Optional.of(BOB), h.holder("plot", T + Holds.LAPSE));
        assertFalse(h.hold("plot", ANN, T + Holds.LAPSE + 1), "the old holder lost it");
    }

    @Test void holdingAgainRenews() {
        var h = new Holds<String>();
        h.hold("plot", ANN, T);
        assertTrue(h.hold("plot", ANN, T + 150));
        assertFalse(h.hold("plot", BOB, T + Holds.LAPSE), "the lapse counts from the renewal");
        assertTrue(h.hold("plot", BOB, T + 150 + Holds.LAPSE));
    }

    @Test void whoHoldsWhat() {
        var h = new Holds<String>();
        assertEquals(Optional.empty(), h.holder("plot", T), "nobody");
        assertFalse(h.heldByAnother("plot", ANN, T), "free");
        h.hold("plot", ANN, T);
        assertFalse(h.heldByAnother("plot", ANN, T), "its own");
        assertTrue(h.heldByAnother("plot", BOB, T), "another's");
        assertEquals(Optional.empty(), h.holder("plot", T + Holds.LAPSE), "lapsed");
        assertFalse(h.heldByAnother("plot", BOB, T + Holds.LAPSE), "another's, lapsed");
    }

    @Test void onlyTheHolderLetsGo() {
        var h = new Holds<String>();
        h.hold("plot", ANN, T);
        h.release("plot", BOB);
        assertEquals(Optional.of(ANN), h.holder("plot", T), "another can't let go of it");
        h.release("plot", ANN);
        assertEquals(Optional.empty(), h.holder("plot", T));
        assertTrue(h.hold("plot", BOB, T), "free at once");
        h.release("elsewhere", ANN);
        assertEquals(1, h.held(T), "letting go of nothing changes nothing");
    }

    @Test void aLapsedHoldIsLetGoToo() {
        var h = new Holds<String>();
        h.hold("plot", ANN, T);
        h.release("plot", ANN);
        h.hold("plot", ANN, T + Holds.LAPSE + 5);
        h.release("plot", ANN);
        assertEquals(0, h.held(T + Holds.LAPSE + 5));
    }

    @Test void releasingAllFreesOneWorkersPlacesOnly() {
        var h = new Holds<String>();
        h.hold("a", ANN, T);
        h.hold("b", ANN, T - 500);
        h.hold("c", BOB, T);
        h.releaseAll(ANN);
        assertEquals(Optional.empty(), h.holder("a", T));
        assertEquals(Optional.of(BOB), h.holder("c", T));
        assertTrue(h.hold("b", BOB, T));
        assertEquals(2, h.held(T));
    }

    @Test void pruningForgetsOnlyLapsedHolds() {
        var h = new Holds<String>();
        h.hold("old", ANN, T);
        h.hold("new", BOB, T + 150);
        h.prune(T + Holds.LAPSE);
        assertEquals(Optional.of(BOB), h.holder("new", T + Holds.LAPSE));
        assertEquals(1, h.held(T + Holds.LAPSE));
        assertTrue(h.hold("old", BOB, T + Holds.LAPSE));
    }

    @Test void manyHoldsKeepTheirMeaning() {
        var h = new Holds<Integer>();
        for (int i = 0; i < 3000; i++) assertTrue(h.hold(i, i % 2 == 0 ? ANN : BOB, i));
        long now = 2999;
        assertEquals(Holds.LAPSE, h.held(now), "the last LAPSE are live, whatever was pruned on the way");
        assertEquals(Optional.of(ANN), h.holder(2998, now));
        assertEquals(Optional.empty(), h.holder(2000, now));
    }

    @Test void nullsAreRefused() {
        var h = new Holds<String>();
        assertThrows(NullPointerException.class, () -> h.hold(null, ANN, T));
        assertThrows(NullPointerException.class, () -> h.hold("plot", null, T));
    }
}
