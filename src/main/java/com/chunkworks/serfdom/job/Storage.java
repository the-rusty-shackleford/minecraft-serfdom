/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.domain.Cell;
import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.domain.Sorting;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** A post's storage (D-0001): the containers in its area that hold at least
 * {@link #MIN_SLOTS} stacks (chests, barrels, shulker boxes, vaults; not furnaces, hoppers or
 * dispensers), found among the area's block entities and read only when a worker fetches or
 * deposits. */
public final class Storage {
    /** A container this size or larger is storage. */
    public static final int MIN_SLOTS = 18;
    private Storage() {}

    /** effects: the item handler at {@code pos}, when its chunk is loaded and it has one. */
    public static Optional<IItemHandler> handler(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return Optional.empty();
        return Optional.ofNullable(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null));
    }

    /** effects: the storage in the cube of {@code radius} around {@code post}, nearest the post
     * first, from the block entities of the loaded chunks it covers. */
    public static List<BlockPos> scan(ServerLevel level, BlockPos post, int radius) {
        var out = new ArrayList<BlockPos>();
        var centre = cell(post);
        for (int cx = (post.getX() - radius) >> 4; cx <= (post.getX() + radius) >> 4; cx++)
            for (int cz = (post.getZ() - radius) >> 4; cz <= (post.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var pos : chunk.getBlockEntitiesPos()) {
                    if (pos.equals(post) || !Radius.contains(centre, radius, cell(pos))) continue;
                    var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                    if (handler != null && handler.getSlots() >= MIN_SLOTS) out.add(pos.immutable());
                }
            }
        out.sort(Comparator.<BlockPos>comparingDouble(p -> p.distSqr(post)).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return out;
    }

    /** effects: the nearest storage holding something in {@code tag}. */
    public static Optional<BlockPos> toolAt(ServerLevel level, WorkPostBlockEntity post, TagKey<Item> tag) {
        for (var pos : post.storage(level)) {
            var h = handler(level, pos);
            if (h.isEmpty()) continue;
            for (int i = 0; i < h.get().getSlots(); i++) if (h.get().getStackInSlot(i).is(tag)) return Optional.of(pos);
        }
        return Optional.empty();
    }

    /** effects: takes one thing in {@code tag} out of the storage at {@code pos}, the most worn
     * first so tools are used up in turn; EMPTY when there is none. */
    public static ItemStack takeTool(ServerLevel level, BlockPos pos, TagKey<Item> tag) {
        var h = handler(level, pos).orElse(null);
        if (h == null) return ItemStack.EMPTY;
        int best = -1;
        for (int i = 0; i < h.getSlots(); i++) {
            var s = h.getStackInSlot(i);
            if (s.is(tag) && (best < 0 || s.getDamageValue() > h.getStackInSlot(best).getDamageValue())) best = i;
        }
        return best < 0 ? ItemStack.EMPTY : h.extractItem(best, 1, false);
    }

    /** effects: the containers to try for {@code stack}, best first ({@link Sorting#order}). */
    public static List<BlockPos> order(ServerLevel level, WorkPostBlockEntity post, ItemStack stack) {
        var containers = post.storage(level);
        var tags = stack.getTags().collect(Collectors.toSet());
        var bins = new ArrayList<Sorting.Bin>(containers.size());
        for (var pos : containers) {
            var h = handler(level, pos);
            if (h.isEmpty()) continue;
            bins.add(bin(h.get(), pos, stack, tags));
        }
        var centre = cell(post.getBlockPos());
        var overflow = Sorting.overflow(centre, containers.stream().map(Storage::cell).toList());
        return Sorting.order(centre, bins, overflow).stream().map(c -> new BlockPos(c.x(), c.y(), c.z())).toList();
    }

    private static Sorting.Bin bin(IItemHandler h, BlockPos pos, ItemStack stack, Set<TagKey<Item>> tags) {
        boolean holds = false;
        int shared = 0;
        var seen = new java.util.HashSet<Item>();
        for (int i = 0; i < h.getSlots(); i++) {
            var s = h.getStackInSlot(i);
            if (s.isEmpty() || !seen.add(s.getItem())) continue;
            if (s.is(stack.getItem())) holds = true;
            else if (!tags.isEmpty()) shared = Math.max(shared, (int) s.getTags().filter(tags::contains).count());
        }
        int room = stack.getCount() - ItemHandlerHelper.insertItemStacked(h, stack.copy(), true).getCount();
        return new Sorting.Bin(cell(pos), holds, shared, room);
    }

    /** effects: the first container some carried stack goes to, the first stack's first; empty when
     * nothing carried has anywhere to go. */
    public static Optional<BlockPos> depositTarget(ServerLevel level, WorkPostBlockEntity post, SimpleContainer carried) {
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var s = carried.getItem(i);
            if (s.isEmpty()) continue;
            var order = order(level, post, s);
            if (!order.isEmpty()) return Optional.of(order.getFirst());
        }
        return Optional.empty();
    }

    /** effects: puts into the container at {@code pos} every carried stack whose best place it is,
     * as much as fits; returns how many items went in. */
    public static int depositAt(ServerLevel level, WorkPostBlockEntity post, SimpleContainer carried, BlockPos pos) {
        var h = handler(level, pos).orElse(null);
        if (h == null) return 0;
        int moved = 0;
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var s = carried.getItem(i);
            if (s.isEmpty()) continue;
            var order = order(level, post, s);
            if (order.isEmpty() || !order.getFirst().equals(pos)) continue;
            var rest = ItemHandlerHelper.insertItemStacked(h, s.copy(), false);
            moved += s.getCount() - rest.getCount();
            carried.setItem(i, rest);
        }
        return moved;
    }

    /** effects: whether anything carried has somewhere to go. */
    public static boolean room(ServerLevel level, WorkPostBlockEntity post, SimpleContainer carried) {
        return depositTarget(level, post, carried).isPresent();
    }

    /** effects: whether {@code stacks} all fit into {@code carried} together. */
    public static boolean fits(SimpleContainer carried, List<ItemStack> stacks) {
        var trial = new SimpleContainer(carried.getContainerSize());
        for (int i = 0; i < carried.getContainerSize(); i++) trial.setItem(i, carried.getItem(i).copy());
        for (var s : stacks) if (!trial.addItem(s.copy()).isEmpty()) return false;
        return true;
    }

    /** effects: adds {@code stacks} to {@code carried}; requires that they fit ({@link #fits}). */
    public static void add(SimpleContainer carried, List<ItemStack> stacks) {
        for (var s : stacks) carried.addItem(s);
    }

    public static Cell cell(BlockPos pos) { return new Cell(pos.getX(), pos.getY(), pos.getZ()); }
}
