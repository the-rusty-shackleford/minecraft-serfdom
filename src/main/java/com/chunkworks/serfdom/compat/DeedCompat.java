/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;

/** Village Deed's villages and claims (D-0001): which village a villager lives in, and whether a
 * player may hire there: anyone in a village nobody bought, only the owner and the players on the
 * owner's trust list in one somebody did. Without Village Deed there are no villages to buy and
 * anyone may hire anywhere. */
public final class DeedCompat {
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("villagedeed");
    private DeedCompat() {}

    /** effects: whether {@code player} may hire a villager at {@code pos}. */
    public static boolean allows(ServerLevel level, BlockPos pos, UUID player) { return !LOADED || Inner.allows(level, pos, player); }

    /** effects: the id of the village at {@code pos}, if Village Deed knows one there. */
    public static Optional<String> village(ServerLevel level, BlockPos pos) { return LOADED ? Inner.village(level, pos) : Optional.empty(); }

    /** effects: the name of the owner of the village at {@code pos}, if somebody bought it. */
    public static Optional<String> owner(ServerLevel level, BlockPos pos) { return LOADED ? Inner.owner(level, pos) : Optional.empty(); }

    /** A village found around a place: its id and its bounds. */
    public record Found(String id, com.chunkworks.serfdom.domain.Area bounds) {}

    /** How far from a village a capture may be and still be the village's, as the law reckons it. */
    static final int NEAR = 16;

    /** effects: the village a capture at {@code pos} is taken from (D-0003): the one there, else the
     * nearest within {@link #NEAR} blocks, as Village Law places a crime; empty without Village Deed. */
    public static Optional<Found> around(ServerLevel level, BlockPos pos) { return LOADED ? Inner.around(level, pos) : Optional.empty(); }

    private static final class Inner {
        static Optional<com.chunkworks.villagedeed.Claims.Claim> claim(ServerLevel level, BlockPos pos) {
            return com.chunkworks.villagedeed.api.VillageProviders.at(level, pos)
                    .map(v -> com.chunkworks.villagedeed.Claims.get(level).get(v.id()));
        }
        static boolean allows(ServerLevel level, BlockPos pos, UUID player) {
            var claim = claim(level, pos);
            if (claim.isEmpty()) return true;
            var owner = claim.get().deed().owner();
            return owner.equals(player) || com.chunkworks.villagedeed.Rosters.get(level.getServer()).permits(owner, player);
        }
        static Optional<String> village(ServerLevel level, BlockPos pos) {
            return com.chunkworks.villagedeed.api.VillageProviders.at(level, pos).map(v -> v.id().toString());
        }
        static Optional<String> owner(ServerLevel level, BlockPos pos) { return claim(level, pos).map(c -> c.ownerName()); }
        static Optional<Found> around(ServerLevel level, BlockPos pos) {
            return com.chunkworks.villagedeed.api.VillageProviders.at(level, pos)
                    .or(() -> com.chunkworks.villagedeed.api.VillageProviders.near(level, pos, NEAR))
                    .map(v -> {
                        var b = v.bounds();
                        return new Found(v.id().toString(), new com.chunkworks.serfdom.domain.Area(level.dimension().location().toString(),
                                b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
                    });
        }
    }
}
