/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.*;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;

/** A cook's or a blacksmith's work (D-0002): reads what the post's chests hold, what its stations
 * are doing and what is on its way, asks {@link Workshop#next} what to do, and turns the answer
 * into a {@link WorkshopTask}: the fetches from the chests nearest the post, then the station. */
public final class WorkshopJob implements Job {
    public static final WorkshopJob INSTANCE = new WorkshopJob();
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    /** What a station that burns may be given: coal, charcoal and coal blocks unless a pack adds. */
    public static final TagKey<Item> FUEL = TagKey.create(Registries.ITEM, Serfdom.id("fuel"));
    /** A worker carries eight slots. */
    private static final int CARRY = 8;
    private WorkshopJob() {}

    /** The facts as read, with the stations they were read from. */
    public record Read(Workshop.Facts facts, List<Stations.Found> found) {}

    /** effects: what the post's worker {@code self} (or, for the post's screen, nobody) knows: the
     * chests' counts; on its way, what the post's loads will give, what other workers have claimed
     * and what every worker on the post carries; the stations, a claimed one BUSY and one the worker
     * could not reach lately OFF. */
    public static Read read(ServerLevel level, WorkPostBlockEntity post, JobScript job, Optional<Villager> self, Predicate<BlockPos> skip) {
        var stored = Storage.counts(level, post);
        var coming = new HashMap<String, Integer>();
        post.loaded().values().forEach(l -> coming.merge(l.item(), l.count(), Integer::sum));
        var me = self.map(Villager::getUUID).orElse(null);
        var claimed = new HashSet<BlockPos>();
        post.claims(level.getGameTime()).forEach((id, c) -> {
            if (id.equals(me)) return;
            claimed.add(c.at());
            if (c.count() > 0) coming.merge(c.item(), c.count(), Integer::sum);
        });
        for (var id : post.workers()) {
            if (level.getEntity(id) instanceof Villager v) {
                var inv = v.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    var s = inv.getItem(i);
                    if (!s.isEmpty()) coming.merge(RecipeBook.key(s.getItem()), s.getCount(), Integer::sum);
                }
            }
        }
        var found = post.stations(level, job.stations());
        var sites = new ArrayList<Workshop.Site>(found.size());
        for (var f : found) {
            var site = Stations.site(level, post, f, claimed.contains(f.pos()));
            if (skip.test(f.pos()) && site.state() == Workshop.State.FREE) site = new Workshop.Site(site.at(), site.kind(), Workshop.State.OFF, 0);
            sites.add(site);
        }
        int fuel = 0, fuelComing = 0;
        String log = null;
        var raw = new ArrayList<String>();
        for (var e : stored.entrySet()) {
            var stack = new ItemStack(RecipeBook.item(e.getKey()));
            if (stack.is(FUEL)) fuel += e.getValue();
            if (stack.is(ItemTags.LOGS_THAT_BURN) && (log == null || e.getValue() > stored.get(log))) log = e.getKey();
            if (stack.is(Tags.Items.RAW_MATERIALS)) raw.add(e.getKey());
        }
        for (var e : coming.entrySet()) if (new ItemStack(RecipeBook.item(e.getKey())).is(FUEL)) fuelComing += e.getValue();
        Collections.sort(raw);
        var worn = job.duties().contains(Workshop.Duty.REPAIR) ? worn(level, post, stored) : List.<Repair.Worn>of();
        boolean knife = self.map(v -> FarmersDelightCompat.knife(v.getItemBySlot(EquipmentSlot.MAINHAND))).orElse(false)
                || Storage.holding(level, post, FarmersDelightCompat::knife).isPresent();
        var facts = new Workshop.Facts(post.stock(), stored, coming, sites, RecipeBook.rules(level, job.stations()), job.duties(),
                fuel, fuelComing, Optional.ofNullable(log), raw, worn, knife);
        return new Read(facts, found);
    }

    /** effects: the damaged items in the post's storage with how much of their repair material it
     * holds, one entry per item kind (its most worn stack). */
    private static List<Repair.Worn> worn(ServerLevel level, WorkPostBlockEntity post, Map<String, Integer> stored) {
        var most = new LinkedHashMap<String, ItemStack>();
        for (var pos : post.storage(level)) Storage.handler(level, pos).ifPresent(h -> {
            for (int i = 0; i < h.getSlots(); i++) {
                var s = h.getStackInSlot(i);
                if (!s.isDamaged()) continue;
                most.merge(RecipeBook.key(s.getItem()), s, (a, b) -> b.getDamageValue() > a.getDamageValue() ? b : a);
            }
        });
        var out = new ArrayList<Repair.Worn>();
        most.forEach((key, tool) -> {
            int material = 0;
            for (var e : stored.entrySet()) if (tool.getItem().isValidRepairItem(tool, new ItemStack(RecipeBook.item(e.getKey())))) material += e.getValue();
            out.add(new Repair.Worn(key, tool.getMaxDamage(), tool.getDamageValue(), material));
        });
        return out;
    }

    @Override public Search find(ServerLevel level, Villager worker, WorkPostBlockEntity post, Predicate<BlockPos> skip) {
        var job = Jobs.get(post.job()).orElse(null);
        if (job == null) return Search.none();
        long t0 = System.nanoTime();
        var read = read(level, post, job, Optional.of(worker), skip);
        long t1 = System.nanoTime();
        var choice = Workshop.next(read.facts());
        if (TRACE) LOG.info("Serfdom trace: {} workshop {} -> {} (read {} us, chose {} us)", worker.getId(), post.getBlockPos().toShortString(), choice, (t1 - t0) / 1000, (System.nanoTime() - t1) / 1000);
        return switch (choice) {
            case Workshop.Rest rest -> new Search(Optional.empty(), rest.need());
            case Workshop.Collect c -> collect(level, worker, post, read, pos(c.at()));
            case Workshop.Load l -> load(level, worker, post, read, pos(l.at()), l.step(), l.logFuel());
            case Workshop.Make m -> make(level, worker, post, read, pos(m.at()), m.step());
            case Workshop.Mend m -> mend(level, worker, post, read, pos(m.at()), read.facts().worn().get(m.worn()));
        };
    }

    private static BlockPos pos(Cell c) { return new BlockPos(c.x(), c.y(), c.z()); }

    private static Station kindAt(Read read, BlockPos pos) {
        return read.found().stream().filter(f -> f.pos().equals(pos)).findFirst().orElseThrow().kind();
    }

    private static Search collect(ServerLevel level, Villager worker, WorkPostBlockEntity post, Read read, BlockPos at) {
        var loaded = post.loaded().get(at);
        var kind = kindAt(read, at);
        post.claim(worker.getUUID(), new WorkPostBlockEntity.Claim(at, loaded == null ? "" : loaded.item(), 0, level.getGameTime()));
        return found(post, List.of(new WorkshopTask.Collect(at, kind, loaded == null ? "" : loaded.item(), post)), "collect at " + at.toShortString());
    }

    /** effects: the task that loads up to {@code step.times()} into the station: as many as the
     * fuel stored covers and a stack holds, fetched with its fuel; none when not even one can be. */
    private static Search load(ServerLevel level, Villager worker, WorkPostBlockEntity post, Read read, BlockPos at, Recipes.Step step, boolean logFuel) {
        var kind = kindAt(read, at);
        var holder = RecipeBook.recipe(level, step.rule());
        if (holder.isEmpty()) return Search.none();
        var recipe = holder.get().value();
        int n = step.times();
        for (var pick : step.picks()) n = Math.min(n, new ItemStack(RecipeBook.item(pick)).getMaxStackSize());
        if (kind == Station.POT) n = Math.min(n, recipe.getResultItem(level.registryAccess()).getMaxStackSize());
        var stored = read.facts().stored();
        ItemStack fuel = ItemStack.EMPTY;
        int cookTicks = RecipeBook.cookTicks(recipe);
        if (kind.burns()) {
            var be = level.getBlockEntity(at);
            var slot = be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity c ? c.getItem(1) : ItemStack.EMPTY;
            String fuelItem = !slot.isEmpty() ? RecipeBook.key(slot.getItem()) : logFuel ? step.picks().get(0) : mostFuel(stored);
            if (fuelItem == null) return Search.none();
            var one = new ItemStack(RecipeBook.item(fuelItem));
            int burn = Math.max(1, Stations.burnTicks(be, one));
            int inSlot = slot.isEmpty() ? 0 : slot.getCount();
            int burnLeft = Stations.burnLeft(be);
            // As many as the fuel covers: logs burnt under logs come out of the same stock.
            int units = 0;
            for (; n >= 1; n--) {
                units = Fuel.units(n, cookTicks, burn, burnLeft, inSlot, one.getMaxStackSize() - inSlot);
                int fuelHave = stored.getOrDefault(fuelItem, 0) - (logFuel ? n : 0);
                boolean covered = (long) (units + inSlot) * burn + burnLeft >= (long) n * cookTicks;
                if (units <= fuelHave && covered) break;
            }
            if (n < 1) return Search.none();
            if (units > 0) fuel = one.copyWithCount(units);
        }
        boolean container = false;
        if (kind == Station.POT) {
            int ingredients = 0;
            for (var i : recipe.getIngredients()) if (!i.isEmpty()) ingredients++;
            container = step.rule().cells().size() > ingredients;
        }
        var want = new LinkedHashMap<String, Integer>();
        for (var pick : step.picks()) want.merge(pick, n, Integer::sum);
        if (!fuel.isEmpty()) want.merge(RecipeBook.key(fuel.getItem()), fuel.getCount(), Integer::sum);
        var takes = Storage.takes(level, post, want);
        if (takes.isEmpty()) return Search.none();
        var legs = new ArrayList<WorkshopTask.Leg>();
        for (var t : takes.get()) legs.add(new WorkshopTask.Fetch(t.at(), t.items()));
        legs.add(new WorkshopTask.Load(at, kind, step.rule(), step.picks(), n, fuel, cookTicks, container, post));
        post.claim(worker.getUUID(), new WorkPostBlockEntity.Claim(at, step.rule().result(), n * step.rule().yield(), level.getGameTime()));
        return found(post, legs, "load " + step.rule().id() + " x" + n);
    }

    private static String mostFuel(Map<String, Integer> stored) {
        String best = null;
        for (var e : stored.entrySet())
            if (new ItemStack(RecipeBook.item(e.getKey())).is(FUEL) && (best == null || e.getValue() > stored.get(best))) best = e.getKey();
        return best;
    }

    /** effects: the task that makes up to {@code step.times()} crafts at the station: as many as the
     * worker can carry the picks of, with a knife fetched into its hand first for a cutting board. */
    private static Search make(ServerLevel level, Villager worker, WorkPostBlockEntity post, Read read, BlockPos at, Recipes.Step step) {
        var kind = kindAt(read, at);
        int n = step.times();
        while (n > 1 && slots(step, n) > CARRY) n--;
        if (slots(step, n) > CARRY) return Search.none();
        var want = new LinkedHashMap<String, Integer>();
        for (var e : step.perCraft().entrySet()) want.put(e.getKey(), e.getValue() * n);
        var takes = Storage.takes(level, post, want);
        if (takes.isEmpty()) return Search.none();
        var legs = new ArrayList<WorkshopTask.Leg>();
        if (kind == Station.BOARD && !FarmersDelightCompat.knife(worker.getItemBySlot(EquipmentSlot.MAINHAND))) {
            var knife = Storage.holding(level, post, FarmersDelightCompat::knife);
            if (knife.isEmpty()) return new Search(Optional.empty(), Optional.of(Need.NO_TOOL));
            legs.add(new WorkshopTask.FetchOne(knife.get(), FarmersDelightCompat::knife, true));
        }
        for (var t : takes.get()) legs.add(new WorkshopTask.Fetch(t.at(), t.items()));
        legs.add(new WorkshopTask.Make(at, kind, step.rule(), step.picks(), n));
        post.claim(worker.getUUID(), new WorkPostBlockEntity.Claim(at, step.rule().result(), n * step.rule().yield(), level.getGameTime()));
        return found(post, legs, "make " + step.rule().id() + " x" + n);
    }

    /** effects: the slots the picks of {@code n} crafts fill. */
    private static int slots(Recipes.Step step, int n) {
        int slots = 0;
        for (var e : step.perCraft().entrySet()) {
            int max = new ItemStack(RecipeBook.item(e.getKey())).getMaxStackSize();
            slots += (e.getValue() * n + max - 1) / max;
        }
        return slots;
    }

    private static Search mend(ServerLevel level, Villager worker, WorkPostBlockEntity post, Read read, BlockPos anvil, Repair.Worn worn) {
        var where = Storage.holding(level, post, s -> s.isDamaged() && RecipeBook.key(s.getItem()).equals(worn.item()));
        if (where.isEmpty()) return Search.none();
        var tool = new ItemStack(RecipeBook.item(worn.item()));
        var material = new LinkedHashMap<String, Integer>();
        int left = worn.spend();
        for (var e : read.facts().stored().entrySet()) {
            if (left <= 0) break;
            if (!tool.getItem().isValidRepairItem(tool, new ItemStack(RecipeBook.item(e.getKey())))) continue;
            int n = Math.min(left, e.getValue());
            material.put(e.getKey(), n);
            left -= n;
        }
        var takes = Storage.takes(level, post, material);
        if (takes.isEmpty()) return Search.none();
        var legs = new ArrayList<WorkshopTask.Leg>();
        legs.add(new WorkshopTask.FetchOne(where.get(), s -> s.isDamaged() && RecipeBook.key(s.getItem()).equals(worn.item()), false));
        for (var t : takes.get()) legs.add(new WorkshopTask.Fetch(t.at(), t.items()));
        legs.add(new WorkshopTask.Mend(anvil, worn.item(), worn.spend()));
        post.claim(worker.getUUID(), new WorkPostBlockEntity.Claim(anvil, worn.item(), 0, level.getGameTime()));
        return found(post, legs, "mend " + worn.item());
    }

    private static Search found(WorkPostBlockEntity post, List<WorkshopTask.Leg> legs, String what) {
        return new Search(Optional.of(new WorkshopTask(post, legs, what)), Optional.empty());
    }
}
