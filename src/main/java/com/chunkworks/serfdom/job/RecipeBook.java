/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.Recipes;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.mixin.SmithingAccess;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/** The rule book of everything a workshop's stations make (D-0002), read off the game's recipes once
 * per recipe set, on either side (the client's picker reads the synced recipes):
 * <ul>
 * <li>the crafting table: shaped and shapeless recipes, not special, every ingredient simple;</li>
 * <li>Ranged Weapons Mod's weapons workbench: its {@code rangedweaponsmod:assembly} recipes, laid
 * out on a grid as the table's are;</li>
 * <li>the smithing table: upgrades (a template, a base, an addition), not trims;</li>
 * <li>the blast furnace, smoker, furnace and campfire: their cooking recipes, never taking an item
 * that has durability or does not stack (vanilla melts iron tools into nuggets, and a smith would
 * melt his own swords);</li>
 * <li>Farmer's Delight's cooking pot and cutting board ({@link FarmersDelightCompat#rules}).</li>
 * </ul>
 * And the crafting itself: a recipe found again by its id, laid out and assembled. */
public final class RecipeBook {
    private static final Logger LOG = LogUtils.getLogger();
    /** Ranged Weapons Mod's assembly recipes, made at its weapons workbench. */
    public static final ResourceLocation ASSEMBLY = ResourceLocation.parse("rangedweaponsmod:assembly");
    private RecipeBook() {}

    private record Book(Collection<?> recipes, Recipes.Rules rules, Map<Set<Station>, Recipes.Rules> byKinds) {}
    private static final Map<RecipeManager, Book> BOOKS = new WeakHashMap<>();

    /** effects: the rules made at the {@code kinds} of station, from the level's recipes. */
    public static Recipes.Rules rules(Level level, Set<Station> kinds) { return rules(level.getRecipeManager(), level.registryAccess(), kinds); }

    /** effects: the rules made at the {@code kinds} of station: the whole book reduced, cached per
     * recipe set and per set of kinds. */
    public static Recipes.Rules rules(RecipeManager manager, HolderLookup.Provider registries, Set<Station> kinds) {
        var recipes = manager.getRecipes();
        synchronized (BOOKS) {
            var book = BOOKS.get(manager);
            if (book == null || book.recipes() != recipes) {
                long t0 = System.nanoTime();
                book = new Book(recipes, build(manager, registries), new HashMap<>());
                BOOKS.put(manager, book);
                LOG.info("Serfdom: workshop rule book: {} rules from {} recipes in {} ms", book.rules().size(), recipes.size(), (System.nanoTime() - t0) / 1_000_000);
            }
            var key = Set.copyOf(kinds);
            var whole = book.rules();
            return book.byKinds().computeIfAbsent(key, k -> whole.only(r -> k.contains(r.station())));
        }
    }

    private static Recipes.Rules build(RecipeManager manager, HolderLookup.Provider registries) {
        var rules = new ArrayList<Recipes.Rule>();
        for (var holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            var recipe = holder.value();
            if (recipe.isSpecial() || !(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)) continue;
            grid(holder.id(), recipe, registries, Station.TABLE).ifPresent(rules::add);
        }
        BuiltInRegistries.RECIPE_TYPE.getOptional(ASSEMBLY).ifPresent(type -> {
            @SuppressWarnings({"unchecked", "rawtypes"}) Collection<RecipeHolder<?>> all = (Collection) manager.getAllRecipesFor((RecipeType) type);
            for (var holder : all) grid(holder.id(), holder.value(), registries, Station.WORKBENCH).ifPresent(rules::add);
        });
        for (var holder : manager.getAllRecipesFor(RecipeType.SMITHING)) {
            if (!(holder.value() instanceof SmithingTransformRecipe recipe)) continue;
            var access = (SmithingAccess) recipe;
            var result = recipe.getResultItem(registries);
            var cells = cells(List.of(access.serfdom$template(), access.serfdom$base(), access.serfdom$addition()), false);
            if (result.isEmpty() || cells == null || cells.size() != 3) continue;
            rules.add(new Recipes.Rule(holder.id().toString(), Station.SMITHING, cells, key(result.getItem()), result.getCount()));
        }
        heat(manager, registries, RecipeType.BLASTING, Station.BLAST, rules);
        heat(manager, registries, RecipeType.SMOKING, Station.SMOKER, rules);
        heat(manager, registries, RecipeType.SMELTING, Station.FURNACE, rules);
        heat(manager, registries, RecipeType.CAMPFIRE_COOKING, Station.CAMPFIRE, rules);
        rules.addAll(FarmersDelightCompat.rules(manager, registries));
        return Recipes.Rules.of(rules);
    }

