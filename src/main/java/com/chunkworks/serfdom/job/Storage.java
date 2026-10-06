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
 * dispensers, nor a For Sale block, whose stock and proceeds are the stall's), found among the area's
 * block entities and read only when a worker fetches or deposits. */
public final class Storage {
    /** A container this size or larger is storage. */
    public static final int MIN_SLOTS = 18;
    private Storage() {}

    /** effects: true iff the block at {@code pos} is storage: a container of at least
     * {@link #MIN_SLOTS} stacks that is not a For Sale block. Its 27 slots are a stall's stock and
     * proceeds (D-0006): a worker would put its harvest into the stock and take a stall for its home
     * chest. */
    public static boolean storage(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof com.chunkworks.serfdom.market.ForSaleBlockEntity) return false;
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        return handler != null && handler.getSlots() >= MIN_SLOTS;
    }

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
                    if (storage(level, pos)) out.add(pos.immutable());
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

    // ---- a farm's storage (D-0008) ---------------------------------------------------------------

    /** A container on a farm and the post whose storage it is. */
    public record At(WorkPostBlockEntity post, BlockPos pos) {}

    /** effects: the farm's posts, the nearest to {@code from} first (ties by position). */
    static List<WorkPostBlockEntity> nearestFirst(List<WorkPostBlockEntity> farm, BlockPos from) {
        if (farm.size() == 1) return farm;
        var out = new ArrayList<>(farm);
        out.sort(Comparator.<WorkPostBlockEntity>comparingDouble(p -> p.getBlockPos().distSqr(from)).thenComparingLong(p -> p.getBlockPos().asLong()));
        return out;
    }

    /** effects: where on the farm to take what is carried: of the post nearest {@code from} whose
     * storage takes something carried, the container its sorting chooses ({@link #depositTarget}). */
    public static Optional<At> depositTarget(ServerLevel level, List<WorkPostBlockEntity> farm, BlockPos from, SimpleContainer carried) {
        for (var post : nearestFirst(farm, from)) {
            var pos = depositTarget(level, post, carried);
            if (pos.isPresent()) return Optional.of(new At(post, pos.get()));
        }
        return Optional.empty();
    }

    /** effects: of the post nearest {@code from} whose storage holds something in {@code tag}, the
     * container nearest that post holding one. */
    public static Optional<At> toolAt(ServerLevel level, List<WorkPostBlockEntity> farm, BlockPos from, TagKey<Item> tag) {
        for (var post : nearestFirst(farm, from)) {
            var pos = toolAt(level, post, tag);
            if (pos.isPresent()) return Optional.of(new At(post, pos.get()));
        }
        return Optional.empty();
    }

    /** effects: the container on the farm holding {@code item} nearest {@code from}. */
    public static Optional<BlockPos> nearestHolding(ServerLevel level, List<WorkPostBlockEntity> farm, BlockPos from, Item item) {
        BlockPos best = null;
        for (var post : farm) for (var pos : post.storage(level)) {
            if (best != null && pos.distSqr(from) >= best.distSqr(from)) continue;
            var h = handler(level, pos);
            if (h.isEmpty()) continue;
            for (int i = 0; i < h.get().getSlots(); i++) if (h.get().getStackInSlot(i).is(item)) { best = pos; break; }
        }
        return Optional.ofNullable(best);
    }

    /** effects: takes up to {@code count} of {@code item} out of the container at {@code pos} into
     * {@code into}, as many as fit there; returns how many it took. */
    public static int takeUpTo(ServerLevel level, BlockPos pos, Item item, int count, SimpleContainer into) {
        var h = handler(level, pos).orElse(null);
        if (h == null) return 0;
        int took = 0;
        for (int i = 0; i < h.getSlots() && took < count; i++) {
            if (!h.getStackInSlot(i).is(item)) continue;
            var got = h.extractItem(i, count - took, false);
            var rest = into.addItem(got);
            if (!rest.isEmpty()) { ItemHandlerHelper.insertItemStacked(h, rest, false); took += got.getCount() - rest.getCount(); break; }
            took += got.getCount();
        }
        return took;
    }

    /** effects: how many of {@code item} {@code carried} holds. */
    public static int count(SimpleContainer carried, Item item) {
        int n = 0;
        for (int i = 0; i < carried.getContainerSize(); i++) if (carried.getItem(i).is(item)) n += carried.getItem(i).getCount();
        return n;
    }

    // ---- a workshop's fetching (D-0002) ---------------------------------------------------------

    /** effects: how many of each item the post's storage holds, by item id. */
    public static java.util.Map<String, Integer> counts(ServerLevel level, WorkPostBlockEntity post) {
        var out = new java.util.HashMap<String, Integer>();
        for (var pos : post.storage(level)) handler(level, pos).ifPresent(h -> {
            for (int i = 0; i < h.getSlots(); i++) {
                var s = h.getStackInSlot(i);
                if (!s.isEmpty()) out.merge(RecipeBook.key(s.getItem()), s.getCount(), Integer::sum);
            }
        });
        return out;
    }

    /** What to take out of one container. */
    public record Take(BlockPos at, java.util.Map<String, Integer> items) {}

    /** effects: where to take {@code want} from, the containers nearest the post first, each
     * container visited once for everything it gives; empty when the storage does not hold it all. */
    public static Optional<List<Take>> takes(ServerLevel level, WorkPostBlockEntity post, java.util.Map<String, Integer> want) {
        var left = new java.util.LinkedHashMap<>(want);
        left.values().removeIf(n -> n <= 0);
        var out = new ArrayList<Take>();
        for (var pos : post.storage(level)) {
            if (left.isEmpty()) break;
            var h = handler(level, pos);
            if (h.isEmpty()) continue;
            var here = new java.util.LinkedHashMap<String, Integer>();
            for (int i = 0; i < h.get().getSlots(); i++) {
                var s = h.get().getStackInSlot(i);
                if (s.isEmpty()) continue;
                var k = RecipeBook.key(s.getItem());
                int need = left.getOrDefault(k, 0) - here.getOrDefault(k, 0);
                if (need > 0) here.merge(k, Math.min(need, s.getCount()), Integer::sum);
            }
            if (here.isEmpty()) continue;
            here.forEach((k, n) -> left.computeIfPresent(k, (kk, m) -> m - n > 0 ? m - n : null));
            out.add(new Take(pos, here));
        }
        return left.isEmpty() ? Optional.of(out) : Optional.empty();
    }

    /** effects: takes {@code items} out of the container at {@code pos} into {@code into}; true iff
     * all of them were there and fit, otherwise nothing is taken. */
    public static boolean take(ServerLevel level, BlockPos pos, java.util.Map<String, Integer> items, SimpleContainer into) {
        var h = handler(level, pos).orElse(null);
        if (h == null) return false;
        var taken = new ArrayList<ItemStack>();
        for (var e : items.entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < h.getSlots() && left > 0; i++) {
                var s = h.getStackInSlot(i);
                if (s.isEmpty() || !RecipeBook.key(s.getItem()).equals(e.getKey())) continue;
                var got = h.extractItem(i, left, false);
                left -= got.getCount();
                if (!got.isEmpty()) taken.add(got);
            }
            if (left > 0) { putBack(h, taken); return false; }
        }
        if (!fits(into, taken)) { putBack(h, taken); return false; }
        add(into, taken);
        return true;
    }

    private static void putBack(IItemHandler h, List<ItemStack> stacks) {
        for (var s : stacks) ItemHandlerHelper.insertItemStacked(h, s, false);
    }

    /** effects: the nearest storage holding a stack {@code wanted} accepts. */
    public static Optional<BlockPos> holding(ServerLevel level, WorkPostBlockEntity post, java.util.function.Predicate<ItemStack> wanted) {
        for (var pos : post.storage(level)) {
            var h = handler(level, pos);
            if (h.isEmpty()) continue;
            for (int i = 0; i < h.get().getSlots(); i++) if (wanted.test(h.get().getStackInSlot(i))) return Optional.of(pos);
        }
        return Optional.empty();
    }

    /** effects: takes one stack {@code wanted} accepts out of the storage at {@code pos}, the most
     * worn first; EMPTY when there is none. */
    public static ItemStack takeOne(ServerLevel level, BlockPos pos, java.util.function.Predicate<ItemStack> wanted) {
        var h = handler(level, pos).orElse(null);
        if (h == null) return ItemStack.EMPTY;
        int best = -1;
        for (int i = 0; i < h.getSlots(); i++) {
            var s = h.getStackInSlot(i);
            if (wanted.test(s) && (best < 0 || s.getDamageValue() > h.getStackInSlot(best).getDamageValue())) best = i;
        }
        return best < 0 ? ItemStack.EMPTY : h.extractItem(best, 1, false);
    }
}
