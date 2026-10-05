/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Optional;
import java.util.UUID;

/** Whose a newborn villager is (the spec's §5, D-0003): a child of two villagers with the same owner
 * is that owner's, hired; any other child is free, as vanilla's are. */
public final class Birth {
    private Birth() {}

    /** effects: the owner of a child whose parents have these owners: the one they share, else none. */
    public static Optional<UUID> owner(Optional<UUID> mother, Optional<UUID> father) {
        return mother.isPresent() && mother.equals(father) ? mother : Optional.empty();
    }
}
