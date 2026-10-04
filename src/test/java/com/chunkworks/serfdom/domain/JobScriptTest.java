/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Partitions: target names in any case and unknown; bonus professions all present, some, none
 * with a fallback present, none with none; blank names; sets copied. */
final class JobScriptTest {
    static JobScript woodcutting() {
        return new JobScript("serfdom:woodcutting", Optional.of("minecraft:axes"), JobScript.Target.TREE, new Radius(4, 16, 32),
                Set.of("morevillagers:woodworker"), Set.of("minecraft:fletcher"));
    }

    @Test void targetsAreNamedInAnyCase() {
        assertEquals(Optional.of(JobScript.Target.TREE), JobScript.Target.named("tree"));
        assertEquals(Optional.of(JobScript.Target.CROP), JobScript.Target.named("CROP"));
        assertEquals(Optional.empty(), JobScript.Target.named("mine"));
    }
    @Test void theWoodworkerWorksFasterWhereThePackHasOne() {
        var job = woodcutting();
        assertEquals(Set.of("morevillagers:woodworker"), job.bonusIn(Set.of("minecraft:fletcher", "morevillagers:woodworker", "minecraft:farmer")));
        assertEquals(Set.of("minecraft:fletcher"), job.bonusIn(Set.of("minecraft:fletcher", "minecraft:farmer")), "else the fletcher");
        assertEquals(Set.of(), job.bonusIn(Set.of("minecraft:farmer")));
    }
    @Test void severalBonusProfessionsAllCount() {
        var smith = new JobScript("serfdom:blacksmith", Optional.empty(), JobScript.Target.CROP, Radius.WORK,
                Set.of("minecraft:armorer", "minecraft:toolsmith", "minecraft:weaponsmith"), Set.of());
        assertEquals(Set.of("minecraft:armorer", "minecraft:toolsmith"), smith.bonusIn(Set.of("minecraft:armorer", "minecraft:toolsmith")));
    }
    @Test void blankNamesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new JobScript(" ", Optional.empty(), JobScript.Target.TREE, Radius.WORK, Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new JobScript("a", Optional.of(""), JobScript.Target.TREE, Radius.WORK, Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new JobScript("a", Optional.empty(), JobScript.Target.TREE, Radius.WORK, Set.of(""), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new JobScript("a", Optional.empty(), JobScript.Target.TREE, Radius.WORK, Set.of(), Set.of(" ")));
    }
    @Test void theSetsAreCopies() {
        var names = new java.util.HashSet<>(Set.of("minecraft:farmer"));
        var job = new JobScript("serfdom:farming", Optional.of("minecraft:hoes"), JobScript.Target.CROP, Radius.WORK, names, Set.of());
        names.add("minecraft:cleric");
        assertEquals(Set.of("minecraft:farmer"), job.bonus());
        assertThrows(UnsupportedOperationException.class, () -> job.bonus().add("x"));
    }
}
