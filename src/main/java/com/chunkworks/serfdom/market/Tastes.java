/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Climate;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Taste;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.job.RecipeBook;
import com.chunkworks.serfdom.job.Storage;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** What a villager will pay, beyond an item's base value (D-0006, 4b), and what it wants: its
 * village's climate, from the biome at its bed ({@code #serfdom:climate/hot} and
 * {@code #serfdom:climate/cold} biome tags), against where an item is from (the item tags of the same
 * names); its taste ({@link Taste}), drawn from its UUID and leaning by its profession's data, against
 * an item's categories (the item tags {@code #serfdom:taste/food}, {@code tools}, {@code decor} and
 * {@code luxury}); and how many of an item it has at home. */
public final class Tastes {
    public static final TagKey<Biome> HOT_BIOMES = TagKey.create(Registries.BIOME, Serfdom.id("climate/hot"));
    public static final TagKey<Biome> COLD_BIOMES = TagKey.create(Registries.BIOME, Serfdom.id("climate/cold"));
    public static final TagKey<Item> HOT = TagKey.create(Registries.ITEM, Serfdom.id("climate/hot"));
    public static final TagKey<Item> COLD = TagKey.create(Registries.ITEM, Serfdom.id("climate/cold"));
    private static final TagKey<Item> FOOD = TagKey.create(Registries.ITEM, Serfdom.id("taste/food"));
    private static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, Serfdom.id("taste/tools"));
    private static final TagKey<Item> DECOR = TagKey.create(Registries.ITEM, Serfdom.id("taste/decor"));
    private static final TagKey<Item> LUXURY = TagKey.create(Registries.ITEM, Serfdom.id("taste/luxury"));
    private Tastes() {}

    static double climateBonus() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.CLIMATE_BONUS.get() : 1.5; }
    static double spread() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.TASTE_SPREAD.get() : 0.5; }
    static int wantsEach() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.WANTS_EACH.get() : 3; }
    static double needBonus() { return SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.NEED_BONUS.get() : 1.5; }

    /** effects: the taste of a villager of {@code profession} with UUID {@code id}. */
    public static Taste of(VillagerProfession profession, UUID id) {
        return Taste.of(id.getMostSignificantBits(), id.getLeastSignificantBits(), Needs.lean(profession), spread());
    }

    /** effects: the villager's taste. */
    public static Taste of(Villager villager) { return of(villager.getVillagerData().getProfession(), villager.getUUID()); }

    /** effects: the climate of the biome at {@code pos}. */
    public static Climate climate(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        return Climate.of(biome.is(HOT_BIOMES), biome.is(COLD_BIOMES));
    }

    /** effects: the villager's climate: its village's, from the biome at its bed; where it stands when
     * it has no bed here. */
    public static Climate climate(ServerLevel level, Villager villager) {
        return climate(level, Shoppers.bed(level, villager).orElse(villager.blockPosition()));
    }

    /** effects: the categories of goods the item is in. */
    public static Set<Taste.Category> categories(ItemStack stack) {
        var out = EnumSet.noneOf(Taste.Category.class);
        if (stack.is(FOOD)) out.add(Taste.Category.FOOD);
        if (stack.is(TOOLS)) out.add(Taste.Category.TOOLS);
        if (stack.is(DECOR)) out.add(Taste.Category.DECOR);
        if (stack.is(LUXURY)) out.add(Taste.Category.LUXURY);
        return out;
    }

    /** effects: the climates the item is from: none for an untagged item. */
    public static Set<Climate> origins(ItemStack stack) {
        var out = EnumSet.noneOf(Climate.class);
        if (stack.is(HOT)) out.add(Climate.HOT);
        if (stack.is(COLD)) out.add(Climate.COLD);
        return out;
    }

    /** effects: what the villager multiplies the item's base value by: its climate's, its taste's,
     * and for a need the need's. */
    public static double bonus(ServerLevel level, Villager villager, ItemStack stack, boolean need) {
        return climate(level, villager).factor(origins(stack), climateBonus()) * of(villager).factor(categories(stack)) * (need ? needBonus() : 1.0);
    }

    /** effects: true iff the item is from the villager's favourite category. */
    public static boolean favourite(Villager villager, ItemStack stack) { return of(villager).favourite(categories(stack)); }

    /** effects: how many of the item the villager wants (4b): up to a few of anything with a value in a
     * category it likes, counted against its household (free) or its home chest (a worker, which wants
     * nothing without a home chest that has room for it); none for a captive or a child. */
    public static int wanted(ServerLevel level, Villager villager, ItemStack stack) {
        if (villager.isBaby() || Workers.of(villager).captive()) return 0;
        boolean valued = Prices.value(level, stack).isPresent();
        String key = RecipeBook.key(stack.getItem());
        int has;
        if (!Workers.owned(villager)) has = Households.of(villager).goods().getOrDefault(key, 0);
        else {
            var home = Kitchen.home(level, villager);
            var handler = home.flatMap(p -> Storage.handler(level, p));
            if (handler.isEmpty() || !ItemHandlerHelper.insertItemStacked(handler.get(), stack.copyWithCount(1), true).isEmpty()) return 0;
            has = Kitchen.counts(level, home.get()).getOrDefault(key, 0);
        }
        return of(villager).wants(categories(stack), valued, has, wantsEach());
    }

    /** effects: where what it buys for a want goes: a free villager's household, a worker's home
     * chest. */
    public static Shopping.Dest wantsTo(Villager villager) { return Workers.owned(villager) ? Shopping.Dest.HOME : Shopping.Dest.HOUSEHOLD; }
}
