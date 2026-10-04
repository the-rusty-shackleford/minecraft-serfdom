/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The verification mod: a listener at normal priority, where Village Deed's offer listens, that
 * counts the entity uses that reach it, so a test can tell whether Deed would have seen one. */
@net.neoforged.fml.common.Mod("serfdom_gametest")
public final class TestMod {
    /** Uses that reached normal priority, by the target's id. */
    static final Map<UUID, AtomicInteger> REACHED_NORMAL = new ConcurrentHashMap<>();

    public TestMod() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (!e.getLevel().isClientSide) REACHED_NORMAL.computeIfAbsent(e.getTarget().getUUID(), k -> new AtomicInteger()).incrementAndGet();
        });
    }

    static int reached(UUID target) { var n = REACHED_NORMAL.get(target); return n == null ? 0 : n.get(); }
}
