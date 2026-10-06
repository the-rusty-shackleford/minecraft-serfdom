/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Sowing.Layer;
import com.chunkworks.serfdom.domain.Sowing.Sow;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>a spot that grew something: sown with it among neighbours of another kind, and among stems; a
 * remembered stem sown again;</li>
 * <li>a spot that never grew anything: one kind 1 away, at the edge of the square (4 away along an
 * axis, and at its corner), and just past it (5); two kinds in the square, one of them remembered on
 * a bare spot; nothing in the square;</li>
 * <li>stems: only stems in the square; a stem beside the spot with the square's other crops all one
 * kind; a stem diagonal to it;</li>
 * <li>the wait: one tick short, exactly, a wait of 0;</li>
 * <li>the square at the layer's edge and corner; the spots' order; crops are never sown;</li>
 * <li>refused: a crop on a bare spot and the other way, a spot outside the layer, an empty layer, a
 * negative wait.</li>
 * </ul> */
final class SowingTest {
    private static final String WHEAT = "minecraft:wheat_seeds", CARROT = "minecraft:carrot", PUMPKIN = "minecraft:pumpkin_seeds";
    private static final Set<String> STEMS = Set.of(PUMPKIN);
    private static final long NOW = 50_000, WAIT = 1_200, LONG_AGO = NOW - WAIT;

    /** effects: a 21 by 21 layer from (-10, -10), with the bare spot (0, 0) never planted. */
    private static Layer field() { return new Layer(-10, -10, 21, 21); }
    private static List<Sow> sown(Layer l) { return Sowing.choose(l, STEMS, NOW, WAIT); }

    @Test void aSpotThatGrewACropIsSownWithItAgain() {
        var l = field();
        l.bare(0, 0, Optional.of(CARROT), LONG_AGO);
        for (int x = -2; x <= 2; x++) if (x != 0) l.crop(x, 0, WHEAT);
        l.crop(0, 1, PUMPKIN);
        assertEquals(List.of(new Sow(0, 0, CARROT)), sown(l), "the player's carrot, whatever grows around it");
        var stem = field();
        stem.bare(0, 0, Optional.of(PUMPKIN), LONG_AGO);
        assertEquals(List.of(new Sow(0, 0, PUMPKIN)), sown(stem), "a stem where a stem grew");
    }

    @Test void aNeverPlantedSpotCopiesNeighboursThatAgree() {
        var l = field();
        l.bare(0, 0, Optional.empty(), LONG_AGO);
        l.crop(1, 0, WHEAT);
        l.crop(-3, 2, WHEAT);
        assertEquals(List.of(new Sow(0, 0, WHEAT)), sown(l));
    }

    @Test void theSquareReachesFourBlocksEachWay() {
        var edge = field();
        edge.bare(0, 0, Optional.empty(), LONG_AGO);
        edge.crop(4, 0, WHEAT);
        assertEquals(List.of(new Sow(0, 0, WHEAT)), sown(edge), "4 away along x");
        var corner = field();
        corner.bare(0, 0, Optional.empty(), LONG_AGO);
        corner.crop(-4, 4, WHEAT);
        assertEquals(List.of(new Sow(0, 0, WHEAT)), sown(corner), "the square's corner");
        var past = field();
        past.bare(0, 0, Optional.empty(), LONG_AGO);
        past.crop(5, 0, WHEAT);
        past.crop(0, -5, WHEAT);
        assertEquals(List.of(), sown(past), "5 away is too far");
    }

    @Test void whereTwoKindsMeetTheSpotIsLeftForThePlayer() {
        var l = field();
        l.bare(0, 0, Optional.empty(), LONG_AGO);
        l.crop(1, 0, WHEAT);
        l.crop(-4, -4, CARROT);
        assertEquals(List.of(), sown(l));
        var remembered = field();
        remembered.bare(0, 0, Optional.empty(), LONG_AGO);
        remembered.crop(1, 0, WHEAT);
        remembered.bare(0, 3, Optional.of(CARROT), NOW);
        assertEquals(List.of(), sown(remembered), "a remembered carrot counts as one; it is not due itself");
        var empty = field();
        empty.bare(0, 0, Optional.empty(), LONG_AGO);
        assertEquals(List.of(), sown(empty), "nothing grows near");
    }

    @Test void stemsAreNeverCopied() {
        var only = field();
        only.bare(0, 0, Optional.empty(), LONG_AGO);
        only.crop(2, 0, PUMPKIN);
        only.crop(-2, 0, PUMPKIN);
        assertEquals(List.of(), sown(only), "only stems near");
        var beside = field();
        beside.bare(0, 0, Optional.empty(), LONG_AGO);
        beside.crop(1, 0, PUMPKIN);
        for (int z = -4; z <= 4; z++) if (z != 0) beside.crop(-1, z, WHEAT);
        assertEquals(List.of(), sown(beside), "the ground beside a stem is its fruit's");
        var diagonal = field();
        diagonal.bare(0, 0, Optional.empty(), LONG_AGO);
        diagonal.crop(1, 1, PUMPKIN);
        diagonal.crop(-1, 0, WHEAT);
        assertEquals(List.of(), sown(diagonal), "a stem anywhere in the square keeps it bare");
    }

    @Test void aSpotWaitsBeforeItIsSown() {
        var l = field();
        l.bare(0, 0, Optional.of(WHEAT), NOW - WAIT + 1);
        l.bare(5, 5, Optional.of(WHEAT), NOW - WAIT);
        assertEquals(List.of(new Sow(5, 5, WHEAT)), sown(l), "one tick short waits; exactly the wait is due");
        assertEquals(2, Sowing.choose(l, STEMS, NOW, 0).size(), "a wait of 0 sows at once");
    }

    @Test void aSpotAtTheLayersEdgeSeesOnlyTheLayer() {
        var l = new Layer(0, 0, 3, 3);
        l.bare(0, 0, Optional.empty(), LONG_AGO);
        l.crop(2, 2, WHEAT);
        l.bare(2, 0, Optional.empty(), LONG_AGO);
        assertEquals(List.of(new Sow(0, 0, WHEAT), new Sow(2, 0, WHEAT)), sown(l), "corners, by z then x");
    }

    @Test void spotsComeByZThenXAndCropsAreNeverSown() {
        var l = field();
        l.crop(0, 0, WHEAT);
        l.bare(2, -1, Optional.empty(), LONG_AGO);
        l.bare(-2, 1, Optional.empty(), LONG_AGO);
        l.bare(-1, -1, Optional.empty(), LONG_AGO);
        assertEquals(List.of(new Sow(-1, -1, WHEAT), new Sow(2, -1, WHEAT), new Sow(-2, 1, WHEAT)), sown(l));
    }

    @Test void badInputsAreRefused() {
        var l = field();
        l.crop(0, 0, WHEAT);
        assertThrows(IllegalArgumentException.class, () -> l.bare(0, 0, Optional.empty(), NOW));
        l.bare(1, 0, Optional.empty(), NOW);
        assertThrows(IllegalArgumentException.class, () -> l.crop(1, 0, WHEAT));
        assertThrows(IllegalArgumentException.class, () -> l.crop(11, 0, WHEAT));
        assertThrows(IllegalArgumentException.class, () -> new Layer(0, 0, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> Sowing.choose(l, STEMS, NOW, -1));
    }
}
