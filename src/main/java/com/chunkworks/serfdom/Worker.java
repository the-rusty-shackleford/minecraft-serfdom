/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.Area;
import com.chunkworks.serfdom.domain.Assignment;
import com.chunkworks.serfdom.domain.Bond;
import com.chunkworks.serfdom.domain.Spot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

/** An owned villager's state, saved with it (D-0001, D-0003): whose it is and how (hired or
 * captive), its bed and its Work Post, the village it came from, the tool it keeps between shifts,
 * whether the chain is on it, and for a captive where it was taken, its village's bounds and how
 * its nights' escapes stand. A villager without this record (or with {@link #NONE}) is a free
 * villager. Immutable; the tool stack is copied in and out.
 *
 * <p>Rep invariant: an owned record has an owner; NONE has none and nothing else; bed and post
 * satisfy {@link Assignment}'s invariant; a captive knows where it was taken; only a captive has a
 * home area or escapes, and a cuffed one never escapes. */
public record Worker(Optional<UUID> owner, String ownerName, Optional<GlobalPos> bed, Optional<GlobalPos> post, Optional<String> homeVillage,
                     ItemStack tool, Bond bond, boolean cuffed, Optional<GlobalPos> takenFrom, Optional<Area> home, Night night) {
    /** A captive's nights (D-0003): the last day it rolled to escape, the moment it means to get up
     * when a roll succeeded, and whether it is on its way home. */
    public record Night(long rolled, Optional<Long> getUpAt, boolean escaping) {
        public static final Night NONE = new Night(Long.MIN_VALUE, Optional.empty(), false);
        static final Codec<Night> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("rolled", Long.MIN_VALUE).forGetter(Night::rolled),
                Codec.LONG.optionalFieldOf("get_up_at").forGetter(Night::getUpAt),
                Codec.BOOL.optionalFieldOf("escaping", false).forGetter(Night::escaping)
        ).apply(i, Night::new));
        public Night { Objects.requireNonNull(getUpAt); }
    }

    private static final Codec<Bond> BOND_CODEC = Codec.STRING.comapFlatMap(s -> {
        for (var b : Bond.values()) if (b.name().equalsIgnoreCase(s)) return com.mojang.serialization.DataResult.success(b);
        return com.mojang.serialization.DataResult.error(() -> "no bond " + s);
    }, b -> b.name().toLowerCase(java.util.Locale.ROOT));
    static final Codec<Area> AREA_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("dimension").forGetter(Area::dimension),
            Codec.INT.fieldOf("min_x").forGetter(Area::minX), Codec.INT.fieldOf("min_y").forGetter(Area::minY), Codec.INT.fieldOf("min_z").forGetter(Area::minZ),
            Codec.INT.fieldOf("max_x").forGetter(Area::maxX), Codec.INT.fieldOf("max_y").forGetter(Area::maxY), Codec.INT.fieldOf("max_z").forGetter(Area::maxZ)
    ).apply(i, Area::new));

    public static final Worker NONE = new Worker(Optional.empty(), "", Optional.empty(), Optional.empty(), Optional.empty(), ItemStack.EMPTY,
            Bond.HIRED, false, Optional.empty(), Optional.empty(), Night.NONE);

    public static final Codec<Worker> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Worker::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Worker::ownerName),
            GlobalPos.CODEC.optionalFieldOf("bed").forGetter(Worker::bed),
            GlobalPos.CODEC.optionalFieldOf("post").forGetter(Worker::post),
            Codec.STRING.optionalFieldOf("home_village").forGetter(Worker::homeVillage),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("tool", ItemStack.EMPTY).forGetter(Worker::tool),
            BOND_CODEC.optionalFieldOf("bond", Bond.HIRED).forGetter(Worker::bond),
            Codec.BOOL.optionalFieldOf("cuffed", false).forGetter(Worker::cuffed),
            GlobalPos.CODEC.optionalFieldOf("taken_from").forGetter(Worker::takenFrom),
            AREA_CODEC.optionalFieldOf("home").forGetter(Worker::home),
            Night.CODEC.optionalFieldOf("night", Night.NONE).forGetter(Worker::night)
    ).apply(i, Worker::new));

    public Worker {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(bed);
        Objects.requireNonNull(post);
        Objects.requireNonNull(homeVillage);
        Objects.requireNonNull(bond);
        Objects.requireNonNull(takenFrom);
        Objects.requireNonNull(home);
        Objects.requireNonNull(night);
        tool = tool.copy();
        if (owner.isEmpty() && (bed.isPresent() || post.isPresent() || !tool.isEmpty() || cuffed || bond != Bond.HIRED || takenFrom.isPresent()))
            throw new IllegalArgumentException("a free villager has no bed, post, tool, chain or captor");
        if (bond == Bond.CAPTIVE && takenFrom.isEmpty()) throw new IllegalArgumentException("a captive knows where it was taken");
        if (bond != Bond.CAPTIVE && (home.isPresent() || night.escaping() || night.getUpAt().isPresent()))
            throw new IllegalArgumentException("only a captive has a home to escape to");
        if (cuffed && night.escaping()) throw new IllegalArgumentException("a cuffed captive does not escape");
        assignment(bed, post);
    }

    /** effects: a newly hired worker of {@code owner}, with no bed, post or tool. */
    public static Worker hired(UUID owner, String ownerName, Optional<String> homeVillage) {
        return new Worker(Optional.of(owner), ownerName, Optional.empty(), Optional.empty(), homeVillage, ItemStack.EMPTY,
                Bond.HIRED, false, Optional.empty(), Optional.empty(), Night.NONE);
    }

    /** effects: a captive just taken by {@code owner} at {@code takenFrom}, cuffed, with no bed, post
     * or tool, from the village {@code homeVillage} whose bounds are {@code home}, when it knows one. */
    public static Worker captive(UUID owner, String ownerName, GlobalPos takenFrom, Optional<String> homeVillage, Optional<Area> home) {
        return new Worker(Optional.of(owner), ownerName, Optional.empty(), Optional.empty(), homeVillage, ItemStack.EMPTY,
                Bond.CAPTIVE, true, Optional.of(takenFrom), home, Night.NONE);
    }

    public boolean owned() { return owner.isPresent(); }
    public boolean ownedBy(UUID player) { return owner.isPresent() && owner.get().equals(player); }
    public boolean captive() { return bond == Bond.CAPTIVE; }
    public boolean escaping() { return night.escaping(); }
    @Override public ItemStack tool() { return tool.copy(); }

    /** effects: the bed and post as the domain's {@link Assignment}. */
    public Assignment assignment() { return assignment(bed, post); }

    /** effects: this worker with the bed and post of {@code a}. */
    public Worker with(Assignment a) {
        return new Worker(owner, ownerName, a.bed().map(Worker::global), a.post().map(Worker::global), homeVillage, tool, bond, cuffed, takenFrom, home, night);
    }
    /** effects: this worker keeping {@code stack} as its tool between shifts. */
    public Worker withTool(ItemStack stack) { return new Worker(owner, ownerName, bed, post, homeVillage, stack, bond, cuffed, takenFrom, home, night); }
    /** effects: this worker with the chain on, which ends an escape, or off. */
    public Worker withCuffs(boolean on) {
        var n = on ? new Night(night.rolled(), Optional.empty(), false) : night;
        return new Worker(owner, ownerName, bed, post, homeVillage, tool, bond, on, takenFrom, home, n);
    }
    /** requires: a captive. effects: this captive with its nights at {@code n}. */
    public Worker withNight(Night n) { return new Worker(owner, ownerName, bed, post, homeVillage, tool, bond, cuffed, takenFrom, home, n); }

    /** effects: where a captive was taken, as the domain's spot. */
    public Optional<Spot> takenSpot() { return takenFrom.map(Worker::spot); }

    private static Assignment assignment(Optional<GlobalPos> bed, Optional<GlobalPos> post) {
        return new Assignment(bed.map(Worker::spot), post.map(Worker::spot));
    }
    /** effects: the domain's spot for a position. */
    public static Spot spot(GlobalPos pos) {
        return new Spot(pos.dimension().location().toString(), pos.pos().getX(), pos.pos().getY(), pos.pos().getZ());
    }
    /** effects: the position a domain spot names. */
    public static GlobalPos global(Spot spot) {
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(spot.dimension()));
        return GlobalPos.of(key, new BlockPos(spot.cell().x(), spot.cell().y(), spot.cell().z()));
    }
}
