/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.domain.Workshop;
import com.chunkworks.serfdom.mixin.FurnaceAccess;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;

/** The stations in a Work Post's area (D-0002): what kind each block is, how a worker of the post
 * finds it now, and putting a load in and taking one out. A worker uses only an empty station,
 * and takes out only what its post put in ({@link WorkPostBlockEntity#loaded}); a station holding
 * anything else is a player's and OFF. A campfire or stove must be lit and a pot heated. */
public final class Stations {
    /** Ranged Weapons Mod's weapons workbench, a plain block. */
    public static final ResourceLocation WORKBENCH = ResourceLocation.parse("rangedweaponsmod:weapons_workbench");
    /** How far from a campfire or stove its cooked food is picked up. */
    private static final double DROPS = 2.5;
    private Stations() {}

    /** A station found in an area. */
    public record Found(BlockPos pos, Station kind) {}

    /** effects: the kind of station the block is, by its block entity or else the block. */
    public static Optional<Station> kind(BlockState state, @Nullable BlockEntity be) {
        if (be != null) {
            if (be instanceof BlastFurnaceBlockEntity) return Optional.of(Station.BLAST);
            if (be instanceof SmokerBlockEntity) return Optional.of(Station.SMOKER);
            if (be instanceof FurnaceBlockEntity) return Optional.of(Station.FURNACE);
            if (be instanceof CampfireBlockEntity) return Optional.of(Station.CAMPFIRE);
            var fd = FarmersDelightCompat.station(be);
            if (fd.isPresent()) return fd;
        }
        return plainKind(state);
    }

