/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Arrays;
import java.util.Optional;

/** A kind of block a workshop job works at (D-0002), declared in the order a worker prefers it
 * when two make the same thing: made on the spot before left to cook, a blast furnace or a smoker
 * before a furnace, a furnace before a campfire. */
public enum Station {
    /** A crafting table. */
    TABLE("table"),
    /** Ranged Weapons Mod's weapons workbench: guns, parts and magazines. */
    WORKBENCH("workbench"),
    /** A smithing table: netherite upgrades. */
    SMITHING("smithing"),
    /** Farmer's Delight's cutting board, with a knife. */
    BOARD("board"),
    BLAST("blast_furnace"),
    SMOKER("smoker"),
    FURNACE("furnace"),
    /** A campfire, or Farmer's Delight's stove: campfire recipes, no fuel, lit. */
    CAMPFIRE("campfire"),
    /** Farmer's Delight's cooking pot, heated from below. */
    POT("pot"),
    /** An anvil: repairs, no recipes. */
    ANVIL("anvil");

    private final String name;
    Station(String name) { this.name = name; }

    /** effects: the name a job file uses for it. */
    public String named() { return name; }

    /** effects: true iff a load is left at the station to cook, and collected later. */
    public boolean cooks() { return this == BLAST || this == SMOKER || this == FURNACE || this == CAMPFIRE || this == POT; }

    /** effects: true iff the station burns fuel a worker must load. */
    public boolean burns() { return this == BLAST || this == SMOKER || this == FURNACE; }

    /** effects: the station a job file names ("table", "blast_furnace"), ignoring case. */
    public static Optional<Station> named(String name) {
        return Arrays.stream(values()).filter(s -> s.name.equalsIgnoreCase(name)).findFirst();
    }
}