    private static Optional<Recipes.Rule> grid(ResourceLocation id, Recipe<?> recipe, HolderLookup.Provider registries, Station station) {
        var result = recipe.getResultItem(registries);
        if (result.isEmpty()) return Optional.empty();
        var cells = cells(recipe.getIngredients(), false);
        if (cells == null || cells.isEmpty()) return Optional.empty();
        return Optional.of(new Recipes.Rule(id.toString(), station, cells, key(result.getItem()), result.getCount()));
    }

    private static <T extends AbstractCookingRecipe> void heat(RecipeManager manager, HolderLookup.Provider registries, RecipeType<T> type, Station station, List<Recipes.Rule> out) {
        for (var holder : manager.getAllRecipesFor(type)) {
            var recipe = holder.value();
            var result = recipe.getResultItem(registries);
            var cells = cells(recipe.getIngredients(), true);
            if (result.isEmpty() || cells == null || cells.size() != 1) continue;
            out.add(new Recipes.Rule(holder.id().toString(), station, cells, key(result.getItem()), result.getCount()));
        }
    }

    /** effects: the cells of the non-empty ingredients, in order, each the ids it accepts (for a
     * cooking recipe, never an item that has durability or does not stack); null when an
     * ingredient is not simple or accepts nothing it may. A new, modifiable list. */
    public static List<List<String>> cells(List<Ingredient> ingredients, boolean cooking) {
        var cells = new ArrayList<List<String>>();
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) continue;
            if (!ingredient.isSimple()) return null;
            var options = new ArrayList<String>();
            for (var s : ingredient.getItems()) {
                if (cooking && (s.isDamageableItem() || s.getMaxStackSize() == 1)) continue;
                var k = key(s.getItem());
                if (!options.contains(k)) options.add(k);
            }
            if (options.isEmpty()) return null;
            cells.add(options);
        }
        return cells;
    }

    public static String key(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }
    public static Item item(String key) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(key)); }

    // ---- making ---------------------------------------------------------------------------------

    /** effects: the recipe with the rule's id, if the level still has it. */
    public static Optional<RecipeHolder<?>> recipe(ServerLevel level, Recipes.Rule rule) {
        return level.getRecipeManager().byKey(ResourceLocation.parse(rule.id()));
    }

    /** requires: one stack of one item per pick, in the rule's cell order. effects: for a grid
     * recipe (the table's or the workbench's), the picks laid out as its pattern has them, the
     * result assembled and what the craft leaves behind (a bucket from milk); empty when the recipe
     * refuses them in every layout. */
    public static Optional<Made> grid(ServerLevel level, Recipe<?> recipe, List<ItemStack> picks) {
        @SuppressWarnings("unchecked") var crafting = (Recipe<CraftingInput>) recipe;
        var ingredients = recipe.getIngredients();
        var items = new ArrayList<ItemStack>(ingredients.size());
        int next = 0;
        for (var ingredient : ingredients) items.add(ingredient.isEmpty() ? ItemStack.EMPTY : picks.get(next++));
        for (var input : layouts(items)) {
            if (!crafting.matches(input, level)) continue;
            var result = crafting.assemble(input, level.registryAccess());
            if (result.isEmpty()) return Optional.empty();
            var left = new ArrayList<ItemStack>();
            for (var s : crafting.getRemainingItems(input)) if (!s.isEmpty()) left.add(s);
            return Optional.of(new Made(result, left));
        }
        return Optional.empty();
    }

    /** What a craft made, and what it left behind. */
    public record Made(ItemStack result, List<ItemStack> leftovers) {}

    /** effects: every grid the items fill exactly, in one row first (a shapeless recipe's), then
     * each width and height of at most three. */
    private static List<CraftingInput> layouts(List<ItemStack> items) {
        var out = new ArrayList<CraftingInput>();
        int n = items.size();
        out.add(CraftingInput.of(n, 1, items));
        for (int w = 1; w <= 3; w++) for (int h = 1; h <= 3; h++) if (w * h == n && !(w == n && h == 1)) out.add(CraftingInput.of(w, h, items));
        return out;
    }

    /** requires: the picks a smithing upgrade's rule chose, one stack of each. effects: the upgraded
     * item (keeping what the base carries, its enchantments), or empty when refused. */
    public static Optional<Made> smith(ServerLevel level, Recipe<?> recipe, List<ItemStack> picks) {
        if (!(recipe instanceof SmithingRecipe smithing) || picks.size() != 3) return Optional.empty();
        var input = new SmithingRecipeInput(picks.get(0), picks.get(1), picks.get(2));
        if (!smithing.matches(input, level)) return Optional.empty();
        var result = smithing.assemble(input, level.registryAccess());
        return result.isEmpty() ? Optional.empty() : Optional.of(new Made(result, List.of()));
    }

    /** effects: the ticks a cooking recipe takes, 200 when it is not one. */
    public static int cookTicks(Recipe<?> recipe) { return recipe instanceof AbstractCookingRecipe c ? c.getCookingTime() : 200; }
}
