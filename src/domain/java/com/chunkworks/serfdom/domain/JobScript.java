/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** A job as data (D-0001, D-0002): find a target of a kind, act on it, deposit. What a target kind
 * is and how it is worked are code's fixed set; which tool, which radii, which professions work
 * faster, and for a workshop which stations it works at and which duties it has beyond its list,
 * are the data file's. Immutable.
 *
 * <p>Rep invariant: id is not blank; every set is unmodifiable and holds no blank names; a
 * workshop has at least one station, and only a workshop has stations or duties. */
public record JobScript(String id, Optional<String> toolTag, Target target, Radius radius, Set<String> bonus, Set<String> bonusFallback,
                        Set<Station> stations, Set<Workshop.Duty> duties) {
    /** The target kinds code knows how to find and work. */
    public enum Target {
        /** A natural tree whose trunk base is in the area: felled whole, replanted. */
        TREE,
        /** A ripe crop on farmland in the area: harvested, replanted from the harvest. */
        CROP,
        /** The post's stock list, kept filled at the stations in the area (D-0002). */
        WORKSHOP;

        /** effects: the kind named {@code name} in a data file ("tree", "crop", "workshop"),
         * ignoring case. */
        public static Optional<Target> named(String name) {
            return Arrays.stream(values()).filter(t -> t.name().equalsIgnoreCase(name)).findFirst();
        }
    }

    public JobScript {
        Objects.requireNonNull(id);
        Objects.requireNonNull(toolTag);
        Objects.requireNonNull(target);
        Objects.requireNonNull(radius);
        if (id.isBlank()) throw new IllegalArgumentException("blank job id");
        if (toolTag.isPresent() && toolTag.get().isBlank()) throw new IllegalArgumentException("blank tool tag in " + id);
        bonus = Set.copyOf(bonus);
        bonusFallback = Set.copyOf(bonusFallback);
        stations = Set.copyOf(stations);
        duties = Set.copyOf(duties);
        if (bonus.stream().anyMatch(String::isBlank) || bonusFallback.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("blank profession in " + id);
        if (target == Target.WORKSHOP && stations.isEmpty()) throw new IllegalArgumentException("a workshop without stations: " + id);
        if (target != Target.WORKSHOP && (!stations.isEmpty() || !duties.isEmpty())) throw new IllegalArgumentException("stations or duties on a " + target + " job: " + id);
    }

    /** effects: a job that works targets in the area, with no stations or duties. */
    public JobScript(String id, Optional<String> toolTag, Target target, Radius radius, Set<String> bonus, Set<String> bonusFallback) {
        this(id, toolTag, target, radius, bonus, bonusFallback, Set.of(), Set.of());
    }

    /** effects: the professions that work this job faster in a game whose registered professions
     * are {@code registered}: the {@link #bonus} ones the game has, or, when it has none of them,
     * the {@link #bonusFallback} ones it has (the spec's woodcutting: a modded woodworker if the
     * pack has one, else the fletcher). */
    public Set<String> bonusIn(Set<String> registered) {
        var present = bonus.stream().filter(registered::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!present.isEmpty()) return present;
        return bonusFallback.stream().filter(registered::contains).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
