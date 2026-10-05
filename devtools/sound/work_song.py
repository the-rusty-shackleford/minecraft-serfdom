# Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later
"""Renders the captives' work song (D-0003) to a WAV file, to be heard before it ships.

The game sings each note as the villager's own hum (vanilla's mob/villager/idle2), played at the
note's pitch: OpenAL's pitch is a playback rate, so a note at pitch p is the sample resampled to
1/p of its length. This does the same, note by note, at each cue's tick (20 ticks a second), the
phrases two seconds apart. The phrases, the tonic and the beat are read from WorkSong.java, so the
clip is the song the code sings.

The vanilla sample is read from the local Minecraft assets and only mixed into the output, which is
written under run/ (never committed: it carries Mojang's sound).

    uv run --no-project --with numpy python devtools/sound/work_song.py [out.wav]
"""
import json
import os
import re
import subprocess
import sys
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
SONG = os.path.join(REPO, "src/domain/java/com/chunkworks/serfdom/domain/WorkSong.java")
ASSETS = os.path.expanduser("~/.gradle/caches/neoformruntime/assets")
RATE = 44100
TICK = 1 / 20


def song():
    """The tonic, the ticks per beat and the phrases, as WorkSong.java has them."""
    text = open(SONG).read()
    tonic = float(re.search(r"TONIC = ([0-9.]+)F", text).group(1))
    beat = int(re.search(r"TICKS_PER_BEAT = (\d+)", text).group(1))
    body = text[text.index("PHRASES = List.of("):]
    groups = re.findall(r"List\.of\(((?:n\(-?\d+, \d+\)(?:, )?)+)\)", body)
    phrases = [[(int(s), int(b)) for s, b in re.findall(r"n\((-?\d+), (\d+)\)", g)] for g in groups]
    return tonic, beat, phrases


def hum():
    """Vanilla's villager idle2, as mono floats at RATE."""
    index = json.load(open(os.path.join(ASSETS, "indexes/17.json")))["objects"]
    digest = index["minecraft/sounds/mob/villager/idle2.ogg"]["hash"]
    path = os.path.join(ASSETS, "objects", digest[:2], digest)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.int16).astype(np.float64) / 32768


def at_pitch(sample, pitch):
    """The sample played at a playback rate: shorter and higher above 1, longer and lower below."""
    n = int(len(sample) / pitch)
    return np.interp(np.arange(n) * pitch, np.arange(len(sample)), sample)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "run/work_song.wav")
    tonic, beat, phrases = song()
    sample = hum()
    gap = 2.0
    pieces, start = [], 0.0
    for phrase in phrases:
        tick = 0
        for semitones, beats in phrase:
            pitch = min(2.0, max(0.5, tonic * 2 ** (semitones / 12)))
            pieces.append((start + tick * TICK, at_pitch(sample, pitch)))
            tick += beats * beat
        start += tick * TICK + gap
    mix = np.zeros(int((start + 1) * RATE))
    for when, note in pieces:
        i = int(when * RATE)
        mix[i:i + len(note)] += note[: len(mix) - i]
    mix *= 0.8 / max(1e-9, np.abs(mix).max())
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((mix * 32767).astype(np.int16).tobytes())
    print(f"{len(phrases)} phrases, {len(pieces)} notes, {start:.1f} s -> {out}")


if __name__ == "__main__":
    main()
