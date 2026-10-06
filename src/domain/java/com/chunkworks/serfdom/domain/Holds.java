/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Who holds which place of work (D-0008): a farm's plot or a tree, held by one worker at a time so
 * that no other works it meanwhile. A hold lasts while its worker renews it and lapses
 * {@link #LAPSE} ticks after the worker last did: it died, was unloaded, or stopped without letting
 * go. Then any worker may take the place. Mutable.
 *
 * <p>Abstraction function: at time t, place p is held by {@code holds.get(p).worker()} when
 * {@code holds} has p and t - {@code holds.get(p).renewed()} &lt; LAPSE; every other place is free.
 *
 * <p>Rep invariant: no key or value is null.
 *
 * <p>Safety from rep exposure: {@code holds} is private and never handed out; a {@code Hold} is an
 * immutable record; places and workers are taken as given and should be immutable.
 *
 * @param <P> what a place is */
public final class Holds<P> {
    /** The ticks a hold lasts after its worker last renewed it: ten seconds. */
    public static final long LAPSE = 200;
    /** Every this many holds taken, the lapsed ones are forgotten. */
    private static final int PRUNE_EVERY = 1024;

    private record Hold(UUID worker, long renewed) {}
    private final Map<P, Hold> holds = new HashMap<>();
    private int sincePrune;

    private void checkRep() {
        for (var e : holds.entrySet()) assert e.getKey() != null && e.getValue() != null && e.getValue().worker() != null;
    }

    private static boolean live(Hold h, long now) { return now - h.renewed() < LAPSE; }

    /** effects: {@code worker} holds {@code place} from {@code now}: taken when it is free or its hold
     * lapsed, renewed when it is already the worker's; true. False, and nothing changed, when another
     * worker holds it. */
    public boolean hold(P place, UUID worker, long now) {
        Objects.requireNonNull(place);
        Objects.requireNonNull(worker);
        var h = holds.get(place);
        if (h != null && live(h, now) && !h.worker().equals(worker)) return false;
        holds.put(place, new Hold(worker, now));
        if (++sincePrune >= PRUNE_EVERY) prune(now);
        checkRep();
        return true;
    }

    /** effects: the worker holding {@code place} at {@code now}, if one does. */
    public Optional<UUID> holder(P place, long now) {
        var h = holds.get(place);
        return h != null && live(h, now) ? Optional.of(h.worker()) : Optional.empty();
    }

    /** effects: true iff a worker other than {@code worker} holds {@code place} at {@code now}. */
    public boolean heldByAnother(P place, UUID worker, long now) {
        return holder(place, now).filter(w -> !w.equals(worker)).isPresent();
    }

    /** effects: {@code place} is free when {@code worker} held it, lapsed or not; otherwise nothing
     * changes. */
    public void release(P place, UUID worker) {
        var h = holds.get(place);
        if (h != null && h.worker().equals(worker)) holds.remove(place);
        checkRep();
    }

    /** effects: every place {@code worker} holds, lapsed or not, is free. */
    public void releaseAll(UUID worker) {
        holds.values().removeIf(h -> h.worker().equals(worker));
        checkRep();
    }

    /** effects: forgets the holds lapsed at {@code now}; who holds what is unchanged. */
    public void prune(long now) {
        holds.values().removeIf(h -> !live(h, now));
        sincePrune = 0;
        checkRep();
    }

    /** effects: how many places are held at {@code now}. */
    public int held(long now) { return (int) holds.values().stream().filter(h -> live(h, now)).count(); }
}
