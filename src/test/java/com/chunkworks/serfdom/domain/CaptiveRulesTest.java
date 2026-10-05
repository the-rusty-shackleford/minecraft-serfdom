/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Birth, the captive's pace and the work song (D-0003), each at its boundaries. */
final class CaptiveRulesTest {
    // Birth. Partitions: both parents owned by one owner, by two, one owned, neither.
    private static final Optional<UUID> ANN = Optional.of(new UUID(1, 1)), BOB = Optional.of(new UUID(2, 2));
    @Test void aChildIsTheOwnersOnlyWhenBothParentsAreTheirs() {
        assertEquals(ANN, Birth.owner(ANN, Optional.of(new UUID(1, 1))));
        assertEquals(Optional.empty(), Birth.owner(ANN, BOB), "two owners: free");
        assertEquals(Optional.empty(), Birth.owner(ANN, Optional.empty()), "one free parent: free");
        assertEquals(Optional.empty(), Birth.owner(Optional.empty(), ANN));
        assertEquals(Optional.empty(), Birth.owner(Optional.empty(), Optional.empty()));
    }

    // Pace. Partitions: matching or not × captive or not; no slowdown, the default, bad slowdowns.
    @Test void theCaptivesSlowdownStacksWithTheBonus() {
        assertEquals(1.0, Pace.speed(false, false, 0.1), 1e-12);
        assertEquals(1.25, Pace.speed(true, false, 0.1), 1e-12);
        assertEquals(0.9, Pace.speed(false, true, Pace.CAPTIVE_SLOWDOWN), 1e-12);
        assertEquals(1.125, Pace.speed(true, true, Pace.CAPTIVE_SLOWDOWN), 1e-12, "1.25 × 0.9");
        assertEquals(1.25, Pace.speed(true, true, 0.0), 1e-12, "no slowdown");
    }
    @Test void aBadSlowdownIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Pace.speed(true, true, -0.01));
        assertThrows(IllegalArgumentException.class, () -> Pace.speed(true, true, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Pace.speed(true, true, Double.NaN));
    }

    // The work song. Partitions: the tonic, an octave up and down, past the game's bounds; every
    // phrase's notes in the game's range, ending on the tonic, its cues back to back; the gap before,
    // at and after its least, a draw just under and at the chance.
    @Test void pitchesAreEqualTemperedAboutTheTonicAndHeldToTheGamesBounds() {
        assertEquals(WorkSong.TONIC, WorkSong.pitch(0), 1e-6);
        assertEquals(2.0F, WorkSong.pitch(12), 1e-6);
        assertEquals(0.5F, WorkSong.pitch(-12), 1e-6);
        assertEquals(2.0F, WorkSong.pitch(30), 1e-6, "held at the top");
        assertEquals(0.5F, WorkSong.pitch(-30), 1e-6, "held at the bottom");
        assertEquals(Math.pow(2, 3 / 12.0), WorkSong.pitch(3), 1e-6, "a minor third");
    }
    @Test void everyPhraseStaysInRangeAndComesHomeToTheTonic() {
        assertFalse(WorkSong.PHRASES.isEmpty());
        for (var phrase : WorkSong.PHRASES) {
            assertFalse(phrase.isEmpty());
            assertEquals(0, phrase.get(phrase.size() - 1).semitones(), "ends on the tonic: " + phrase);
            for (var note : phrase) {
                float p = WorkSong.pitch(note.semitones());
                assertTrue(p > WorkSong.LOWEST && p < WorkSong.HIGHEST, "inside the game's range, not clipped: " + note);
            }
            var cues = WorkSong.cues(phrase);
            assertEquals(phrase.size(), cues.size());
            assertEquals(0, cues.get(0).tick());
            for (int i = 1; i < cues.size(); i++)
                assertEquals(cues.get(i - 1).tick() + phrase.get(i - 1).beats() * WorkSong.TICKS_PER_BEAT, cues.get(i).tick(), "each note follows the last");
            var last = phrase.get(phrase.size() - 1);
            assertEquals(cues.get(cues.size() - 1).tick() + last.beats() * WorkSong.TICKS_PER_BEAT, WorkSong.length(phrase));
        }
    }
    @Test void aPhraseBeginsOnlyAfterTheLeastGapAndThenRarely() {
        double p = 1.0 / (WorkSong.MEAN_GAP - WorkSong.MIN_GAP);
        assertFalse(WorkSong.begins(WorkSong.MIN_GAP - 1, 0.0), "too soon, whatever the draw");
        assertTrue(WorkSong.begins(WorkSong.MIN_GAP, 0.0));
        assertTrue(WorkSong.begins(WorkSong.MIN_GAP, Math.nextDown(p)));
        assertFalse(WorkSong.begins(WorkSong.MIN_GAP, p));
        assertFalse(WorkSong.begins(100_000, 0.5));
        assertThrows(IllegalArgumentException.class, () -> WorkSong.begins(500, 1.0));
        assertThrows(IllegalArgumentException.class, () -> WorkSong.begins(500, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new WorkSong.Note(0, 0));
    }
}
