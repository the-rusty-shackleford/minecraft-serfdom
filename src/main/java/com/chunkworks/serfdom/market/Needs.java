/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Household;
import com.chunkworks.serfdom.domain.Hunger;
import com.chunkworks.serfdom.domain.Menu;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.job.RecipeBook;
import com.chunkworks.serfdom.job.Storage;
import com.chunkworks.serfdom.job.WorkshopJob;
import com.chunkworks.serfdom.post.Posts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** What villagers need (D-0006). A free villager's needs come from its profession's list in
 * {@code data/<namespace>/serfdom/needs/<name>.json}; a list for {@code "*"} is everyone's and comes
 * first:
 * <pre>{"profession": "minecraft:farmer", "needs": [
 *   {"name": "hoe", "tag": "minecraft:hoes", "keep": 1, "per_day": 0.125},
 *   {"name": "bone_meal", "items": ["minecraft:bone_meal"], "keep": 8, "per_day": 1}]}
 * {"profession": "*", "needs": [{"name": "food", "food": true, "keep": 4, "per_day": 2}]}</pre>
 * {@code "food": true} is any ready food (D-0005: it fills, nothing harmful, and no heat recipe makes
 * it better). A hired worker's needs are its own: food when its home chest and the canteen hold less
 * than a day's, and what its post shows it lacks. A captive needs nothing it could buy. */
