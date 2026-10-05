/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import com.chunkworks.serfdom.domain.Parting.Fate;
import com.chunkworks.serfdom.domain.Parting.Way;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: each way a worker stops being its owner's (dies, turns zombie, set free, escapes,
 * freed by the law) × a piece with Curse of Vanishing or without; the guaranteed mark. */
final class PartingTest {
    @Test void deathAndTheZombieDropEveryPieceButTheVanishing() {
        for (var way : new Way[]{Way.DIES, Way.CONVERTS}) {
            assertEquals(Fate.DROPS, Parting.fate(way, false), way.name());
            assertEquals(Fate.VANISHES, Parting.fate(way, true), way.name());
        }
    }

    @Test void setFreeTheWorkerDropsItAllWhereItStandsVanishingToo() {
        assertEquals(Fate.DROPS, Parting.fate(Way.SET_FREE, false));
        assertEquals(Fate.DROPS, Parting.fate(Way.SET_FREE, true), "Vanishing is a curse of death only");
    }

    @Test void anEscapeeAndOneTheLawFreesLeaveWearingIt() {
        for (var way : new Way[]{Way.ESCAPES, Way.FREED_BY_LAW}) for (boolean vanishing : new boolean[]{false, true})
            assertEquals(Fate.STAYS_ON, Parting.fate(way, vanishing), way.name());
    }

    @Test void everyWayHasAFate() {
        for (var way : Way.values()) for (boolean vanishing : new boolean[]{false, true}) assertNotNull(Parting.fate(way, vanishing));
    }

    @Test void thePieceIsMarkedToAlwaysDropWhole() {
        assertTrue(Parting.GUARANTEED > 1.0F, "vanilla drops a piece whole when its chance is above 1");
    }
}
