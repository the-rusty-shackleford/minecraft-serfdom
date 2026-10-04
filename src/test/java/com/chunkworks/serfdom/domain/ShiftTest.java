/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import static com.chunkworks.serfdom.domain.Shift.Step.*;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: the wind-down boundary (WIND_DOWN, WIND_DOWN + 1, 0) with and without cargo and
 * room; a full inventory with and without room; a tool needed and held, needed and stored, needed
 * and nowhere, not needed; precedence of full over tool. */
final class ShiftTest {
    static final int MID = 3000;
    static Shift.Facts facts(int left, boolean carrying, boolean full, boolean needsTool, boolean holdsTool, boolean stored, boolean room) {
        return new Shift.Facts(left, carrying, full, needsTool, holdsTool, stored, room);
    }

    @Test void windingDownDepositsWhatIsCarried() {
        assertEquals(DEPOSIT, Shift.next(facts(Shift.WIND_DOWN, true, false, true, true, true, true)).step());
        assertEquals(WORK, Shift.next(facts(Shift.WIND_DOWN + 1, true, false, true, true, true, true)).step());
        assertEquals(DEPOSIT, Shift.next(facts(0, true, false, false, false, false, true)).step());
    }
    @Test void windingDownWithNothingCarriedRests() {
        var plan = Shift.next(facts(Shift.WIND_DOWN, false, false, true, false, false, false));
        assertEquals(REST, plan.step());
        assertEquals(Optional.empty(), plan.need(), "no tool is not a need once the shift is over");
    }
    @Test void windingDownWithNoRoomShowsChestFull() {
        var plan = Shift.next(facts(10, true, true, true, true, true, false));
        assertEquals(WAIT, plan.step());
        assertEquals(Optional.of(Need.CHEST_FULL), plan.need());
    }
    @Test void aFullInventoryIsEmptiedBeforeAnythingElse() {
        assertEquals(DEPOSIT, Shift.next(facts(MID, true, true, true, false, false, true)).step(), "full beats a missing tool");
        var stuck = Shift.next(facts(MID, true, true, true, true, true, false));
        assertEquals(WAIT, stuck.step());
        assertEquals(Optional.of(Need.CHEST_FULL), stuck.need());
    }
    @Test void aMissingToolIsFetchedOrShown() {
        assertEquals(FETCH_TOOL, Shift.next(facts(MID, false, false, true, false, true, true)).step());
        var none = Shift.next(facts(MID, true, false, true, false, false, true));
        assertEquals(WAIT, none.step());
        assertEquals(Optional.of(Need.NO_TOOL), none.need());
    }
    @Test void otherwiseItWorks() {
        assertEquals(WORK, Shift.next(facts(MID, false, false, true, true, false, false)).step(), "holding a tool, nothing stored, no room: work");
        var plan = Shift.next(facts(MID, true, false, false, false, false, false));
        assertEquals(WORK, plan.step(), "a job without a tool never waits for one");
        assertEquals(Optional.empty(), plan.need());
    }
}
