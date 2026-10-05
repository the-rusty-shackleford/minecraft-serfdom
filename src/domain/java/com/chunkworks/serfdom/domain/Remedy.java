/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** The captives each open case of the law is owed (D-0003). A capture an officer saw joins the
 * player's case with the village; when that case is paid, every captive it is owed goes free, and
 * when the player flees, the case forgets them and they stay taken. Immutable.
 *
 * <p>Abstraction function: for each (player, village) key, the captives the player's open case
 * with the village would free if paid now.
 * <p>Rep invariant: no key maps to an empty set; no captive is owed under two keys. */
public final class Remedy {
    /** One player's case with one village. */
    public record Case(UUID player, String village) {
        public Case { Objects.requireNonNull(player); Objects.requireNonNull(village); }
    }

    /** What a payment frees, and the ledger after it. */
    public record Paid(Set<UUID> freed, Remedy after) {
        public Paid { freed = Set.copyOf(freed); Objects.requireNonNull(after); }
    }

    public static final Remedy EMPTY = new Remedy(Map.of());
    private final Map<Case, Set<UUID>> owed;

    private Remedy(Map<Case, Set<UUID>> owed) {
        var copy = new HashMap<Case, Set<UUID>>();
        var seen = new HashSet<UUID>();
        for (var e : owed.entrySet()) {
            if (e.getValue().isEmpty()) throw new IllegalArgumentException("an empty entry for " + e.getKey());
            for (var captive : e.getValue()) if (!seen.add(captive)) throw new IllegalArgumentException(captive + " is owed twice");
            copy.put(e.getKey(), Set.copyOf(e.getValue()));
        }
        this.owed = Map.copyOf(copy);
    }

    /** effects: a ledger holding exactly {@code owed}. Throws IllegalArgumentException when it
     * breaks the rep invariant. */
    public static Remedy of(Map<Case, Set<UUID>> owed) { return new Remedy(owed); }

    /** effects: every case and what it is owed. */
    public Map<Case, Set<UUID>> owed() { return owed; }

    /** effects: the captives {@code c} is owed; empty when none. */
    public Set<UUID> owedTo(Case c) { return owed.getOrDefault(c, Set.of()); }

    /** effects: this ledger with {@code captive} owed to {@code c}, and to no other case. */
    public Remedy owe(Case c, UUID captive) {
        var next = mutable(forget(captive));
        next.computeIfAbsent(c, k -> new HashSet<>()).add(captive);
        return new Remedy(next);
    }

    /** effects: the payment of {@code c}: what it frees, and the ledger without it. */
    public Paid paid(Case c) {
        var next = mutable(this);
        var freed = next.remove(c);
        return new Paid(freed == null ? Set.of() : freed, new Remedy(next));
    }

    /** effects: this ledger once the player has fled {@code c}: the case is owed nothing more, so a
     * debt paid after it frees nobody. */
    public Remedy fled(Case c) {
        if (!owed.containsKey(c)) return this;
        var next = mutable(this);
        next.remove(c);
        return new Remedy(next);
    }

    /** effects: this ledger with {@code captive} owed nowhere: it died, escaped or was set free. */
    public Remedy forget(UUID captive) {
        var next = new HashMap<Case, Set<UUID>>();
        boolean changed = false;
        for (var e : owed.entrySet()) {
            if (e.getValue().contains(captive)) {
                changed = true;
                var rest = new HashSet<>(e.getValue());
                rest.remove(captive);
                if (!rest.isEmpty()) next.put(e.getKey(), rest);
            } else next.put(e.getKey(), e.getValue());
        }
        return changed ? new Remedy(next) : this;
    }

    private static Map<Case, Set<UUID>> mutable(Remedy r) {
        var m = new HashMap<Case, Set<UUID>>();
        for (var e : r.owed.entrySet()) m.put(e.getKey(), new HashSet<>(e.getValue()));
        return m;
    }

    @Override public boolean equals(Object o) { return o instanceof Remedy r && r.owed.equals(owed); }
    @Override public int hashCode() { return owed.hashCode(); }
    @Override public String toString() { return "Remedy" + owed; }
}
