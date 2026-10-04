/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;

/** The logs players placed, kept with each chunk (D-0001), so a woodcutter never takes a build for
 * a tree: set when a player places a log, cleared when it is broken or a tree grows from a
 * sapling there. A log placed before Serfdom was installed is not on record; the tree rules
 * (standing on soil, crowned with natural leaves) keep most builds safe all the same. */
public final class PlacedLogs {
    private PlacedLogs() {}

    public static final Codec<LongOpenHashSet> CODEC = Codec.LONG.listOf().xmap(LongOpenHashSet::new, s -> new ArrayList<>(s));

    /** effects: true iff a player placed the log at {@code pos} in {@code chunk}. */
    public static boolean placed(ChunkAccess chunk, BlockPos pos) {
        return chunk instanceof LevelChunk level && level.getExistingData(Serfdom.PLACED_LOGS).map(s -> s.contains(pos.asLong())).orElse(false);
    }

    /** effects: the log at {@code pos} is no longer on record. */
    public static void forget(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !server.isLoaded(pos)) return;
        var chunk = server.getChunkAt(pos);
        chunk.getExistingData(Serfdom.PLACED_LOGS).ifPresent(s -> { if (s.remove(pos.asLong())) chunk.setUnsaved(true); });
    }

    private static void remember(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        var chunk = server.getChunkAt(pos);
        if (chunk.getData(Serfdom.PLACED_LOGS).add(pos.asLong())) chunk.setUnsaved(true);
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (e.getEntity() instanceof Player && e.getPlacedBlock().is(BlockTags.LOGS)) remember(e.getLevel(), e.getPos());
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> forget(e.getLevel(), e.getPos()));
        NeoForge.EVENT_BUS.addListener((BlockGrowFeatureEvent e) -> forget(e.getLevel(), e.getPos()));
    }
}
