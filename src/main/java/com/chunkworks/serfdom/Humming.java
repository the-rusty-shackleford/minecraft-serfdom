/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.WorkSong;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;

/** A captive's work song as it is sung (D-0003): at a work action (a fell, a harvest, a craft) now
 * and then a captive begins a phrase of {@link WorkSong} and sings it a note at a time in its own
 * hum, on through the shift's walks between actions. It ends when the worker waits with nothing to
 * do or the shift is over, so it is never heard idle, eating, sleeping or in chains. Server side;
 * nothing of it is saved. */
public final class Humming {
    /** The volume each note is played at: the sound's range is fixed at eight blocks. */
    private static final float VOLUME = 0.6F;
    /** Every phrase's cues, worked out once. */
    private static final List<List<WorkSong.Cue>> CUES = WorkSong.PHRASES.stream().map(WorkSong::cues).toList();
    private static final int[] LENGTHS = WorkSong.PHRASES.stream().mapToInt(WorkSong::length).toArray();

    /** A phrase being sung: which, the game tick it began, and the last tick of it already sung. */
    private static final class Singing {
        final int phrase;
        final long since;
        int sung = -1;
        Singing(int phrase, long since) { this.phrase = phrase; this.since = since; }
    }
    private static final Map<UUID, Singing> SINGING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_END = new ConcurrentHashMap<>();
    private Humming() {}

    /** effects: one tick of a shift at game time {@code now}, the worker {@code acting} (at a work
     * action) or on its way to one: a captive sings every note of its phrase due by now and not yet
     * sung, ends the phrase after its last note, and while acting may begin one as
     * {@link WorkSong#begins} says. Nothing for a hired worker, or with humming off. */
    public static void tick(ServerLevel level, Villager worker, long now, boolean acting) {
        if (!SerfdomConfig.HUMMING.get() || !Workers.of(worker).captive()) return;
        var id = worker.getUUID();
        var singing = SINGING.get(id);
        if (singing == null) {
            if (!acting) return;
            long since = now - LAST_END.getOrDefault(id, now - WorkSong.MIN_GAP);
            if (!WorkSong.begins(since, worker.getRandom().nextDouble())) return;
            singing = new Singing(worker.getRandom().nextInt(CUES.size()), now);
            SINGING.put(id, singing);
        }
        int at = (int) (now - singing.since);
        for (var cue : CUES.get(singing.phrase)) {
            if (cue.tick() <= singing.sung || cue.tick() > at) continue;
            level.playSound(null, worker.getX(), worker.getEyeY(), worker.getZ(), Serfdom.HUM.get(), SoundSource.NEUTRAL, VOLUME, cue.pitch());
        }
        singing.sung = at;
        if (at >= LENGTHS[singing.phrase]) stop(worker, now);
    }

    /** effects: the worker waits or its shift is over: any phrase it was singing ends here. */
    public static void stop(Villager worker, long now) {
        if (SINGING.remove(worker.getUUID()) != null) LAST_END.put(worker.getUUID(), now);
    }

    /** effects: true iff the worker is singing a phrase now. */
    public static boolean singing(Villager worker) { return SINGING.containsKey(worker.getUUID()); }
}
