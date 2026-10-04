/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.Assignment;
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

/** An owned villager's state, saved with it (D-0001): whose it is, its bed and its Work Post, the
 * village it was hired from, and the tool it keeps between shifts. A villager without this record
 * (or with {@link #NONE}) is a free villager. Immutable; the tool stack is copied in and out.
 *
 * <p>Rep invariant: an owned record has an owner; NONE has none and nothing else; bed and post
 * satisfy {@link Assignment}'s invariant. */
public record Worker(Optional<UUID> owner, String ownerName, Optional<GlobalPos> bed, Optional<GlobalPos> post, Optional<String> homeVillage, ItemStack tool) {
    public static final Worker NONE = new Worker(Optional.empty(), "", Optional.empty(), Optional.empty(), Optional.empty(), ItemStack.EMPTY);

    public static final Codec<Worker> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Worker::owner),
            Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Worker::ownerName),
            GlobalPos.CODEC.optionalFieldOf("bed").forGetter(Worker::bed),
            GlobalPos.CODEC.optionalFieldOf("post").forGetter(Worker::post),
            Codec.STRING.optionalFieldOf("home_village").forGetter(Worker::homeVillage),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("tool", ItemStack.EMPTY).forGetter(Worker::tool)
    ).apply(i, Worker::new));

    public Worker {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(ownerName);
        Objects.requireNonNull(bed);
        Objects.requireNonNull(post);
        Objects.requireNonNull(homeVillage);
        tool = tool.copy();
        if (owner.isEmpty() && (bed.isPresent() || post.isPresent() || !tool.isEmpty()))
            throw new IllegalArgumentException("a free villager has no bed, post or tool");
        assignment(bed, post);
    }

    /** effects: a newly hired worker of {@code owner}, with no bed, post or tool. */
    public static Worker hired(UUID owner, String ownerName, Optional<String> homeVillage) {
        return new Worker(Optional.of(owner), ownerName, Optional.empty(), Optional.empty(), homeVillage, ItemStack.EMPTY);
    }

    public boolean owned() { return owner.isPresent(); }
    public boolean ownedBy(UUID player) { return owner.isPresent() && owner.get().equals(player); }
    @Override public ItemStack tool() { return tool.copy(); }

    /** effects: the bed and post as the domain's {@link Assignment}. */
    public Assignment assignment() { return assignment(bed, post); }

    /** effects: this worker with the bed and post of {@code a}. */
    public Worker with(Assignment a) {
        return new Worker(owner, ownerName, a.bed().map(Worker::global), a.post().map(Worker::global), homeVillage, tool);
    }
    /** effects: this worker keeping {@code stack} as its tool between shifts. */
    public Worker withTool(ItemStack stack) { return new Worker(owner, ownerName, bed, post, homeVillage, stack); }

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
