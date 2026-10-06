/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.Cell;
import com.chunkworks.serfdom.domain.Farm;
import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.domain.Sowing;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/** What one farming post's area offers its farm (D-0008): the ripe crops to take and the bare
 * farmland due to be sown, by plot, found by one sweep of the area and shared by every farmer of the
 * farm; and, kept from sweep to sweep, the kind each spot last grew and since when each bare spot has
 * been bare ({@link Sowing}). A sweep reads the area section by section, skipping every section whose
 * palette holds neither a crop nor farmland, and reads {@link Sowing#NEAR} blocks past the area's
 * sides for the crops a spot at its edge would copy. An area is swept the first time it is read (and
 * after it changed shape); after that the server sweeps again one area a tick, the stalest of those
 * read lately and swept {@link #SWEEP_EVERY} ticks ago or more ({@link
 * com.chunkworks.serfdom.post.Farms#listen}): a radius-16 area full of crops takes about a
 * millisecond, and a farm of nine read at once would otherwise take nine in one tick. Between sweeps
 * an area shows what it last found, which a farmer checks block by block as it works. Not saved:
 * after a restart, or once the post unloads, the kinds are learned again from what grows. A spot is
 * where a crop stands: the block above its farmland. */
public final class Field {
    /** The fewest ticks between two sweeps of one area. */
    public static final long SWEEP_EVERY = 100;
    /** The ticks after its last read that an area is still swept again. */
    public static final long ACTIVE = 2 * SWEEP_EVERY;

    /** One piece of work at {@code pos}: a crop to take, or, with a seed, a bare spot to sow. */
    public record Work(BlockPos pos, Optional<Item> sow) {}

    /** A crop's kind as sowing names it: the id of what plants it, and whether it is a stem. */
    record Kind(String id, boolean stem) {}

    private Map<Farm.Plot, List<Work>> work = Map.of();
    private final Map<Long, String> grew = new HashMap<>();
    private final Map<Long, Long> bareSince = new HashMap<>();
    private long sweptAt, lastRead = Long.MIN_VALUE;
    /** Swept since the area last changed shape. */
    private boolean fresh;

    /** effects: the area's work by plot as the last sweep found it, swept first when it never was
     * or the area changed shape. */
    public Map<Farm.Plot, List<Work>> work(ServerLevel level, WorkPostBlockEntity post) {
        long now = level.getGameTime();
        lastRead = now;
        if (!fresh) sweep(level, post, now);
        return Collections.unmodifiableMap(work);
    }

    /** effects: how long ago the area was swept, when it is due another sweep: swept
     * {@link #SWEEP_EVERY} ticks ago or more and read within {@link #ACTIVE}; empty otherwise. */
    public java.util.OptionalLong due(long now) {
        if (!fresh || now - sweptAt < SWEEP_EVERY || now - lastRead > ACTIVE) return java.util.OptionalLong.empty();
        return java.util.OptionalLong.of(now - sweptAt);
    }

    /** effects: sweeps the area again. */
    public void refresh(ServerLevel level, WorkPostBlockEntity post) { sweep(level, post, level.getGameTime()); }

    /** effects: the area's work, swept now whatever the clock says (a test's timing). */
    public Map<Farm.Plot, List<Work>> sweepNow(ServerLevel level, WorkPostBlockEntity post) {
        sweep(level, post, level.getGameTime());
        return Collections.unmodifiableMap(work);
    }

    /** effects: the area will be swept again before its work is next read. */
    public void stale() { fresh = false; }

    /** effects: the work at {@code pos} is done (taken or sown) until a sweep finds some there again. */
    public void taken(BlockPos pos) {
        var list = work.get(Farm.Plot.of(pos.getX(), pos.getZ()));
        if (list != null) list.removeIf(w -> w.pos().equals(pos));
    }

    /** effects: true iff a sweep must read a section whose palette may hold {@code state}. */
    private static boolean worthReading(BlockState state) {
        var block = state.getBlock();
        return block instanceof FarmBlock || block instanceof StemBlock || Farming.crop(state) || FarmersDelightCompat.budding(state);
    }

    /** effects: the kind of the crop at {@code pos}, when it stands on farmland and is a crop, a stem
     * or a budding tomato that something plants (its pick block places it). */
    static Optional<Kind> kind(ServerLevel level, BlockPos pos, BlockState state) {
        var block = state.getBlock();
        boolean stem = block instanceof StemBlock || block instanceof AttachedStemBlock;
        if (!(block instanceof CropBlock) && !stem && !FarmersDelightCompat.budding(state)) return Optional.empty();
        if (!(level.getBlockState(pos.below()).getBlock() instanceof FarmBlock)) return Optional.empty();
        var seed = block.getCloneItemStack(level, pos, state);
        if (seed.isEmpty() || !(seed.getItem() instanceof BlockItem)) return Optional.empty();
        return Optional.of(new Kind(BuiltInRegistries.ITEM.getKey(seed.getItem()).toString(), stem));
    }

