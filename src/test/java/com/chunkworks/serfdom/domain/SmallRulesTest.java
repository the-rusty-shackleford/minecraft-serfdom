/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Radius, Need, Pace, Follow and Spot: each rule at its boundaries. */
final class SmallRulesTest {
    // Radius. Partitions: below, at and above each bound; the cube's faces, edges and corners; an
    // invalid triple of each kind.
    @Test void radiusIsHeldToItsBounds() {
        var r = new Radius(4, 16, 32);
        assertEquals(4, r.clamp(1));
        assertEquals(4, r.clamp(4));
        assertEquals(20, r.clamp(20));
        assertEquals(32, r.clamp(32));
        assertEquals(32, r.clamp(33));
    }
    @Test void theAreaIsACube() {
        var post = new Cell(0, 64, 0);
        assertTrue(Radius.contains(post, 8, new Cell(8, 72, -8)), "the corner is inside");
        assertFalse(Radius.contains(post, 8, new Cell(9, 64, 0)));
        assertFalse(Radius.contains(post, 8, new Cell(0, 55, 0)));
    }
    @Test void aBadRadiusIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Radius(0, 8, 16));
        assertThrows(IllegalArgumentException.class, () -> new Radius(9, 8, 16));
        assertThrows(IllegalArgumentException.class, () -> new Radius(4, 17, 16));
        assertThrows(IllegalArgumentException.class, () -> new Radius(4, 8, Radius.CEILING + 1));
        assertDoesNotThrow(() -> new Radius(1, 1, Radius.CEILING));
    }

    // Need. Partitions: none, one, several in and out of order; the wire code for none, each need
    // and codes no need has.
    @Test void theFirstDeclaredNeedShows() {
        assertEquals(Optional.empty(), Need.shown(List.of()));
        assertEquals(Optional.of(Need.CHEST_FULL), Need.shown(List.of(Need.CHEST_FULL)));
        assertEquals(Optional.of(Need.NO_BED), Need.shown(List.of(Need.CHEST_FULL, Need.NO_TOOL, Need.NO_BED)));
        assertEquals(Optional.of(Need.NO_TOOL), Need.shown(Set.of(Need.CHEST_FULL, Need.NO_TOOL)));
    }
    @Test void needsTravelAsCodes() {
        assertEquals(0, Need.code(Optional.empty()));
        for (var n : Need.values()) assertEquals(Optional.of(n), Need.of(Need.code(Optional.of(n))));
        assertEquals(Optional.empty(), Need.of((byte) 0));
        assertEquals(Optional.empty(), Need.of((byte) (Need.values().length + 1)));
        assertEquals(Optional.empty(), Need.of((byte) -1));
    }

    // Pace. Partitions: a log with an iron axe, with a hand, with a bonus; a block that does not
    // drop for the tool; instant blocks under the floor; efficiency on a tool and on a hand; bad
    // arguments.
    @Test void anIronAxeTakesTheTicksAPlayerTakes() {
        assertEquals(10, Pace.ticks(2.0F, 6.0F, true, 1, 1.0), "oak log, iron axe: 2 x 30 / 6");
        assertEquals(60, Pace.ticks(2.0F, 1.0F, true, 1, 1.0), "by hand: 2 x 30 / 1");
        assertEquals(8, Pace.ticks(2.0F, 6.0F, true, 1, Pace.BONUS), "10 / 1.25");
        assertEquals(200, Pace.ticks(2.0F, 1.0F, false, 1, 1.0), "stone by hand: 2 x 100");
    }
    @Test void theFloorKeepsQuickWorkVisible() {
        assertEquals(10, Pace.ticks(0.0F, 1.0F, true, 10, 1.0), "a ripe crop breaks at once; the floor holds it to 10");
        assertEquals(8, Pace.ticks(0.0F, 1.0F, true, 10, Pace.BONUS));
        assertEquals(1, Pace.ticks(0.0F, 1.0F, true, 1, 1.0));
    }
    @Test void efficiencyAddsLevelSquaredPlusOne() {
        assertEquals(6.0F, Pace.withEfficiency(6.0F, 0));
        assertEquals(8.0F, Pace.withEfficiency(6.0F, 1));
        assertEquals(32.0F, Pace.withEfficiency(6.0F, 5));
        assertEquals(1.0F, Pace.withEfficiency(1.0F, 5), "no bonus on a block the tool is no faster at");
    }
    @Test void badPaceArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Pace.ticks(-1, 1, true, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Pace.ticks(1, 0, true, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Pace.ticks(1, 1, true, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Pace.ticks(1, 1, true, 1, 0));
    }

    // Follow. Partitions: other dimension; past LOST; past START; between STOP and START walking and
    // standing; at STOP; near.
    @Test void followingStartsLateAndStopsClose() {
        assertEquals(Follow.Step.LOST, Follow.step(false, 1, false));
        assertEquals(Follow.Step.LOST, Follow.step(true, 48.01, true));
        assertEquals(Follow.Step.WALK, Follow.step(true, 48, false));
        assertEquals(Follow.Step.WALK, Follow.step(true, 6.01, false));
        assertEquals(Follow.Step.STAY, Follow.step(true, 5, false), "standing between stop and start: stays");
        assertEquals(Follow.Step.WALK, Follow.step(true, 5, true), "walking between stop and start: keeps walking");
        assertEquals(Follow.Step.STAY, Follow.step(true, 3, true));
        assertEquals(Follow.Step.STAY, Follow.step(true, 0, false));
        assertThrows(IllegalArgumentException.class, () -> Follow.step(true, -1, false));
    }

    // Spot and Cell.
    @Test void spotsCompareWithinADimension() {
        var a = new Spot("minecraft:overworld", 0, 0, 0);
        assertTrue(a.within(new Spot("minecraft:overworld", 3, 4, 0), 5));
        assertFalse(a.within(new Spot("minecraft:overworld", 3, 4, 1), 5));
        assertFalse(a.within(new Spot("minecraft:the_nether", 0, 0, 0), 5));
        assertEquals(26, Cell.AROUND.length);
        assertEquals(3, new Cell(0, 0, 0).chebyshev(new Cell(-3, 2, 1)));
    }
}
