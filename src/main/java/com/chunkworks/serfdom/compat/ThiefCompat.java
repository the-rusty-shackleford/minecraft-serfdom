/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/** Thief's crimes (D-0003). A capture is Thief's HEAVY crime where the villager stood: Thief finds
 * the witnesses by its own rules, costs each witnessing villager its reputation, and tells the mods
 * that listen (Village Law opens a case when an officer saw it). Thief decides what is no crime: a
 * Hero of the Village, a place outside its protected structures, and through Village Deed a
 * village's owner and the players they trust. Without Thief a capture is no crime. */
public final class ThiefCompat {
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("thief");
    private ThiefCompat() {}

    /** effects: commits a capture's crime at {@code pos} by {@code player}; true iff anyone saw it. */
    public static boolean commitCapture(ServerLevel level, ServerPlayer player, BlockPos pos) { return LOADED && Inner.commit(level, player, pos); }

    private static final class Inner {
        static boolean commit(ServerLevel level, ServerPlayer player, BlockPos pos) {
            return !io.github.mortuusars.thief.world.Crime.HEAVY.commit(level, player, pos).witnesses().isEmpty();
        }
    }
}
