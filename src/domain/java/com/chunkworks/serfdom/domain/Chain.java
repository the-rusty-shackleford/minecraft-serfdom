/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.domain;

import java.util.Objects;

/** What a player's use of a villager does where the chain is concerned (D-0003). A villager with
 * the chain on it is cuffed: it goes where the chain's holder leads it, and stands still when
 * nobody holds the chain. Anyone may put the chain on an owned villager and lead it; only its owner
 * takes the chain off. The chain on a free villager is the capture.
 *
 * <p>The gestures, by what is in the hand:
 * <ul>
 * <li>the chain: on a free villager, the capture; on an owned one not cuffed, cuffs it; on a cuffed
 * one, as the empty hand below, sneaking or not;</li>
 * <li>the empty hand, not sneaking: on a cuffed villager nobody holds, takes its chain; on one the
 * player holds, the owner uncuffs it and anyone else lets go; on one another player holds, says
 * so; on an owned villager not cuffed or a free one, nothing of the chain's;</li>
 * <li>sneaking with anything but the chain on an owned villager: the Worker Screen for its owner,
 * the owner's name for anyone else (D-0001);</li>
 * <li>anything else on a cuffed villager: refused, since a villager in chains does not trade.</li>
 * </ul> */
public final class Chain {
    private Chain() {}

    /** What is in the player's hand. */
    public enum Hand { EMPTY, CHAIN, OTHER }

    /** Who holds a cuffed villager's chain, as the acting player sees it. Nobody also stands for a
     * fence it is tied to. */
    public enum Holder { NOBODY, ACTOR, SOMEONE_ELSE }

    /** The facts of one use.
     * <p>Rep invariant: a free villager is neither cuffed nor the actor's; only a cuffed villager is
     * held. */
    public record Facts(boolean owned, boolean cuffed, Holder holder, boolean actorOwns, Hand hand, boolean sneaking) {
        public Facts {
            Objects.requireNonNull(holder);
            Objects.requireNonNull(hand);
            if (!owned && (cuffed || actorOwns)) throw new IllegalArgumentException("a free villager is neither cuffed nor anyone's");
            if (!cuffed && holder != Holder.NOBODY) throw new IllegalArgumentException("only a cuffed villager is held");
        }
    }

    /** What the use does. */
    public enum Act {
        /** Nothing of the chain's: the use goes on to vanilla (trading, the hire offer). */
        PASS,
        /** The capture begins: the player holds the chain on a free villager. */
        CAPTURE,
        /** The chain goes on an owned villager and the player leads it. */
        CUFF,
        /** The player takes the chain of a cuffed villager nobody holds. */
        TAKE,
        /** The owner takes the chain off; it comes back to them. */
        UNCUFF,
        /** The player lets go of a villager someone else owns; it stays cuffed. */
        LET_GO,
        /** Someone else holds its chain: the player is told so. */
        HELD,
        /** The Worker Screen, for the owner. */
        SCREEN,
        /** The owner's name, for anyone else. */
        WHOSE,
        /** A villager in chains does not trade: the use is taken and does nothing. */
        REFUSE
    }

    /** effects: what the use with these facts does. */
    public static Act gesture(Facts f) {
        if (!f.owned()) return f.hand() == Hand.CHAIN ? Act.CAPTURE : Act.PASS;
        boolean chainGesture = f.hand() == Hand.CHAIN || (f.hand() == Hand.EMPTY && !f.sneaking() && f.cuffed());
        if (chainGesture) {
            if (!f.cuffed()) return Act.CUFF;
            return switch (f.holder()) {
                case NOBODY -> Act.TAKE;
                case ACTOR -> f.actorOwns() ? Act.UNCUFF : Act.LET_GO;
                case SOMEONE_ELSE -> Act.HELD;
            };
        }
        if (f.sneaking()) return f.actorOwns() ? Act.SCREEN : Act.WHOSE;
        return f.cuffed() ? Act.REFUSE : Act.PASS;
    }
}