    /** effects: the kind of a station that is a plain block, with no block entity. */
    private static Optional<Station> plainKind(BlockState state) {
        if (state.is(Tags.Blocks.PLAYER_WORKSTATIONS_CRAFTING_TABLES)) return Optional.of(Station.TABLE);
        if (state.is(Blocks.SMITHING_TABLE)) return Optional.of(Station.SMITHING);
        if (state.is(BlockTags.ANVIL)) return Optional.of(Station.ANVIL);
        if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(WORKBENCH)) return Optional.of(Station.WORKBENCH);
        return Optional.empty();
    }

    /** effects: the stations of {@code kinds} in the cube of {@code radius} around {@code post},
     * nearest the post first: block entities from the loaded chunks' lists, plain blocks from the
     * sections whose palette may hold one. */
    public static List<Found> scan(ServerLevel level, BlockPos post, int radius, Set<Station> kinds) {
        var out = new ArrayList<Found>();
        if (kinds.isEmpty()) return out;
        var centre = Storage.cell(post);
        boolean plains = kinds.contains(Station.TABLE) || kinds.contains(Station.SMITHING) || kinds.contains(Station.ANVIL) || kinds.contains(Station.WORKBENCH);
        for (int cx = (post.getX() - radius) >> 4; cx <= (post.getX() + radius) >> 4; cx++)
            for (int cz = (post.getZ() - radius) >> 4; cz <= (post.getZ() + radius) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var pos : chunk.getBlockEntitiesPos()) {
                    if (!Radius.contains(centre, radius, Storage.cell(pos))) continue;
                    kind(level.getBlockState(pos), level.getBlockEntity(pos)).filter(kinds::contains).ifPresent(k -> out.add(new Found(pos.immutable(), k)));
                }
                if (!plains) continue;
                for (int sy = Math.max(level.getMinSection(), (post.getY() - radius) >> 4); sy <= Math.min(level.getMaxSection() - 1, (post.getY() + radius) >> 4); sy++) {
                    var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(s -> plainKind(s).isPresent())) continue;
                    var origin = SectionPos.of(cx, sy, cz).origin();
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        var state = section.getBlockState(x, y, z);
                        var k = plainKind(state);
                        if (k.isEmpty() || !kinds.contains(k.get())) continue;
                        var pos = origin.offset(x, y, z);
                        if (Radius.contains(centre, radius, Storage.cell(pos))) out.add(new Found(pos, k.get()));
                    }
                }
            }
        out.sort(Comparator.<Found>comparingDouble(f -> f.pos().distSqr(post)).thenComparingInt(f -> f.pos().getY()).thenComparingInt(f -> f.pos().getX()).thenComparingInt(f -> f.pos().getZ()));
        return out;
    }

    /** effects: the station as a worker of {@code post} finds it, another worker's claim making it
     * BUSY; forgets a station of the post's record that is empty again. */
    public static Workshop.Site site(ServerLevel level, WorkPostBlockEntity post, Found f, boolean claimed) {
        var at = Storage.cell(f.pos());
        if (claimed) return new Workshop.Site(at, f.kind(), Workshop.State.BUSY, 0);
        if (!level.isLoaded(f.pos())) return new Workshop.Site(at, f.kind(), Workshop.State.OFF, 0);
        var state = level.getBlockState(f.pos());
        var be = level.getBlockEntity(f.pos());
        if (kind(state, be).filter(k -> k == f.kind()).isEmpty()) return new Workshop.Site(at, f.kind(), Workshop.State.OFF, 0);
        var mine = post.loaded().get(f.pos());
        return switch (f.kind()) {
            case BLAST, SMOKER, FURNACE -> furnace(post, f, at, (AbstractFurnaceBlockEntity) be, state, mine);
            case CAMPFIRE -> {
                var slots = fire(be);
                boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
                int free = 0;
                for (var s : slots) if (s.isEmpty()) free++;
                boolean empty = free == slots.size();
                if (mine != null) yield new Workshop.Site(at, f.kind(), empty ? Workshop.State.READY : Workshop.State.BUSY, 0);
                yield new Workshop.Site(at, f.kind(), lit && empty ? Workshop.State.FREE : Workshop.State.OFF, free);
            }
            case POT -> pot(post, f, at, be, mine);
            case BOARD -> new Workshop.Site(at, f.kind(), FarmersDelightCompat.boardEmpty(be) ? Workshop.State.FREE : Workshop.State.OFF, 0);
            case TABLE, SMITHING, ANVIL, WORKBENCH -> new Workshop.Site(at, f.kind(), Workshop.State.FREE, 0);
        };
    }

    private static Workshop.Site furnace(WorkPostBlockEntity post, Found f, com.chunkworks.serfdom.domain.Cell at, AbstractFurnaceBlockEntity c, BlockState state, @Nullable WorkPostBlockEntity.Loaded mine) {
        var in = c.getItem(0); var fuel = c.getItem(1); var out = c.getItem(2);
        if (in.isEmpty() && out.isEmpty()) {
            if (mine != null) post.forget(f.pos());
            return new Workshop.Site(at, f.kind(), Workshop.State.FREE, 64);
        }
        if (mine == null || (!out.isEmpty() && !RecipeBook.key(out.getItem()).equals(mine.item()))) return new Workshop.Site(at, f.kind(), Workshop.State.OFF, 0);
        boolean lit = state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
        boolean stalled = !in.isEmpty() && !lit && fuel.isEmpty();
        if (in.isEmpty() || stalled) return new Workshop.Site(at, f.kind(), Workshop.State.READY, 0);
        return new Workshop.Site(at, f.kind(), Workshop.State.BUSY, 0);
    }

    private static Workshop.Site pot(WorkPostBlockEntity post, Found f, com.chunkworks.serfdom.domain.Cell at, BlockEntity be, @Nullable WorkPostBlockEntity.Loaded mine) {
        var slots = FarmersDelightCompat.potSlots(be);
        boolean inputs = false;
        for (int i = 0; i < FarmersDelightCompat.POT_INPUTS; i++) inputs |= !slots.getStackInSlot(i).isEmpty();
        boolean display = !slots.getStackInSlot(FarmersDelightCompat.POT_DISPLAY).isEmpty();
        boolean container = !slots.getStackInSlot(FarmersDelightCompat.POT_CONTAINER).isEmpty();
        boolean output = !slots.getStackInSlot(FarmersDelightCompat.POT_OUTPUT).isEmpty();
        boolean heated = FarmersDelightCompat.heated(be);
        if (!inputs && !display && !container && !output) {
            if (mine != null) post.forget(f.pos());
            return new Workshop.Site(at, f.kind(), heated ? Workshop.State.FREE : Workshop.State.OFF, 64);
        }
        if (mine == null) return new Workshop.Site(at, f.kind(), Workshop.State.OFF, 0);
        if (!inputs && !display) return new Workshop.Site(at, f.kind(), Workshop.State.READY, 0);
        return new Workshop.Site(at, f.kind(), Workshop.State.BUSY, 0);
    }

    /** effects: a campfire's or a stove's cooking slots. */
    static List<ItemStack> fire(BlockEntity be) {
        if (be instanceof CampfireBlockEntity c) return c.getItems();
        var slots = FarmersDelightCompat.stoveSlots(be);
        var out = new ArrayList<ItemStack>(slots.getSlots());
        for (int i = 0; i < slots.getSlots(); i++) out.add(slots.getStackInSlot(i));
        return out;
    }

    // ---- loading and collecting ------------------------------------------------------------------

    /** effects: the ticks the fire of a furnace-like station still burns, and how long a fuel burns
     * in it. */
    public static int burnLeft(BlockEntity be) { return be instanceof FurnaceAccess f ? f.serfdom$litTime() : 0; }
    public static int burnTicks(BlockEntity be, ItemStack fuel) { return be instanceof FurnaceAccess f ? f.serfdom$burnDuration(fuel) : 0; }

    /** requires: the station FREE, {@code picks} one stack per cell of the rule (each of the load's
     * count), {@code fuel} what goes into a furnace's fuel slot (or empty), {@code lastIsContainer}
     * whether a pot rule's last cell is the meal's container. effects: puts the load in; true iff
     * all of it went in, otherwise nothing changed that cannot be taken back out. */
    public static boolean load(ServerLevel level, Villager worker, BlockPos pos, Station kind, List<ItemStack> picks, ItemStack fuel, int cookTicks, boolean lastIsContainer) {
        var be = level.getBlockEntity(pos);
        switch (kind) {
            case BLAST, SMOKER, FURNACE -> {
                var c = (AbstractFurnaceBlockEntity) be;
                if (!c.getItem(0).isEmpty() || picks.size() != 1) return false;
                var slot = c.getItem(1);
                if (!fuel.isEmpty() && !slot.isEmpty() && (!ItemStack.isSameItemSameComponents(slot, fuel) || slot.getCount() + fuel.getCount() > slot.getMaxStackSize())) return false;
                c.setItem(0, picks.get(0).copy());
                if (!fuel.isEmpty()) c.setItem(1, slot.isEmpty() ? fuel.copy() : slot.copyWithCount(slot.getCount() + fuel.getCount()));
                c.setChanged();
                return true;
            }
            case CAMPFIRE -> {
                if (picks.size() != 1) return false;
                int free = 0;
                for (var slot : fire(be)) if (slot.isEmpty()) free++;
                if (free < picks.get(0).getCount()) return false;
                var stack = picks.get(0).copy();
                while (!stack.isEmpty()) {
                    var one = stack.copyWithCount(1);
                    boolean placed = be instanceof CampfireBlockEntity c ? c.placeFood(worker, one, cookTicks) : FarmersDelightCompat.stovePlace(be, worker, one);
                    if (!placed) break;
                    stack.shrink(1);
                }
                return stack.isEmpty();
            }
            case POT -> {
                var slots = FarmersDelightCompat.potSlots(be);
                if (picks.size() - (lastIsContainer ? 1 : 0) > FarmersDelightCompat.POT_INPUTS) return false;
                for (int i = 0; i < picks.size(); i++) {
                    var stack = picks.get(i);
                    boolean container = i == picks.size() - 1 && lastIsContainer;
                    slots.setStackInSlot(container ? FarmersDelightCompat.POT_CONTAINER : i, stack.copy());
                }
                be.setChanged();
                return true;
            }
            default -> { return false; }
        }
    }

    /** effects: takes out of the station what its post's load gave (a furnace's output and, when
     * stalled, its input; a pot's meals and, once empty, its spare containers; a campfire's or a
     * stove's food from the ground around it) into {@code into}, as far as it fits; returns how many
     * of the load's item were taken. */
    public static int collect(ServerLevel level, Villager worker, BlockPos pos, Station kind, String item, net.minecraft.world.SimpleContainer into) {
        var be = level.getBlockEntity(pos);
        int got = 0;
        switch (kind) {
            case BLAST, SMOKER, FURNACE -> {
                var c = (AbstractFurnaceBlockEntity) be;
                for (int slot : new int[]{2, 0}) {
                    var s = c.getItem(slot);
                    if (s.isEmpty()) continue;
                    if (slot == 0 && !c.getItem(2).isEmpty()) continue;
                    var rest = into.addItem(s.copy());
                    int moved = s.getCount() - rest.getCount();
                    if (slot == 2) got += moved;
                    c.setItem(slot, rest);
                }
                c.setChanged();
            }
            case POT -> {
                var slots = FarmersDelightCompat.potSlots(be);
                var out = slots.getStackInSlot(FarmersDelightCompat.POT_OUTPUT);
                if (!out.isEmpty()) {
                    var rest = into.addItem(out.copy());
                    got += out.getCount() - rest.getCount();
                    slots.setStackInSlot(FarmersDelightCompat.POT_OUTPUT, rest);
                }
                boolean emptied = true;
                for (int i = 0; i <= FarmersDelightCompat.POT_DISPLAY; i++) emptied &= slots.getStackInSlot(i).isEmpty();
                if (emptied) {
                    var left = slots.getStackInSlot(FarmersDelightCompat.POT_CONTAINER);
                    if (!left.isEmpty()) slots.setStackInSlot(FarmersDelightCompat.POT_CONTAINER, into.addItem(left.copy()));
                }
                be.setChanged();
            }
            case CAMPFIRE -> {
                for (var e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(DROPS), e -> e.isAlive() && RecipeBook.key(e.getItem().getItem()).equals(item))) {
                    var s = e.getItem();
                    var rest = into.addItem(s.copy());
                    got += s.getCount() - rest.getCount();
                    if (rest.isEmpty()) e.discard(); else e.setItem(rest);
                }
            }
            default -> {}
        }
        return got;
    }
}
