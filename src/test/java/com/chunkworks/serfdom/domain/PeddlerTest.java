/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>a lot: no value (0, below, NaN, infinite); an emerald exactly; above (rounded down, up, past the
 * most); below (bread's sixth, a half rounding up, a value so small its lot passes the most); a lot
 * that breaks its invariant;</li>
 * <li>spare: more than it keeps, exactly, less; negative counts;</li>
 * <li>as a stall: open with a lot spare, closed short of one, its proceeds never full.</li>
 * </ul> */
final class PeddlerTest {
    @Test void aLotIsWorthTheItemsValueInWholeEmeralds() {
        assertEquals(Optional.empty(), Peddler.lot(0));
        assertEquals(Optional.empty(), Peddler.lot(-1));
        assertEquals(Optional.empty(), Peddler.lot(Double.NaN));
        assertEquals(Optional.empty(), Peddler.lot(Double.POSITIVE_INFINITY));
        assertEquals(new Peddler.Lot(1, 1), Peddler.lot(1).orElseThrow());
        assertEquals(new Peddler.Lot(1, 2), Peddler.lot(2.4).orElseThrow(), "rounded down");
        assertEquals(new Peddler.Lot(1, 3), Peddler.lot(2.5).orElseThrow(), "rounded up");
        assertEquals(new Peddler.Lot(1, 64), Peddler.lot(500).orElseThrow(), "no more than 64 a lot");
        assertEquals(new Peddler.Lot(6, 1), Peddler.lot(1 / 6.0).orElseThrow(), "bread: six for an emerald");
        assertEquals(new Peddler.Lot(3, 1), Peddler.lot(0.4).orElseThrow(), "2.5 an emerald, rounded up");
        assertEquals(new Peddler.Lot(2, 1), Peddler.lot(0.6).orElseThrow());
        assertEquals(new Peddler.Lot(64, 1), Peddler.lot(0.001).orElseThrow(), "no more than 64 a lot");
        assertThrows(IllegalArgumentException.class, () -> new Peddler.Lot(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Peddler.Lot(1, 65));
    }

    @Test void itSellsWhatItCarriesBeyondWhatItKeeps() {
        assertEquals(17, Peddler.spare(20, 3));
        assertEquals(0, Peddler.spare(3, 3));
        assertEquals(0, Peddler.spare(2, 3));
        assertThrows(IllegalArgumentException.class, () -> Peddler.spare(-1, 3));
        assertThrows(IllegalArgumentException.class, () -> Peddler.spare(1, -3));
    }

    @Test void asAStallItIsOpenWhileItHasALotSpare() {
        var lot = new Peddler.Lot(6, 1);
        var s = Peddler.stall("bread", lot, 17);
        assertTrue(s.open());
        assertEquals(2, s.sales(), "two lots of six in seventeen");
        assertEquals(1 / 6.0, s.each(), 1e-12);
        assertFalse(Peddler.stall("bread", lot, 5).open(), "short of a lot");
        assertEquals(Peddler.ROOM, s.room(), "its purse past the cap takes and loses");
    }
}
