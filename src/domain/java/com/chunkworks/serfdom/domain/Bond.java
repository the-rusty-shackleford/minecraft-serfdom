/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

/** What binds an owned villager to its owner (the spec's §1, D-0003): paid for, or taken. A captive
 * is never hired: the bond is set when the villager is owned and kept until it is free again. */
public enum Bond {
    /** Hired for a fee (D-0001), or born at the base to the owner's workers. */
    HIRED,
    /** Taken with the chain (D-0003). */
    CAPTIVE
}
