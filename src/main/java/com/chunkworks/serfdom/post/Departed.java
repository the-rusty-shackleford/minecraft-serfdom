/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.post;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Workers that died while their post was not loaded, by post: the post takes them off its list
 * when it next loads, so a dead worker never holds one of its four places. Saved with the
 * overworld as {@code serfdom_departed}. */
public final class Departed extends SavedData {
    private static final String NAME = "serfdom_departed";
    private final Map<GlobalPos, Set<UUID>> byPost = new HashMap<>();

    public static Departed get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Departed::new, Departed::load, null), NAME);
    }

    /** effects: {@code worker} leaves the post at {@code post} when it loads. */
    public void add(GlobalPos post, UUID worker) {
        if (byPost.computeIfAbsent(post, k -> new HashSet<>()).add(worker)) setDirty();
    }

    /** effects: the workers who left the post at {@code post}, forgotten here. */
    public Set<UUID> take(GlobalPos post) {
        var gone = byPost.remove(post);
        if (gone == null) return Set.of();
        setDirty();
        return gone;
    }

    static Departed load(CompoundTag tag, HolderLookup.Provider registries) {
        var d = new Departed();
        var list = tag.getList("Posts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            var pos = GlobalPos.CODEC.parse(NbtOps.INSTANCE, entry.get("Post")).result();
            if (pos.isEmpty()) continue;
            var workers = entry.getList("Workers", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < workers.size(); j++) d.byPost.computeIfAbsent(pos.get(), k -> new HashSet<>()).add(net.minecraft.core.UUIDUtil.uuidFromIntArray(workers.getIntArray(j)));
        }
        return d;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        byPost.forEach((pos, workers) -> {
            var entry = new CompoundTag();
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().ifPresent(t -> entry.put("Post", t));
            var ids = new ListTag();
            for (var w : workers) ids.add(net.minecraft.nbt.NbtUtils.createUUID(w));
            entry.put("Workers", ids);
            list.add(entry);
        });
        tag.put("Posts", list);
        return tag;
    }
}
