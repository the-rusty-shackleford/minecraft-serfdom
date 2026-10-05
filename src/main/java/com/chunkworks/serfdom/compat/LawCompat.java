/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import com.chunkworks.serfdom.Remedies;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;

/** Village Law's cases (D-0003). A capture an officer saw opens a case with the village, or joins
 * the open one; Serfdom asks which right after committing the crime, and owes the captive to that
 * case. When the case is paid, the captives it is owed go free; when the player flees, it forgets
 * them. Needs Village Law 1.1 for its question; without it no capture is owed to any case. */
public final class LawCompat {
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("villagelaw") && present();
    private LawCompat() {}

    private static boolean present() {
        try {
            Class.forName("com.chunkworks.villagelaw.api.Cases", false, LawCompat.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** effects: whether Village Law is here and can be asked. */
    public static boolean loaded() { return LOADED; }

    /** effects: the id of the village whose open case the player's crime where they stand belongs
     * to; empty without one, or without Village Law. */
    public static Optional<String> openHere(ServerPlayer player) { return LOADED ? Inner.openHere(player) : Optional.empty(); }

    /** effects: listens for the law's settlements, when Village Law is here. */
    public static void listen() { if (LOADED) Inner.listen(); }

    private static final class Inner {
        static Optional<String> openHere(ServerPlayer player) {
            return com.chunkworks.villagelaw.api.Cases.openHere(player).map(Object::toString);
        }
        static void listen() {
            NeoForge.EVENT_BUS.addListener((com.chunkworks.villagelaw.api.CaseSettledEvent e) -> {
                var village = e.village().toString();
                switch (e.settlement()) {
                    case PAID -> Remedies.paid(e.player(), village);
                    case FLED -> Remedies.fled(e.player(), village);
                }
            });
        }
    }
}
