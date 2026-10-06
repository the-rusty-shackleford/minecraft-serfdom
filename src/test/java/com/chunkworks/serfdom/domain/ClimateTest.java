/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions:
 * <ul>
 * <li>a biome: hot, cold, both, neither;</li>
 * <li>an item: untagged; from here; from another climate; tagged both; each climate's village;</li>
 * <li>the bonus: 1.5, another, none or below.</li>
 * </ul> */
final class ClimateTest {
    @Test void aBiomesClimate() {
        assertEquals(Climate.HOT, Climate.of(true, false));
        assertEquals(Climate.COLD, Climate.of(false, true));
        assertEquals(Climate.TEMPERATE, Climate.of(false, false));
        assertEquals(Climate.TEMPERATE, Climate.of(true, true), "a biome tagged both is neither");
    }

    @Test void anItemFromAnotherClimateIsWorthTheBonus() {
        assertEquals(1.0, Climate.HOT.factor(Set.of(), 1.5), "untagged: the same everywhere");
        assertEquals(1.0, Climate.TEMPERATE.factor(Set.of(), 1.5));
        assertEquals(1.0, Climate.HOT.factor(Set.of(Climate.HOT), 1.5), "from here");
        assertEquals(1.5, Climate.COLD.factor(Set.of(Climate.HOT), 1.5), "the desert's goods in the snow");
        assertEquals(1.5, Climate.HOT.factor(Set.of(Climate.COLD), 1.5), "the snow's goods in the desert");
        assertEquals(1.5, Climate.TEMPERATE.factor(Set.of(Climate.COLD), 1.5), "the snow's goods in the plains");
        assertEquals(1.5, Climate.TEMPERATE.factor(Set.of(Climate.HOT, Climate.COLD), 1.5), "from both, foreign to the plains");
        assertEquals(1.0, Climate.COLD.factor(Set.of(Climate.HOT, Climate.COLD), 1.5), "from both, at home in the snow");
        assertEquals(2.0, Climate.COLD.factor(Set.of(Climate.HOT), 2.0), "the bonus is the server's");
        assertThrows(IllegalArgumentException.class, () -> Climate.HOT.factor(Set.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> Climate.HOT.factor(Set.of(), Double.NaN));
    }
}
