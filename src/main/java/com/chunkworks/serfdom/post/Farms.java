/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.post;

import com.chunkworks.serfdom.domain.Farm;
import com.chunkworks.serfdom.domain.JobScript;
import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.serfdom.job.Storage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Shared farms (D-0008): a post with a crop job and an owner shares its farm with every loaded post
 * of the same owner and job whose area touches its own, through one another ({@link Farm#of}). Any
 * other post is a farm of one. A farm is found again only after a post loads, unloads, or takes a new
 * job, radius or owner. Server thread only. */
public final class Farms {
    /** Each post's farm, by dimension, as positions; emptied when {@link Posts#generation} moves. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, List<BlockPos>>> FOUND = new HashMap<>();
    private static long foundAt = -1;
    private Farms() {}

    /** effects: {@code post}'s farm: the post itself first, then the others nearest it first (ties by
     * position). A post whose job is not a crop job, or that has no owner, is a farm of one. */
    public static List<WorkPostBlockEntity> of(ServerLevel level, WorkPostBlockEntity post) {
        if (!shares(post)) return List.of(post);
        if (Posts.generation() != foundAt) { FOUND.clear(); foundAt = Posts.generation(); }
        var found = FOUND.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        var members = found.get(post.getBlockPos());
        if (members == null) {
            var posts = new ArrayList<Farm.Post>();
            var at = new HashMap<Farm.Post, BlockPos>();
            Farm.Post mine = null;
            for (var pos : Posts.server(level.dimension())) {
                if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof WorkPostBlockEntity other) || !shares(other)) continue;
                var p = seen(other);
                posts.add(p);
                at.put(p, pos);
                if (other == post) mine = p;
            }
            if (mine == null) return List.of(post);
            members = Farm.of(posts, mine).stream().map(at::get).toList();
            for (var m : members) found.put(m, members);
        }
        var origin = post.getBlockPos();
        var out = new ArrayList<WorkPostBlockEntity>();
        out.add(post);
        members.stream().filter(m -> !m.equals(origin))
                .sorted(Comparator.<BlockPos>comparingDouble(m -> m.distSqr(origin)).thenComparingLong(BlockPos::asLong))
                .forEach(m -> { if (level.getBlockEntity(m) instanceof WorkPostBlockEntity other) out.add(other); });
        return out;
    }

    /** effects: each server tick, sweeps again the one field most overdue of all loaded posts' (D-0008:
     * {@link com.chunkworks.serfdom.job.Field#due}), so no tick sweeps more than one and none waits
     * behind another. */
    public static void listen() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> sweepStalest(e.getServer()));
    }

    private static void sweepStalest(net.minecraft.server.MinecraftServer server) {
        WorkPostBlockEntity stalest = null;
        ServerLevel where = null;
        long age = -1;
        for (var level : server.getAllLevels()) {
            long now = level.getGameTime();
            for (var pos : Posts.server(level.dimension())) {
                if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof WorkPostBlockEntity post)) continue;
                var due = post.field().due(now);
                if (due.isPresent() && due.getAsLong() > age) { age = due.getAsLong(); stalest = post; where = level; }
            }
        }
        if (stalest != null) stalest.field().refresh(where, stalest);
    }

    /** effects: true iff the post shares a farm with others like it: it has an owner and a crop job. */
    static boolean shares(WorkPostBlockEntity post) {
        return post.owner().isPresent() && Jobs.get(post.job()).map(j -> j.target() == JobScript.Target.CROP).orElse(false);
    }

    /** effects: the post as a farm sees it. */
    private static Farm.Post seen(WorkPostBlockEntity post) {
        return new Farm.Post(Storage.cell(post.getBlockPos()), post.radius(), post.owner().orElseThrow().toString(), post.job().toString());
    }
}
