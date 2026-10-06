/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.domain.Values;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.job.RecipeBook;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/** Every item's base value (D-0006), worked out by {@link Values} from: the data packs' values in
 * {@code data/<namespace>/serfdom/values/<name>.json}, files in order of id, entries in file order,
 * a tag named with a leading {@code #}:
 * <pre>{"values": {"minecraft:bone_meal": 0.1, "#minecraft:saplings": 0.2}}</pre>
 * then every profession's price list as the server has it (vanilla's and the pack's, after NeoForge's
 * trade event), each listing tried at each level with three seeds by a villager made for it and never
 * added to the world, where a map listing finds no structure (D-0009): drawing a map searches the world
 * for one and saves a new map, and a map's offer, emeralds and a compass, is never a simple one anyway;
 * then food at bread's rate. Worked out once the server has started and again after a reload, the
 * first time it is asked for. */
public final class Prices extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final int SEEDS = 3;
    private static volatile Map<String, Double> items = Map.of();
    private static volatile List<Map.Entry<String, Double>> tags = List.of();
    private static volatile Map<Item, Double> table = Map.of();
    private static volatile boolean stale = true;
    /** The thread asking the price lists for their offers, while it does; null otherwise. */
    private static volatile Thread sampler;
    /** The structure searches refused in the last build; written by the sampler only. */
    private static int refused;

    public Prices() { super(GSON, "serfdom/values"); }

    /** effects: true, and counted, when the caller is the price lists being sampled (D-0009), so that
     * the structure search it asks for finds nothing; false for every other caller. Asked by
     * {@code ServerLevelMixin} before every map's structure search. */
    public static boolean refusesSearch() {
        if (sampler != Thread.currentThread()) return false;
        refused++;
        return true;
    }

    /** effects: how many structure searches the last build refused. For the log and the GameTests. */
    public static int refusedSearches() { return refused; }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var it = new LinkedHashMap<String, Double>();
        var tg = new ArrayList<Map.Entry<String, Double>>();
        new TreeMap<>(files).forEach((id, json) -> {
            try {
                for (var e : json.getAsJsonObject().getAsJsonObject("values").entrySet()) {
                    double v = e.getValue().getAsDouble();
                    if (e.getKey().startsWith("#")) tg.add(Map.entry(e.getKey().substring(1), v));
                    else it.put(ResourceLocation.parse(e.getKey()).toString(), v);
                }
            } catch (RuntimeException e) {
                LOG.error("Serfdom: values {} left out: {}", id, e.getMessage());
            }
        });
        items = Map.copyOf(it);
        tags = List.copyOf(tg);
        stale = true;
    }

    /** effects: the item's base value in emeralds an item; empty when nobody wants it. */
    public static OptionalDouble value(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) return OptionalDouble.empty();
        if (stale) build(level);
        var v = table.get(stack.getItem());
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
    }

    /** effects: the values worked out again now. Public for the GameTests. */
    public static synchronized void build(ServerLevel level) {
        long started = System.nanoTime();
        var tagged = new ArrayList<Values.Tagged>();
        for (var t : tags) {
            var members = new HashSet<String>();
            BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, ResourceLocation.parse(t.getKey()))).forEach(h -> members.add(RecipeBook.key(h.value())));
            tagged.add(new Values.Tagged(t.getKey(), members, t.getValue()));
        }
        var prices = sample(level);
        var nutrition = new HashMap<String, Integer>();
        for (var item : BuiltInRegistries.ITEM) {
            var food = new ItemStack(item).get(DataComponents.FOOD);
            if (food == null || food.nutrition() <= 0) continue;
            boolean harmful = food.effects().stream().anyMatch(e -> e.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL);
            if (!harmful && !new ItemStack(item).is(Kitchen.NOT_EATEN)) nutrition.put(RecipeBook.key(item), food.nutrition());
        }
        var out = Values.table(new Values.Facts(items, tagged, prices, nutrition));
        var t = new HashMap<Item, Double>();
        out.forEach((k, v) -> t.put(RecipeBook.item(k), v));
        table = Map.copyOf(t);
        stale = false;
        LOG.info("Serfdom: base values for {} items ({} from the price lists, {} map searches refused), worked out in {} ms",
                table.size(), prices.size(), refused, (System.nanoTime() - started) / 1_000_000);
    }

    /** effects: the prices every simple offer of every profession's price list shows, by item. */
    private static Map<String, List<Double>> sample(ServerLevel level) {
        var out = new HashMap<String, List<Double>>();
        refused = 0;
        sampler = Thread.currentThread();
        try {
            for (var entry : VillagerTrades.TRADES.entrySet()) {
                var trader = new Villager(EntityType.VILLAGER, level);
                for (var byLevel : entry.getValue().int2ObjectEntrySet()) {
                    trader.setVillagerData(trader.getVillagerData().setProfession(entry.getKey()).setLevel(Math.clamp(byLevel.getIntKey(), 1, 5)));
                    for (var listing : byLevel.getValue()) {
                        for (int seed = 0; seed < SEEDS; seed++) {
                            try {
                                var offer = listing.getOffer(trader, RandomSource.create(seed));
                                if (offer == null) continue;
                                var b = offer.getCostB();
                                Values.seen(RecipeBook.key(offer.getCostA().getItem()), offer.getCostA().getCount(), b.isEmpty() ? "" : RecipeBook.key(b.getItem()), b.getCount(),
                                        RecipeBook.key(offer.getResult().getItem()), offer.getResult().getCount())
                                        .ifPresent(s -> out.computeIfAbsent(s.item(), k -> new ArrayList<>()).add(s.each()));
                            } catch (RuntimeException e) {
                                // A listing that needs more than a bare villager gives no price.
                            }
                        }
                    }
                }
                trader.discard();
            }
        } finally {
            sampler = null;
        }
        return out;
    }
}