    private void sweep(ServerLevel level, WorkPostBlockEntity post, long now) {
        var p = post.getBlockPos();
        int r = post.radius();
        var centre = Storage.cell(p);
        int minX = p.getX() - r - Sowing.NEAR, maxX = p.getX() + r + Sowing.NEAR;
        int minZ = p.getZ() - r - Sowing.NEAR, maxZ = p.getZ() + r + Sowing.NEAR;
        int minY = p.getY() - r, maxY = p.getY() + r;
        var harvest = new LinkedHashMap<BlockPos, Work>();
        var layers = new HashMap<Integer, Sowing.Layer>();
        var stems = new HashSet<String>();
        var seen = new HashSet<Long>();
        var bare = new HashSet<Long>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                // Farmland one below the area's floor holds a spot on it.
                for (int sy = Math.max(level.getMinSection(), (minY - 1) >> 4); sy <= Math.min(level.getMaxSection() - 1, maxY >> 4); sy++) {
                    var section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(Field::worthReading)) continue;
                    var origin = SectionPos.of(cx, sy, cz).origin();
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        int wx = origin.getX() + x, wy = origin.getY() + y, wz = origin.getZ() + z;
                        if (wx < minX || wx > maxX || wz < minZ || wz > maxZ) continue;
                        var state = section.getBlockState(x, y, z);
                        if (state.getBlock() instanceof FarmBlock) {
                            var spot = new BlockPos(wx, wy + 1, wz);
                            if (spot.getY() < minY || spot.getY() > maxY || !chunk.getBlockState(spot).isAir()) continue;
                            long key = spot.asLong();
                            seen.add(key);
                            bare.add(key);
                            long since = bareSince.computeIfAbsent(key, k -> now);
                            layer(layers, spot.getY(), minX, minZ, maxX, maxZ).bare(wx, wz, Optional.ofNullable(grew.get(key)), since);
                            continue;
                        }
                        if (wy < minY || wy > maxY || !worthReading(state)) continue;
                        var pos = new BlockPos(wx, wy, wz);
                        var kind = kind(level, pos, state);
                        if (kind.isPresent()) {
                            long key = pos.asLong();
                            seen.add(key);
                            grew.put(key, kind.get().id());
                            if (kind.get().stem()) stems.add(kind.get().id());
                            layer(layers, wy, minX, minZ, maxX, maxZ).crop(wx, wz, kind.get().id());
                        }
                        if (Farming.crop(state) && Radius.contains(centre, r, new Cell(wx, wy, wz)))
                            Farming.target(level, pos, state).ifPresent(t -> harvest.putIfAbsent(t, new Work(t, Optional.empty())));
                    }
                }
            }
        grew.keySet().retainAll(seen);
        bareSince.keySet().retainAll(bare);
        var out = new LinkedHashMap<Farm.Plot, List<Work>>();
        for (var w : harvest.values()) out.computeIfAbsent(Farm.Plot.of(w.pos().getX(), w.pos().getZ()), k -> new ArrayList<>()).add(w);
        long wait = SerfdomConfig.SOW_AFTER_SECONDS.get() * 20L;
        layers.forEach((y, layer) -> {
            for (var sow : Sowing.choose(layer, stems, now, wait)) {
                if (!Radius.contains(centre, r, new Cell(sow.x(), y, sow.z()))) continue;
                var seed = BuiltInRegistries.ITEM.get(ResourceLocation.parse(sow.kind()));
                if (!(seed instanceof BlockItem)) continue;
                var spot = new BlockPos(sow.x(), y, sow.z());
                out.computeIfAbsent(Farm.Plot.of(sow.x(), sow.z()), k -> new ArrayList<>()).add(new Work(spot, Optional.of(seed)));
            }
        });
        work = out;
        sweptAt = now;
        fresh = true;
    }

    private static Sowing.Layer layer(Map<Integer, Sowing.Layer> layers, int y, int minX, int minZ, int maxX, int maxZ) {
        return layers.computeIfAbsent(y, k -> new Sowing.Layer(minX, minZ, maxX - minX + 1, maxZ - minZ + 1));
    }

    /** effects: the kinds this area remembers, for a test: spot to kind. */
    public Map<Long, String> remembered() { return Collections.unmodifiableMap(grew); }
}
