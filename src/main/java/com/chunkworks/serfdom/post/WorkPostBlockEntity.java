/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.post;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.domain.Assignment;
import com.chunkworks.serfdom.domain.Radius;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.domain.Stock;
import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.serfdom.job.Stations;
import com.chunkworks.serfdom.job.Storage;
import java.util.HashMap;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A Work Post (D-0001, D-0002): its owner, its job, its radius and outline, the workers on it (at
 * most four, with the names they had when they joined), its stock list, the stations its workers
 * loaded and what will come out of each, and the index of the storage and the stations in its
 * area. The index is not saved: it is found again when the post loads, after a block is placed or
 * broken in the area, and at least once a shift. What each worker is making now (its claim) is
 * not saved either: a task in hand ends with the server. Nor is its field (D-0008), what a farming
 * post's area offers its farm. Clients get the job, radius, outline and whether it has workers, to
 * draw the outline.
 *
 * <p>Rep invariant: workers.size() &le; {@link Assignment#MAX_WORKERS}; radius within the job's
 * {@link Radius} when the job is known. */
public final class WorkPostBlockEntity extends BlockEntity {
    public static final ResourceLocation DEFAULT_JOB = Serfdom.id("farming");
    private static final long REINDEX_EVERY = 6000;
    private Optional<UUID> owner = Optional.empty();
    private String ownerName = "";
    private ResourceLocation job = DEFAULT_JOB;
    private int radius = Radius.WORK.standard();
    private boolean outline;
    private final Map<UUID, String> workers = new LinkedHashMap<>();
    private List<BlockPos> storage = List.of();
    private List<Stations.Found> stations = List.of();
    private Stock stock = Stock.EMPTY;
    private final Map<BlockPos, Loaded> loaded = new HashMap<>();
    private final Map<UUID, Claim> claims = new HashMap<>();
    private boolean stale = true;
    private long indexedAt;
    private final com.chunkworks.serfdom.job.Field field = new com.chunkworks.serfdom.job.Field();

    public WorkPostBlockEntity(BlockPos pos, BlockState state) { super(Serfdom.WORK_POST_ENTITY.get(), pos, state); }

    public Optional<UUID> owner() { return owner; }
    public String ownerName() { return ownerName; }
    public boolean ownedBy(UUID player) { return owner.isEmpty() || owner.get().equals(player); }
    public ResourceLocation job() { return job; }
    public int radius() { return radius; }
    public boolean outline() { return outline; }
    /** effects: the workers on this post, in the order they joined. */
    public List<UUID> workers() { return List.copyOf(workers.keySet()); }
    /** effects: the names the workers had when they joined, in the same order. */
    public List<String> workerNames() { return List.copyOf(workers.values()); }

    /** effects: makes {@code player} the post's owner (the one who placed it). */
    public void claim(UUID player, String name) { owner = Optional.of(player); ownerName = name; Posts.reshaped(); changed(); }

    /** effects: what a farming post's area offers its farm (D-0008), swept when read. */
    public com.chunkworks.serfdom.job.Field field() { return field; }

    /** effects: sets the job and its radius to the job's default when the job changes; the radius is
     * then held to the job's bounds. */
    public void setJob(ResourceLocation id, int newRadius) {
        boolean jobChanged = !id.equals(job);
        job = id;
        var bounds = Jobs.get(id).map(j -> j.radius()).orElse(Radius.WORK);
        radius = bounds.clamp(jobChanged && newRadius == radius ? bounds.standard() : newRadius);
        stale = true;
        stationsStale = true;
        field.stale();
        Posts.reshaped();
        changed();
    }

    public void setOutline(boolean on) { outline = on; changed(); }

    /** requires: fewer than four workers, or {@code worker} already on. effects: adds the worker. */
    public void addWorker(UUID worker, net.minecraft.network.chat.Component name) {
        if (!workers.containsKey(worker) && workers.size() >= Assignment.MAX_WORKERS) throw new IllegalStateException("post full");
        workers.put(worker, name.getString());
        changed();
    }

    public void removeWorker(UUID worker) { if (workers.remove(worker) != null) changed(); }

    /** effects: the storage in the area, nearest the post first; looked for again when a block
     * changed in the area or a shift's time has passed since. */
    public List<BlockPos> storage(ServerLevel level) {
        if (stale || level.getGameTime() - indexedAt > REINDEX_EVERY) {
            storage = Storage.scan(level, worldPosition, radius);
            stale = false;
            stationsStale = true;
            indexedAt = level.getGameTime();
        }
        return storage;
    }

    /** effects: the stations of {@code kinds} in the area, nearest the post first; looked for again
     * with the storage. */
    public List<Stations.Found> stations(ServerLevel level, Set<Station> kinds) {
        storage(level);
        if (stationsStale) {
            stations = Stations.scan(level, worldPosition, radius, kinds);
            stationsStale = false;
        }
        return stations;
    }
    private boolean stationsStale = true;

    /** effects: the storage index will be found again before it is next used. */
    public void stale() { stale = true; stationsStale = true; }

    // ---- the stock list and what is on its way (D-0002) ---------------------------------------

    public Stock stock() { return stock; }
    public void setStock(Stock s) { stock = java.util.Objects.requireNonNull(s); changed(); }

    /** What a station this post loaded will give: {@code count} of {@code item}. */
    public record Loaded(String item, int count) {}
    /** What one worker is doing now: the station it uses and what it will bring, since when. */
    public record Claim(BlockPos at, String item, int count, long since) {}
    /** Claims older than this are a worker's that never came back (it died, it was unloaded). */
    private static final long CLAIM_FOR = 2400;

    /** effects: the stations this post loaded and what each will give. */
    public Map<BlockPos, Loaded> loaded() { return java.util.Collections.unmodifiableMap(loaded); }
    /** effects: records that the station at {@code at} was loaded to give {@code count} more of
     * {@code item}. */
    public void loadedAt(BlockPos at, String item, int count) {
        loaded.merge(at.immutable(), new Loaded(item, count), (a, b) -> a.item().equals(b.item()) ? new Loaded(a.item(), a.count() + b.count()) : b);
        setChanged();
    }
    /** effects: {@code n} of what the station at {@code at} gives were collected; the record goes
     * when nothing more is to come. */
    public void collected(BlockPos at, int n) {
        var l = loaded.get(at);
        if (l == null) return;
        if (l.count() - n > 0) loaded.put(at, new Loaded(l.item(), l.count() - n)); else loaded.remove(at);
        setChanged();
    }
    /** effects: forgets the station at {@code at}: it is empty, or no longer this post's. */
    public void forget(BlockPos at) { if (loaded.remove(at) != null) setChanged(); }

    /** effects: the workers' claims, the stale ones dropped. */
    public Map<UUID, Claim> claims(long now) {
        claims.values().removeIf(c -> now - c.since() > CLAIM_FOR);
        return java.util.Collections.unmodifiableMap(claims);
    }
    public void claim(UUID worker, Claim c) { claims.put(worker, c); }
    public void release(UUID worker) { claims.remove(worker); }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null) Posts.track(level, worldPosition);
        if (level instanceof ServerLevel server) {
            var gone = Departed.get(server.getServer()).take(net.minecraft.core.GlobalPos.of(server.dimension(), worldPosition));
            if (!gone.isEmpty() && workers.keySet().removeAll(gone)) setChanged();
        }
        stale = true;
        stationsStale = true;
    }

    @Override public void setRemoved() {
        super.setRemoved();
        if (level != null) Posts.untrack(level, worldPosition);
    }

    @Override public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) Posts.untrack(level, worldPosition);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        owner.ifPresent(u -> tag.putUUID("Owner", u));
        tag.putString("OwnerName", ownerName);
        writeShared(tag);
        var list = new ListTag();
        workers.forEach((u, n) -> { var w = new CompoundTag(); w.putUUID("Id", u); w.putString("Name", n); list.add(w); });
        tag.put("Workers", list);
        var rows = new ListTag();
        for (var r : stock.rows()) { var t = new CompoundTag(); t.putString("Item", r.item()); t.putInt("Keep", r.keep()); rows.add(t); }
        tag.put("Stock", rows);
        var loads = new ListTag();
        loaded.forEach((pos, l) -> { var t = new CompoundTag(); t.putLong("Pos", pos.asLong()); t.putString("Item", l.item()); t.putInt("Count", l.count()); loads.add(t); });
        tag.put("Loaded", loads);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty();
        ownerName = tag.getString("OwnerName");
        readShared(tag);
        workers.clear();
        var list = tag.getList("Workers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && workers.size() < Assignment.MAX_WORKERS; i++) {
            var w = list.getCompound(i);
            if (w.hasUUID("Id")) workers.put(w.getUUID("Id"), w.getString("Name"));
        }
        var out = new java.util.ArrayList<Stock.Row>();
        var rows = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size() && out.size() < Stock.MAX_ROWS; i++) {
            var r = rows.getCompound(i);
            var item = r.getString("Item");
            if (item.isBlank() || out.stream().anyMatch(x -> x.item().equals(item))) continue;
            out.add(new Stock.Row(item, Math.clamp(r.getInt("Keep"), 1, Stock.MAX_KEEP)));
        }
        stock = new Stock(out);
        loaded.clear();
        var loads = tag.getList("Loaded", Tag.TAG_COMPOUND);
        for (int i = 0; i < loads.size(); i++) {
            var l = loads.getCompound(i);
            if (!l.getString("Item").isBlank() && l.getInt("Count") > 0) loaded.put(BlockPos.of(l.getLong("Pos")), new Loaded(l.getString("Item"), l.getInt("Count")));
        }
        stale = true;
        stationsStale = true;
    }

    private void writeShared(CompoundTag tag) {
        tag.putString("Job", job.toString());
        tag.putInt("Radius", radius);
        tag.putBoolean("Outline", outline);
        tag.putInt("WorkerCount", workers.size());
    }

    private int clientWorkers;
    /** effects: on a client, how many workers the post has. */
    public int workerCount() { return level != null && level.isClientSide ? clientWorkers : workers.size(); }

    private void readShared(CompoundTag tag) {
        var id = ResourceLocation.tryParse(tag.getString("Job"));
        job = id == null ? DEFAULT_JOB : id;
        radius = Math.clamp(tag.contains("Radius") ? tag.getInt("Radius") : Radius.WORK.standard(), 1, Radius.CEILING);
        outline = tag.getBoolean("Outline");
        clientWorkers = tag.getInt("WorkerCount");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        writeShared(tag);
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { readShared(tag); }

    @Override public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readShared(packet.getTag());
    }

    /** effects: the names of this post's workers, for the screen. */
    public List<Component> workerLines() {
        var out = new ArrayList<Component>();
        for (var n : workers.values()) out.add(Component.literal(n));
        return out;
    }
}