public final class Needs extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    /** How much fuel a worker without any buys. */
    static final int FUEL = 8;
    public static final String EVERYONE = "*";

    private record Entry(String name, boolean food, Optional<String> tag, List<String> items, int keep, double perDay) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Codec.BOOL.optionalFieldOf("food", false).forGetter(Entry::food),
                Codec.STRING.optionalFieldOf("tag").forGetter(Entry::tag),
                Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Entry::items),
                Codec.intRange(0, 999).fieldOf("keep").forGetter(Entry::keep),
                Codec.doubleRange(0, 64).fieldOf("per_day").forGetter(Entry::perDay)).apply(i, Entry::new));
    }
    private record File(String profession, List<Entry> needs) {
        static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("profession").forGetter(File::profession),
                Entry.CODEC.listOf().fieldOf("needs").forGetter(File::needs)).apply(i, File::new));
    }

    private static volatile Map<String, List<Entry>> lists = Map.of();
    private static final Map<String, List<Household.Need>> RESOLVED = new ConcurrentHashMap<>();
    private static volatile Set<String> readyFoods;

    public Needs() { super(GSON, "serfdom/needs"); }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var out = new HashMap<String, List<Entry>>();
        new TreeMap<>(files).forEach((id, json) -> {
            try {
                var f = File.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                out.computeIfAbsent(f.profession(), k -> new ArrayList<>()).addAll(f.needs());
            } catch (RuntimeException e) {
                LOG.error("Serfdom: needs {} left out: {}", id, e.getMessage());
            }
        });
        lists = Map.copyOf(out);
        RESOLVED.clear();
        readyFoods = null;
    }

    /** effects: the needs of a free villager of {@code profession}: everyone's, then its own. */
    public static List<Household.Need> of(ServerLevel level, VillagerProfession profession) {
        var id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();
        return RESOLVED.computeIfAbsent(id, k -> {
            var out = new ArrayList<Household.Need>();
            for (var e : lists.getOrDefault(EVERYONE, List.of())) out.add(resolve(level, e));
            for (var e : lists.getOrDefault(k, List.of())) out.add(resolve(level, e));
            return List.copyOf(out);
        });
    }

    private static Household.Need resolve(ServerLevel level, Entry e) {
        var items = new HashSet<String>();
        if (e.food()) items.addAll(readyFoods(level));
        e.tag().ifPresent(t -> BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, ResourceLocation.parse(t))).forEach(h -> items.add(RecipeBook.key(h.value()))));
        for (var i : e.items()) items.add(ResourceLocation.parse(i).toString());
        items.remove("minecraft:air");
        return new Household.Need(e.name(), items, e.keep(), e.perDay());
    }

    /** effects: every ready food: it fills, nothing harmful is in it nor is it refused by tag, and no
     * heat recipe turns it into food that fills more. */
    public static Set<String> readyFoods(ServerLevel level) {
        var known = readyFoods;
        if (known != null) return known;
        var foods = new HashMap<String, Menu.Food>();
        for (var item : BuiltInRegistries.ITEM) {
            var stack = new ItemStack(item);
            var food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            boolean harmful = food.effects().stream().anyMatch(x -> x.effect().getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL);
            foods.put(RecipeBook.key(item), new Menu.Food(food.nutrition(), harmful || stack.is(Kitchen.NOT_EATEN), Optional.empty()));
        }
        var facts = new Menu.Facts(Hunger.FULL, Map.of(), List.of(), foods, RecipeBook.rules(level, Kitchen.HEAT),
                com.chunkworks.serfdom.domain.Recipes.Rules.NONE, Set.of());
        var out = new HashSet<String>();
        for (var id : foods.keySet()) if (Menu.ready(id, facts)) out.add(id);
        readyFoods = Set.copyOf(out);
        return readyFoods;
    }

    /** effects: how much one of the item fills, by its food. */
    public static int fills(ItemStack stack) {
        var food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    /** effects: what the villager wants to buy now, food first: a free villager's household's
     * shortfalls; a hired worker's food and what its post lacks; nothing for a captive or a child. */
    public static List<Shopping.Want> wants(ServerLevel level, Villager villager) {
        if (villager.isBaby()) return List.of();
        var worker = Workers.of(villager);
        if (!worker.owned()) return Households.of(villager).wants(of(level, villager.getVillagerData().getProfession()));
        if (worker.captive()) return List.of();
        var out = new ArrayList<Shopping.Want>();
        var foods = readyFoods(level);
        Shopping.workerFood(foodNear(level, villager, foods), foods).ifPresent(out::add);
        out.addAll(forThePost(level, villager));
        return out;
    }

    /** effects: how much the ready food in the worker's home chest and the canteen fills, counted
     * only as far as a day's. */
    static int foodNear(ServerLevel level, Villager worker, Set<String> foods) {
        int fills = 0;
        var stocks = new ArrayList<Map<String, Integer>>();
        Kitchen.home(level, worker).ifPresent(h -> stocks.add(Kitchen.counts(level, h)));
        for (var post : Kitchen.canteen(level, worker)) stocks.add(Storage.counts(level, post));
        for (var stock : stocks)
            for (var e : stock.entrySet()) {
                if (!foods.contains(e.getKey())) continue;
                fills += e.getValue() * fills(new ItemStack(RecipeBook.item(e.getKey())));
                if (fills >= Shopping.DAYS_FOOD) return fills;
            }
        return fills;
    }

    /** effects: what the worker's post shows it lacks, as wants for the post's chests: its job's tool,
     * fuel, or the materials its stock rows are short of. */
    static List<Shopping.Want> forThePost(ServerLevel level, Villager worker) {
        var need = Workers.shiftNeed(worker);
        var at = Workers.of(worker).post();
        if (need.isEmpty() || at.isEmpty()) return List.of();
        var post = Posts.loaded(level.getServer(), at.get());
        if (post.isEmpty()) return List.of();
        var job = Jobs.get(post.get().job());
        if (job.isEmpty()) return List.of();
        var out = new ArrayList<Shopping.Want>();
        switch (need.get()) {
            case NO_TOOL -> Jobs.tool(job.get()).ifPresent(tag -> {
                var items = new HashSet<String>();
                BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> items.add(RecipeBook.key(h.value())));
                out.add(new Shopping.Want("tool", items, 1, Shopping.Unit.ITEMS, Shopping.Dest.POST));
            });
            case NO_FUEL -> {
                var items = new HashSet<String>();
                BuiltInRegistries.ITEM.getTagOrEmpty(WorkshopJob.FUEL).forEach(h -> items.add(RecipeBook.key(h.value())));
                out.add(new Shopping.Want("fuel", items, FUEL, Shopping.Unit.ITEMS, Shopping.Dest.POST));
            }
            case NO_MATERIALS -> {
                if (job.get().target() != com.chunkworks.serfdom.domain.JobScript.Target.WORKSHOP) break;
                var read = WorkshopJob.read(level, post.get(), job.get(), Optional.of(worker), p -> false);
                int n = 0;
                for (var row : com.chunkworks.serfdom.domain.Workshop.rows(read.facts()))
                    for (var s : row.shortages()) out.add(new Shopping.Want("materials_" + n++, new HashSet<>(s.options()), s.missing(), Shopping.Unit.ITEMS, Shopping.Dest.POST));
            }
            default -> {}
        }
        return out;
    }

}
