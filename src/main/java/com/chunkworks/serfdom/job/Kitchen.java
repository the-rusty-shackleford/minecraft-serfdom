/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Assignment;
import com.chunkworks.serfdom.domain.Hunger;
import com.chunkworks.serfdom.domain.Menu;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.post.Posts;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Where a worker's meals come from (D-0005): its home chest (the storage nearest its bed, within
 * {@link #HOME_REACH} blocks), its owner's Work Posts within the bed-to-post reach (the canteen,
 * Rusty's call), and the free stations within {@link #KITCHEN_REACH} blocks of the home chest. Read
 * when a meal begins and between bites, never per tick. */
public final class Kitchen {
    public static final int HOME_REACH = 8, KITCHEN_REACH = 16;
    /** The stations a worker cooks its own meal at. */
    public static final Set<Station> KINDS = EnumSet.of(Station.TABLE, Station.FURNACE, Station.SMOKER, Station.CAMPFIRE, Station.POT);
    /** The stations that say what food is still raw. */
    public static final Set<Station> HEAT = EnumSet.of(Station.FURNACE, Station.SMOKER, Station.CAMPFIRE);
    /** Food a worker never eats though nothing harms it: golden apples, chorus fruit. */
    public static final TagKey<Item> NOT_EATEN = TagKey.create(Registries.ITEM, Serfdom.id("not_eaten"));
    /** What a worker makes at a crafting table for a meal: bread, stews. */
    public static final TagKey<Item> TABLE_MEALS = TagKey.create(Registries.ITEM, Serfdom.id("table_meals"));
    private Kitchen() {}

    /** effects: the worker's home chest: the storage nearest its bed within {@link #HOME_REACH}
     * blocks; empty without a bed in this level or such a container. */
    public static Optional<BlockPos> home(ServerLevel level, Villager worker) {
        var bed = Workers.of(worker).bed();
        if (bed.isEmpty() || bed.get().dimension() != level.dimension()) return Optional.empty();
        var at = bed.get().pos();
        BlockPos best = null;
        for (int cx = (at.getX() - HOME_REACH) >> 4; cx <= (at.getX() + HOME_REACH) >> 4; cx++)
            for (int cz = (at.getZ() - HOME_REACH) >> 4; cz <= (at.getZ() + HOME_REACH) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var pos : chunk.getBlockEntitiesPos()) {
                    if (pos.distSqr(at) > HOME_REACH * HOME_REACH || (best != null && pos.distSqr(at) >= best.distSqr(at))) continue;
                    if (Storage.storage(level, pos)) best = pos.immutable();
                }
            }
        return Optional.ofNullable(best);
    }

    /** effects: the worker's owner's loaded posts within the bed-to-post reach of its bed, nearest
     * first. */
    public static List<WorkPostBlockEntity> canteen(ServerLevel level, Villager worker) {
        var w = Workers.of(worker);
        if (w.bed().isEmpty() || w.owner().isEmpty() || w.bed().get().dimension() != level.dimension()) return List.of();
        return Posts.near(level, w.bed().get().pos(), Assignment.MAX_BED_TO_POST, w.owner().get());
    }

    /** effects: the stations within {@link #KITCHEN_REACH} blocks of {@code home} a worker can cook
     * its meal at now, nearest first: empty, lit and heated; one that burns only when {@code fuel}
     * (the home chest holds fuel) or it already burns. */
    public static List<Stations.Found> free(ServerLevel level, BlockPos home, boolean fuel) {
        var out = new ArrayList<Stations.Found>();
        for (var f : Stations.scan(level, home, KITCHEN_REACH, KINDS)) if (freeNow(level, f, fuel)) out.add(f);
        return out;
    }

    /** effects: whether the worker can cook at the station now (see {@link #free}). */
    public static boolean freeNow(ServerLevel level, Stations.Found f, boolean fuel) {
        if (!level.isLoaded(f.pos())) return false;
        var state = level.getBlockState(f.pos());
        var be = level.getBlockEntity(f.pos());
        return switch (f.kind()) {
            case FURNACE, SMOKER, BLAST -> be instanceof AbstractFurnaceBlockEntity c && c.getItem(0).isEmpty() && c.getItem(2).isEmpty()
                    && (fuel || Stations.burnLeft(be) > 0 || !c.getItem(1).isEmpty());
            case CAMPFIRE -> state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) && Stations.fire(be).stream().allMatch(ItemStack::isEmpty);
            case POT -> {
                var slots = com.chunkworks.serfdom.compat.FarmersDelightCompat.potSlots(be);
                boolean empty = true;
                for (int i = 0; i < slots.getSlots(); i++) empty &= slots.getStackInSlot(i).isEmpty();
                yield empty && com.chunkworks.serfdom.compat.FarmersDelightCompat.heated(be);
            }
            case TABLE -> true;
            default -> false;
        };
    }

    /** effects: how many of each item the container at {@code pos} holds; empty when it is gone. */
    public static Map<String, Integer> counts(ServerLevel level, BlockPos pos) {
        var out = new HashMap<String, Integer>();
        Storage.handler(level, pos).ifPresent(h -> {
            for (int i = 0; i < h.getSlots(); i++) {
                var s = h.getStackInSlot(i);
                if (!s.isEmpty()) out.merge(RecipeBook.key(s.getItem()), s.getCount(), Integer::sum);
            }
        });
        return out;
    }

    /** effects: true iff the container holds some of the server's fuel (#serfdom:fuel). */
    public static boolean holdsFuel(ServerLevel level, BlockPos pos) {
        var h = Storage.handler(level, pos).orElse(null);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (h.getStackInSlot(i).is(WorkshopJob.FUEL)) return true;
        return false;
    }

    /** effects: the fuel item the container holds most of; empty when it holds none. */
    public static Optional<String> mostFuel(ServerLevel level, BlockPos pos) {
        var counts = new HashMap<String, Integer>();
        Storage.handler(level, pos).ifPresent(h -> {
            for (int i = 0; i < h.getSlots(); i++) {
                var s = h.getStackInSlot(i);
                if (s.is(WorkshopJob.FUEL)) counts.merge(RecipeBook.key(s.getItem()), s.getCount(), Integer::sum);
            }
        });
        return counts.entrySet().stream().max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey);
    }

    /** effects: what each item fills for {@code worker}, for the items that are food: refused when it
     * has a harmful effect or is in #serfdom:not_eaten; what eating it leaves behind. */
    public static Map<String, Menu.Food> foods(Collection<String> items, Villager worker) {
        var out = new HashMap<String, Menu.Food>();
        for (var id : items) {
            var stack = new ItemStack(RecipeBook.item(id));
            var food = stack.getFoodProperties(worker);
            if (food == null) continue;
            boolean harmful = food.effects().stream().anyMatch(e -> e.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL);
            var leaves = food.usingConvertsTo().filter(s -> !s.isEmpty()).or(() -> Optional.of(stack.getCraftingRemainingItem()).filter(s -> !s.isEmpty()))
                    .map(s -> RecipeBook.key(s.getItem()));
            out.put(id, new Menu.Food(food.nutrition(), harmful || stack.is(NOT_EATEN), leaves));
        }
        return out;
    }

    /** effects: the table meals, from #serfdom:table_meals. */
    public static Set<String> tableMeals() {
        var out = new HashSet<String>();
        BuiltInRegistries.ITEM.getTagOrEmpty(TABLE_MEALS).forEach(h -> out.add(RecipeBook.key(h.value())));
        return out;
    }

    /** effects: the facts of one bite, read now: the worker's hunger, its home chest's stock, the
     * canteen's stock post by post, the recipes of the free stations near home, what fills. */
    public static Menu.Facts facts(ServerLevel level, Villager worker, Hunger hunger, BlockPos home, List<WorkPostBlockEntity> canteen, List<Stations.Found> free) {
        var homeStock = counts(level, home);
        var posts = new ArrayList<Map<String, Integer>>();
        for (var post : canteen) posts.add(Storage.counts(level, post));
        var heat = RecipeBook.rules(level, HEAT);
        var kinds = EnumSet.noneOf(Station.class);
        for (var f : free) kinds.add(f.kind());
        var kitchen = kinds.isEmpty() ? com.chunkworks.serfdom.domain.Recipes.Rules.NONE : RecipeBook.rules(level, kinds);
        var items = new HashSet<String>(homeStock.keySet());
        for (var p : posts) items.addAll(p.keySet());
        for (var r : heat.all()) items.add(r.result());
        for (var r : kitchen.all()) items.add(r.result());
        return new Menu.Facts(hunger, homeStock, posts, foods(items, worker), heat, kitchen, tableMeals());
    }

    /** effects: puts everything {@code carried} holds into the container at {@code pos} as far as it
     * goes and drops the rest at the worker's feet; {@code carried} is empty after. */
    public static void putBack(ServerLevel level, Villager worker, Optional<BlockPos> pos, SimpleContainer carried) {
        IItemHandler h = pos.flatMap(p -> Storage.handler(level, p)).orElse(null);
        for (int i = 0; i < carried.getContainerSize(); i++) {
            var s = carried.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            var rest = h == null ? s : ItemHandlerHelper.insertItemStacked(h, s, false);
            if (!rest.isEmpty()) worker.spawnAtLocation(rest);
        }
    }
}
