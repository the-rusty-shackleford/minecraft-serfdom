/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The economy module (D-0006): purses, prices, needs, stalls, baskets. Its listeners, its data, the
 * stall's handler for hoppers, and its payloads. */
public final class Market {
    private Market() {}

    /** requires: the mod bus. effects: the module's listeners, data and capabilities registered. */
    public static void listen(IEventBus modBus) {
        Purses.listen();
        Stalls.listen();
        Baskets.listen();
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> { e.addListener(new Prices()); e.addListener(new Needs()); });
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> Prices.build(e.getServer().overworld()));
        modBus.addListener((RegisterCapabilitiesEvent e) ->
                e.registerBlockEntity(Capabilities.ItemHandler.BLOCK, Serfdom.FOR_SALE_ENTITY.get(), (stall, side) -> stall.handler));
    }

    /** effects: the module's payloads registered. */
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Purses.PurseView.TYPE, Purses.PurseView.CODEC, (v, ctx) -> Client.purses.accept(v));
        Stalls.register(registrar);
    }

    /** The client's receivers, set by the client at setup. */
    public static final class Client {
        private static java.util.function.Consumer<Purses.PurseView> purses = v -> {};
        private Client() {}
        public static void receivers(java.util.function.Consumer<Purses.PurseView> p, java.util.function.Consumer<Stalls.LedgerView> l) {
            purses = java.util.Objects.requireNonNull(p);
            Stalls.Client.receiver(l);
        }
    }
}
