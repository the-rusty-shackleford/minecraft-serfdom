/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** The verification mod: a listener at normal priority, where Village Deed's offer listens, that
 * counts the entity uses that reach it, so a test can tell whether Deed would have seen one; the
 * crimes Thief punished (D-0003's captures); and the sounds played at a place, so a test can hear
 * a captive's song. */
@net.neoforged.fml.common.Mod("serfdom_gametest")
public final class TestMod {
    /** Uses that reached normal priority, by the target's id. */
    static final Map<UUID, AtomicInteger> REACHED_NORMAL = new ConcurrentHashMap<>();
    /** A crime Thief punished: who, how grave, seen by how many. */
    record Crime(UUID criminal, String grade, int witnesses) {}
    private static final List<Crime> CRIMES = new ArrayList<>();
    /** A sound played on the server at a place: which, where, at what pitch. */
    record Heard(SoundEvent sound, Vec3 at, float pitch) {}
    private static final List<Heard> HEARD = new ArrayList<>();

    public TestMod() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (!e.getLevel().isClientSide) REACHED_NORMAL.computeIfAbsent(e.getTarget().getUUID(), k -> new AtomicInteger()).incrementAndGet();
        });
        NeoForge.EVENT_BUS.addListener((io.github.mortuusars.thief.neoforge.api.event.CrimeCommitedEvent e) -> {
            synchronized (CRIMES) { CRIMES.add(new Crime(e.criminal.getUUID(), e.crime.name(), e.witnesses.size())); }
        });
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition e) -> {
            if (e.getLevel().isClientSide() || e.getSound() == null) return;
            synchronized (HEARD) { HEARD.add(new Heard(e.getSound().value(), e.getPosition(), e.getNewPitch())); }
        });
    }

    static int reached(UUID target) { var n = REACHED_NORMAL.get(target); return n == null ? 0 : n.get(); }

    /** effects: the crimes Thief punished on that player, oldest first. */
    static List<Crime> crimes(UUID criminal) {
        synchronized (CRIMES) { return CRIMES.stream().filter(c -> c.criminal().equals(criminal)).toList(); }
    }

    /** effects: the sounds of that event heard within a block of {@code near}, oldest first. */
    static List<Heard> heard(SoundEvent sound, Vec3 near) {
        synchronized (HEARD) { return HEARD.stream().filter(s -> s.sound() == sound && s.at().distanceTo(near) < 1.5).toList(); }
    }
}
