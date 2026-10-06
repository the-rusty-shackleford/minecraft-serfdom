/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Purse;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Till;
import com.chunkworks.serfdom.job.RecipeBook;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Every villager's purse (D-0006), saved with it beside its shopping day: how a villager starts
 * (Rusty's call: full when found in the world, at the deposit line when new; NeoForge's finalize
 * event names how it was made, since a villager generated with its chunk does not join as loaded
 * from disk), the trades with players
 * that pay from it and into it, the morning deposit, and the hire fee. The rules are the domain's
 * ({@link Purse}, {@link Till}); this reads and writes them on the villager with the server's
 * settings. Wandering traders are not villagers and have none. */
public final class Purses {
    private Purses() {}

    /** A villager's purse and its shopping day, as saved. Immutable. */
    public record Saved(Purse purse, Shopping.Day day) {
        public static final Saved EMPTY = new Saved(new Purse(0, Purse.NEVER), Shopping.Day.NONE);
        public Saved { Objects.requireNonNull(purse); Objects.requireNonNull(day); }
        public static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("emeralds").forGetter(s -> s.purse().emeralds()),
                Codec.LONG.optionalFieldOf("last_morning", Purse.NEVER).forGetter(s -> s.purse().lastMorning()),
                Codec.LONG.optionalFieldOf("trip_day", Shopping.NEVER).forGetter(s -> s.day().tripDay()),
                Codec.LONG.optionalFieldOf("sales_day", Shopping.NEVER).forGetter(s -> s.day().salesDay()),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("sales", 0).forGetter(s -> s.day().sales()),
                Codec.LONG.optionalFieldOf("seen_day", Shopping.NEVER).forGetter(s -> s.day().seenDay()),
                Codec.LONG.listOf().optionalFieldOf("seen", java.util.List.of()).forGetter(s -> java.util.List.copyOf(s.day().seen()))
        ).apply(i, (e, m, t, sd, s, vd, v) -> new Saved(new Purse(e, m), new Shopping.Day(t, sd, s, vd, java.util.Set.copyOf(v)))));
        public Saved with(Purse p) { return new Saved(p, day); }
        public Saved with(Shopping.Day d) { return new Saved(purse, d); }
    }

    /** The server's purse, sent to a player who trades with a villager, so the trade screen can show
     * it. */
    public record PurseView(int entity, int emeralds) implements CustomPacketPayload {
        public static final Type<PurseView> TYPE = new Type<>(Serfdom.id("purse_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PurseView> CODEC = StreamCodec.of(
                (buf, v) -> { buf.writeVarInt(v.entity); buf.writeVarInt(v.emeralds); },
                buf -> new PurseView(buf.readVarInt(), buf.readVarInt()));
        @Override public Type<PurseView> type() { return TYPE; }
    }

    /** effects: true iff the economy is on (its setting, or before the settings load). */
    public static boolean on() { return !SerfdomConfig.SPEC.isLoaded() || SerfdomConfig.ECONOMY.get(); }

    /** effects: the purse's rules from the server's settings. */
    public static Purse.Rules rules() {
        if (!SerfdomConfig.SPEC.isLoaded()) return Purse.Rules.DEFAULT;
        return new Purse.Rules(SerfdomConfig.PURSE_CAP.get(), SerfdomConfig.DEPOSIT.get(), SerfdomConfig.DEPOSIT_BELOW.get());
    }

    /** effects: the villager's purse and shopping day; empty for one that has none yet. */
    public static Saved of(Villager villager) { return villager.getExistingData(Serfdom.PURSE).orElse(Saved.EMPTY); }

    public static void set(Villager villager, Saved saved) { villager.setData(Serfdom.PURSE, saved); }

    /** effects: the emeralds in the villager's purse. */
    public static int emeralds(Villager villager) { return of(villager).purse().emeralds(); }

    /** requires: n &ge; 0. effects: {@code n} emeralds into the villager's purse, past the cap lost
     * (the hire fee, D-0006). */
    public static void receive(Villager villager, int n) {
        var s = of(villager);
        set(villager, s.with(s.purse().takeIn(n, rules())));
    }

    /** effects: true iff the villager is one the deposit and the shops are for: grown, free or hired,
     * never a captive. */
    public static boolean citizen(Villager villager) { return !villager.isBaby() && !Workers.of(villager).captive(); }

    /** effects: a villager as it is made (Rusty's call): one a village's structure places, as a chunk
     * is generated, starts full; one born, cured, hatched from an egg, summoned or spawned starts at the
     * deposit line. Every spawn but a structure's is new. Set whatever it had: a cure finalizes the
     * villager after it has joined its level. */
    static void finalized(FinalizeSpawnEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        boolean found = event.getSpawnType() == MobSpawnType.STRUCTURE;
        set(villager, new Saved(Purse.start(found, rules()), Shopping.Day.NONE));
    }

    /** effects: a villager joining a level without a purse gets a full one: it was in the world before
     * purses (Rusty's call). A stack it only showed in its hand is taken away. */
    static void joined(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager)) return;
        if (!villager.hasData(Serfdom.PURSE)) set(villager, new Saved(Purse.start(true, rules()), Shopping.Day.NONE));
        Baskets.clearShown(villager);
    }

    /** effects: in a morning the villager has not been seen in, it is seen: a free or hired adult
     * under the line gets the deposit, and a free villager's household uses its morning's share. */
    public static void morning(ServerLevel level, Villager villager) {
        if (!on()) return;
        var s = of(villager);
        long now = level.getDayTime();
        if (!s.purse().newMorning(now)) return;
        set(villager, s.with(s.purse().morning(now, citizen(villager), rules())));
        if (!villager.isBaby() && !Workers.owned(villager)) Households.morning(level, villager);
    }

    // ---- trading with players ---------------------------------------------------------------------

    /** effects: the till of an offer: what it pays the player in emeralds and what it takes, after any
     * discount. */
    public static Till till(MerchantOffer offer) {
        var b = offer.getCostB();
        return Till.of(key(offer.getCostA()), offer.getCostA().getCount(), b.isEmpty() ? "" : key(b), b.getCount(), key(offer.getResult()), offer.getResult().getCount());
    }

    private static String key(ItemStack stack) { return RecipeBook.key(stack.getItem()); }

    /** effects: as {@code player} starts trading with the villager, closes each trade its purse can't
     * pay, and shows the player the purse. */
    public static void open(Villager villager, Player player) {
        if (villager.level().isClientSide()) return;
        if (!on()) { close(villager); return; }
        mark(villager);
        if (player instanceof ServerPlayer sp) show(sp, villager);
    }

    /** effects: every trade of the villager open again: nobody is trading. */
    public static void close(Villager villager) {
        if (villager.level().isClientSide()) return;
        for (var offer : villager.getOffers()) ((ClosedOffer) offer).serfdom$close(false);
    }

    private static void mark(Villager villager) {
        var purse = of(villager).purse();
        for (var offer : villager.getOffers()) ((ClosedOffer) offer).serfdom$close(!till(offer).open(purse));
    }

    /** effects: after a trade, the purse pays and takes what it moved (past the cap lost), the trades
     * are closed again as the purse now stands, and the trading player is shown both. */
    static void traded(TradeWithVillagerEvent event) {
        if (!on() || !(event.getAbstractVillager() instanceof Villager villager) || villager.level().isClientSide()) return;
        var till = till(event.getMerchantOffer());
        if (till.none()) return;
        var s = of(villager);
        // A closed trade never fills its result, so a trade that happened was open.
        if (till.open(s.purse())) set(villager, s.with(till.after(s.purse(), rules())));
        mark(villager);
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof MerchantMenu menu) {
            player.sendMerchantOffers(menu.containerId, villager.getOffers(), villager.getVillagerData().getLevel(), villager.getVillagerXp(),
                    villager.showProgressBar(), villager.canRestock());
            show(player, villager);
        }
    }

    private static void show(ServerPlayer player, Villager villager) {
        var view = new PurseView(villager.getId(), emeralds(villager));
        if (player.connection != null && player.connection.hasChannel(view.type())) PacketDistributor.sendToPlayer(player, view);
    }

    public static void listen() {
        NeoForge.EVENT_BUS.addListener(Purses::joined);
        NeoForge.EVENT_BUS.addListener(Purses::finalized);
        NeoForge.EVENT_BUS.addListener(Purses::traded);
    }
}
