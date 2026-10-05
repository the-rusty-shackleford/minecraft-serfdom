/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.domain.Ledger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** For Sale blocks as the server knows them (D-0006): finding the ones near a bed through the game's
 * index of points of interest, opening one's screen with its ledger, the owner lock against breaking
 * and explosions, and what a stall says it sells. */
public final class Stalls {
    private Stalls() {}

    /** A stall's ledger, sent to its owner with its screen and whenever a visit is written while it is
     * open. */
    public record LedgerView(BlockPos pos, Ledger ledger) implements CustomPacketPayload {
        public static final Type<LedgerView> TYPE = new Type<>(Serfdom.id("ledger_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LedgerView> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LedgerView::pos, ByteBufCodecs.fromCodec(ForSaleBlockEntity.LEDGER_CODEC), LedgerView::ledger, LedgerView::new);
        @Override public Type<LedgerView> type() { return TYPE; }
    }

    /** The client's ledger screen, set by the client at setup. */
    public static final class Client {
        private static Consumer<LedgerView> ledgers = v -> {};
        private Client() {}
        public static void receiver(Consumer<LedgerView> l) { ledgers = Objects.requireNonNull(l); }
        static void accept(LedgerView v) { ledgers.accept(v); }
    }

    /** effects: the loaded stalls whose block lies within {@code reach} blocks of {@code at}, nearest
     * first. */
    public static List<ForSaleBlockEntity> near(ServerLevel level, BlockPos at, int reach) {
        var out = new ArrayList<ForSaleBlockEntity>();
        level.getPoiManager().findAll(h -> h.is(Serfdom.FOR_SALE_POI.getKey()), p -> true, at, reach, PoiManager.Occupancy.ANY).forEach(pos -> {
            if (pos.distSqr(at) <= (double) reach * reach && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ForSaleBlockEntity stall) out.add(stall);
        });
        out.sort(Comparator.comparingDouble(s -> s.getBlockPos().distSqr(at)));
        return out;
    }

    /** effects: opens the stall's screen for its owner and sends its ledger. */
    public static void open(ServerPlayer player, ForSaleBlockEntity stall) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ForSaleMenu(id, inventory, stall), Component.translatable("block.serfdom.for_sale")));
        send(player, new LedgerView(stall.getBlockPos(), stall.ledger()));
    }

    /** effects: every player with the stall's screen open sees its ledger as it is now. */
    static void ledgerChanged(ServerLevel level, ForSaleBlockEntity stall) {
        for (var p : level.players())
            if (p.containerMenu instanceof ForSaleMenu menu && menu.stall() == stall) send(p, new LedgerView(stall.getBlockPos(), stall.ledger()));
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    /** effects: what a stall sells, in words: "8 Bread for 1 emerald", "Sold out", or "Nothing for
     * sale". Either side. */
    public static Component label(ForSaleBlockEntity stall) { return label(stall.template(), stall.quantity(), stall.price(), stall.getLevel() != null && stall.getLevel().isClientSide ? stall.clientOpen() : stall.stall().open()); }

    public static Component label(net.minecraft.world.item.ItemStack sells, int quantity, int price, boolean open) {
        if (sells.isEmpty()) return Component.translatable("screen.serfdom.stall.nothing");
        if (!open) return Component.translatable("screen.serfdom.stall.sold_out", sells.getHoverName());
        return Component.translatable(price == 1 ? "screen.serfdom.stall.offer.one" : "screen.serfdom.stall.offer", quantity, sells.getHoverName(), price);
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
            if (e.getLevel().getBlockEntity(e.getPos()) instanceof ForSaleBlockEntity stall && !stall.ownedBy(e.getPlayer().getUUID())) {
                e.setCanceled(true);
                e.getPlayer().displayClientMessage(Component.translatable("message.serfdom.someones_stall", stall.ownerName(), label(stall)).withStyle(ChatFormatting.GRAY), true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> e.getAffectedBlocks().removeIf(p -> e.getLevel().getBlockState(p).is(Serfdom.FOR_SALE.get())));
    }

    static void register(net.neoforged.neoforge.network.registration.PayloadRegistrar registrar) {
        registrar.playToClient(LedgerView.TYPE, LedgerView.CODEC, (v, ctx) -> Client.accept(v));
    }
}
