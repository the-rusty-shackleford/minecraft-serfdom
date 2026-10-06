/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.domain.Holds;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** The holds on places of work in each dimension (D-0008): a farm's plots and the trees being
 * felled, each held by one worker at a time, by the game's clock. Not saved: a hold ends with the
 * server, as the task holding it does. Server thread only. */
public final class Holding {
    private static final Map<ResourceKey<Level>, Holds<Job.Place>> HOLDS = new ConcurrentHashMap<>();
    private Holding() {}

    private static Holds<Job.Place> in(ServerLevel level) { return HOLDS.computeIfAbsent(level.dimension(), k -> new Holds<>()); }

    /** effects: {@code worker} holds {@code place} from now (taken or renewed); false, and nothing
     * changed, when another worker holds it. */
    public static boolean hold(ServerLevel level, Job.Place place, UUID worker) { return in(level).hold(place, worker, level.getGameTime()); }

    /** effects: true iff a worker other than {@code worker} holds {@code place} now. */
    public static boolean heldByAnother(ServerLevel level, Job.Place place, UUID worker) { return in(level).heldByAnother(place, worker, level.getGameTime()); }

    /** effects: the worker holding {@code place} now, if one does. */
    public static Optional<UUID> holder(ServerLevel level, Job.Place place) { return in(level).holder(place, level.getGameTime()); }

    /** effects: {@code place} is free, when {@code worker} held it. */
    public static void release(ServerLevel level, Job.Place place, UUID worker) { in(level).release(place, worker); }

    /** effects: every place {@code worker} holds, in any dimension, is free. */
    public static void releaseAll(UUID worker) { for (var h : HOLDS.values()) h.releaseAll(worker); }

    public static void listen() { NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> HOLDS.clear()); }
}
