/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

/** A trade a villager's purse can't pay for (D-0006), as every {@code MerchantOffer} carries it
 * (mixed in): while it is closed the offer reads as sold out, so the trade screen draws its cross and
 * its result slot stays empty, and nothing else about the offer changes. Set while a player trades,
 * cleared when they stop. */
public interface ClosedOffer {
    void serfdom$close(boolean closed);
    boolean serfdom$closed();
}
