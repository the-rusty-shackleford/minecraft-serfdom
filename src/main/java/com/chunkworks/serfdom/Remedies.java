/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.Remedy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** The law's remedy for captures (D-0003), kept with the overworld: the captives each open case is
 * owed ({@link Remedy}), and the captives a payment freed while they were not loaded, freed as they
 * next load. Server side only. */
public final class Remedies extends SavedData {
    private static final SavedData.Factory<Remedies> FACTORY = new SavedData.Factory<>(Remedies::new, Remedies::load);
    private Remedy ledger = Remedy.EMPTY;
    private final Set<UUID> freeOnLoad = new HashSet<>();

    private Remedies() {}

    /** effects: the server's remedies. */
    public static Remedies get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, Serfdom.ID + "_remedies");
    }

    /** effects: the ledger as it stands. */
    public Remedy ledger() { return ledger; }

    /** effects: the captive is owed to the player's open case with the village. */
    public static void owe(ServerPlayer player, String village, UUID captive) {
        var r = get(player.server);
        r.ledger = r.ledger.owe(new Remedy.Case(player.getUUID(), village), captive);
        r.setDirty();
    }

    /** effects: the captive is owed to no case: it died, escaped or was set free. */
    public static void forget(MinecraftServer server, UUID captive) {
        var r = get(server);
        var next = r.ledger.forget(captive);
        boolean dropped = r.freeOnLoad.remove(captive);
        if (next != r.ledger || dropped) {
            r.ledger = next;
            r.setDirty();
        }
    }

    /** effects: the player paid their case with the village: every captive it is owed goes free and
     * walks home, its chain confiscated; one not loaded now goes free as it next loads. */
    public static void paid(ServerPlayer player, String village) {
        var r = get(player.server);
        var paid = r.ledger.paid(new Remedy.Case(player.getUUID(), village));
        r.ledger = paid.after();
        r.setDirty();
        for (var captive : paid.freed()) {
            var loaded = find(player.server, captive);
            if (loaded == null) { r.freeOnLoad.add(captive); continue; }
            player.sendSystemMessage(Component.translatable("message.serfdom.law.freed", Workers.name(loaded)).withStyle(ChatFormatting.GOLD));
            Workers.free((ServerLevel) loaded.level(), loaded, com.chunkworks.serfdom.domain.Parting.Way.FREED_BY_LAW);
        }
    }

    /** effects: the player fled their case with the village: it is owed nothing more. */
    public static void fled(ServerPlayer player, String village) {
        var r = get(player.server);
        var next = r.ledger.fled(new Remedy.Case(player.getUUID(), village));
        if (next != r.ledger) {
            r.ledger = next;
            r.setDirty();
        }
    }

    private static Villager find(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) if (level.getEntity(id) instanceof Villager v && v.isAlive()) return v;
        return null;
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
            if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof Villager v)) return;
            var r = get(level.getServer());
            if (r.freeOnLoad.remove(v.getUUID())) {
                r.setDirty();
                // Freed on its next tick: the entity is joining, and its brain is rebuilt as it is freed.
                level.getServer().execute(() -> { if (v.isAlive() && Workers.of(v).owned()) Workers.free(level, v, com.chunkworks.serfdom.domain.Parting.Way.FREED_BY_LAW); });
            }
        });
    }

    private static Remedies load(CompoundTag tag, HolderLookup.Provider registries) {
        var r = new Remedies();
        var owed = new HashMap<Remedy.Case, Set<UUID>>();
        var cases = tag.getList("Owed", Tag.TAG_COMPOUND);
        for (int i = 0; i < cases.size(); i++) {
            var c = cases.getCompound(i);
            var captives = new HashSet<UUID>();
            for (var t : c.getList("Captives", Tag.TAG_INT_ARRAY)) captives.add(NbtUtils.loadUUID(t));
            if (!captives.isEmpty()) owed.put(new Remedy.Case(c.getUUID("Player"), c.getString("Village")), captives);
        }
        try {
            r.ledger = Remedy.of(owed);
        } catch (IllegalArgumentException e) {
            com.mojang.logging.LogUtils.getLogger().warn("Serfdom: the remedies could not be read; starting with none", e);
        }
        for (var t : tag.getList("FreeOnLoad", Tag.TAG_INT_ARRAY)) r.freeOnLoad.add(NbtUtils.loadUUID(t));
        return r;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var cases = new ListTag();
        for (var e : ledger.owed().entrySet()) {
            var c = new CompoundTag();
            c.putUUID("Player", e.getKey().player());
            c.putString("Village", e.getKey().village());
            var captives = new ListTag();
            for (var id : e.getValue()) captives.add(NbtUtils.createUUID(id));
            c.put("Captives", captives);
            cases.add(c);
        }
        tag.put("Owed", cases);
        var free = new ListTag();
        for (var id : freeOnLoad) free.add(NbtUtils.createUUID(id));
        tag.put("FreeOnLoad", free);
        return tag;
    }
}
