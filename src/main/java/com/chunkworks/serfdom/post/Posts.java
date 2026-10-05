/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.post;

import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.job.Storage;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** The Work Posts loaded on each side, by dimension: the server's, so a block placed or broken in a
 * post's area marks its storage index stale; the client's, to draw outlines. */
public final class Posts {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> SERVER = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> CLIENT = new ConcurrentHashMap<>();
    private Posts() {}

    static void track(Level level, BlockPos pos) { side(level).computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(pos.immutable()); }
    static void untrack(Level level, BlockPos pos) { var s = side(level).get(level.dimension()); if (s != null) s.remove(pos); }
    private static Map<ResourceKey<Level>, Set<BlockPos>> side(Level level) { return level.isClientSide ? CLIENT : SERVER; }

    /** effects: the post at {@code at} when its chunk is loaded and it is there. */
    public static Optional<WorkPostBlockEntity> loaded(MinecraftServer server, GlobalPos at) {
        var level = server.getLevel(at.dimension());
        if (level == null || !level.isLoaded(at.pos())) return Optional.empty();
        return level.getBlockEntity(at.pos()) instanceof WorkPostBlockEntity post ? Optional.of(post) : Optional.empty();
    }

    /** effects: the loaded posts {@code owner} owns in {@code level} within {@code range} blocks of
     * {@code at}, nearest first (D-0005: the canteen). */
    public static java.util.List<WorkPostBlockEntity> near(net.minecraft.server.level.ServerLevel level, BlockPos at, double range, java.util.UUID owner) {
        var posts = SERVER.get(level.dimension());
        if (posts == null) return java.util.List.of();
        var out = new java.util.ArrayList<WorkPostBlockEntity>();
        for (var pos : posts) {
            if (pos.distSqr(at) > range * range || !level.isLoaded(pos)) continue;
            if (level.getBlockEntity(pos) instanceof WorkPostBlockEntity post && post.owner().filter(owner::equals).isPresent()) out.add(post);
        }
        out.sort(java.util.Comparator.comparingDouble(p -> p.getBlockPos().distSqr(at)));
        return out;
    }

    /** effects: the posts a client has loaded in {@code dimension}. */
    public static Set<BlockPos> client(ResourceKey<Level> dimension) { return CLIENT.getOrDefault(dimension, Set.of()); }

    /** effects: every loaded post whose area holds {@code pos} will look for its storage again. */
    static void changed(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof Level level) || level.isClientSide) return;
        var posts = SERVER.get(level.dimension());
        if (posts == null) return;
        for (var at : posts) {
            if (at.distManhattan(pos) > 3 * Radius.CEILING) continue;
            if (level.getBlockEntity(at) instanceof WorkPostBlockEntity post && Radius.contains(Storage.cell(at), post.radius(), Storage.cell(pos))) post.stale();
        }
    }

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> changed(e.getLevel(), e.getPos()));
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> changed(e.getLevel(), e.getPos()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> SERVER.clear());
    }
}
