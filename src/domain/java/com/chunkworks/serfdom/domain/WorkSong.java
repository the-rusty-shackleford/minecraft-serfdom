/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.ArrayList;
import java.util.List;

/** The captives' work song (the spec's §3, D-0003): a few short phrases in a slow minor key, in the
 * manner of a burlak (Volga boatmen) song and ours, sung one note at a time in the villager's own
 * hum played at the note's pitch. A captive at work begins a phrase now and then, never sooner than
 * {@link #MIN_GAP} ticks after the last one ended, {@link #MEAN_GAP} apart on average.
 *
 * <p>Pitch is the game's playback rate: 1.0 is the hum as recorded (about 95 Hz once it settles),
 * and the game holds it to [0.5, 2.0]. */
public final class WorkSong {
    /** The playback rate of the tonic. */
    public static final float TONIC = 1.0F;
    /** The game's bounds on a sound's playback rate. */
    public static final float LOWEST = 0.5F, HIGHEST = 2.0F;
    /** Ticks in one beat: 75 beats a minute. */
    public static final int TICKS_PER_BEAT = 16;
    /** The fewest ticks between the end of one phrase and the start of the next. */
    public static final int MIN_GAP = 400;
    /** The mean number of ticks from the end of one phrase to the start of the next. */
    public static final int MEAN_GAP = 900;
    private WorkSong() {}

    /** One sung note: {@code semitones} above the tonic (below when negative), held {@code beats}.
     * <p>Rep invariant: beats &ge; 1. */
    public record Note(int semitones, int beats) {
        public Note { if (beats < 1) throw new IllegalArgumentException("beats " + beats); }
    }

    /** One note as the game plays it: {@code tick} after the phrase begins, at {@code pitch}. */
    public record Cue(int tick, float pitch) {}

    private static Note n(int semitones, int beats) { return new Note(semitones, beats); }

    /** The phrases, in natural minor about the tonic: a call, its answer, a low heave and a lament,
     * each coming home to the tonic. */
    public static final List<List<Note>> PHRASES = List.of(
            List.of(n(-5, 1), n(0, 1), n(0, 1), n(3, 2), n(2, 1), n(0, 2)),
            List.of(n(3, 1), n(5, 1), n(7, 2), n(5, 1), n(3, 1), n(2, 1), n(0, 3)),
            List.of(n(0, 2), n(-2, 1), n(-5, 1), n(-5, 2), n(-2, 1), n(0, 3)),
            List.of(n(7, 2), n(8, 1), n(7, 1), n(3, 2), n(5, 1), n(2, 1), n(0, 4)));

    /** effects: the playback rate of a note {@code semitones} from the tonic, in equal temperament,
     * held to the game's bounds. */
    public static float pitch(int semitones) {
        return (float) Math.clamp(TONIC * Math.pow(2.0, semitones / 12.0), LOWEST, HIGHEST);
    }

    /** effects: the phrase's notes as the game plays them, each beginning where the one before it
     * ends. */
    public static List<Cue> cues(List<Note> phrase) {
        var out = new ArrayList<Cue>(phrase.size());
        int tick = 0;
        for (var note : phrase) {
            out.add(new Cue(tick, pitch(note.semitones())));
            tick += note.beats() * TICKS_PER_BEAT;
        }
        return List.copyOf(out);
    }

    /** effects: the ticks the phrase lasts, to the end of its last note. */
    public static int length(List<Note> phrase) {
        int beats = 0;
        for (var note : phrase) beats += note.beats();
        return beats * TICKS_PER_BEAT;
    }

    /** requires: 0 &le; draw &lt; 1.
     * effects: true iff a captive at work, {@code sinceLast} ticks after its last phrase ended,
     * begins one now on a uniform {@code draw}: never before {@link #MIN_GAP}, and after it with the
     * chance that makes the wait {@link #MEAN_GAP} on average. */
    public static boolean begins(long sinceLast, double draw) {
        if (!(draw >= 0 && draw < 1)) throw new IllegalArgumentException("draw " + draw);
        return sinceLast >= MIN_GAP && draw < 1.0 / (MEAN_GAP - MIN_GAP);
    }
}
