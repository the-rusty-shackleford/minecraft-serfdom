/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.compat;

import com.chunkworks.serfdom.domain.Recipes;
import com.chunkworks.serfdom.domain.Station;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Farmer's Delight, checked against 1.3.3. Nothing here loads a Farmer's Delight class unless the
 * mod is present.
 * <ul>
 * <li><b>Crops (D-0001)</b> that are not plain crops. A tomato (the bottom on farmland and every vine
 * up its rope) is picked at its greatest age as its right-click picks it: one or two tomatoes, now
 * and then a rotten one, and set back to age 0. Ripe rice panicles are cut above the water, and the
 * rice below grows new ones.</li>
 * <li><b>A cook's stations (D-0002).</b> The cooking pot takes ingredients in slots 0 to 5, a meal's
 * container in 7, and gives meals out of 8, and cooks only while heated. The stove cooks campfire
 * recipes and pops them out as a campfire does. The cutting board cuts with a knife.</li>
 * </ul> */
public final class FarmersDelightCompat {
    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded("farmersdelight");
    /** The pot's slots. */
    public static final int POT_INPUTS = 6, POT_DISPLAY = 6, POT_CONTAINER = 7, POT_OUTPUT = 8;
    /** What Farmer's Delight's cutting recipes take as a knife. */
    private static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.parse("farmersdelight:tools/knives"));
    private static final TagKey<Item> C_KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:tools/knife"));
    private FarmersDelightCompat() {}

    public static boolean loaded() { return LOADED; }

    /** What a farmer takes from one of these crops, and the change to the world that takes it. */
    public record Picked(List<ItemStack> drops, Runnable apply) {}

    /** effects: true iff the state is a Farmer's Delight crop with its own rule. */
    public static boolean crop(BlockState state) { return LOADED && Inner.crop(state); }

    /** effects: true iff the state is a young tomato, still budding on its farmland (D-0008: a crop
     * a farmer sows, and that sowing copies). */
    public static boolean budding(BlockState state) { return LOADED && Inner.budding(state); }

    /** requires: {@link #crop}(state). effects: true iff it is ready to take. */
    public static boolean ripe(ServerLevel level, BlockPos pos, BlockState state) { return Inner.ripe(state); }

    /** requires: {@link #crop}(state) and {@link #ripe}. effects: what taking it yields, given what
     * breaking it would drop, and how to take it. */
    public static Picked pick(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) { return Inner.pick(level, pos, state, drops); }

    // ---- a cook's stations ----------------------------------------------------------------------

    /** effects: the station a Farmer's Delight block entity is: a pot, a cutting board, or a stove
     * (a campfire's kind); empty for anything else or without the mod. */
    public static Optional<Station> station(BlockEntity be) { return LOADED ? Inner.station(be) : Optional.empty(); }

    /** requires: a pot. effects: true iff something below heats it. */
    public static boolean heated(BlockEntity pot) { return Inner.heated(pot); }

    /** requires: a pot. effects: its nine slots. */
    public static IItemHandlerModifiable potSlots(BlockEntity pot) { return Inner.potSlots(pot); }

    /** requires: a stove. effects: its six cooking slots. */
    public static IItemHandlerModifiable stoveSlots(BlockEntity stove) { return Inner.stoveSlots(stove); }

    /** requires: a stove, {@code one} a single item. effects: puts it on the stove to cook by its
     * campfire recipe; false when it has none or the stove is full. */
    public static boolean stovePlace(BlockEntity stove, LivingEntity cook, ItemStack one) { return Inner.stovePlace(stove, cook, one); }

    /** requires: a cutting board. effects: true iff nothing lies on it. */
    public static boolean boardEmpty(BlockEntity board) { return Inner.boardEmpty(board); }

    /** effects: true iff the stack is a knife a cutting board takes. */
    public static boolean knife(ItemStack stack) { return !stack.isEmpty() && (stack.is(KNIVES) || stack.is(C_KNIVES)); }

    /** effects: the pot's and the cutting board's rules: a pot's cells are its ingredients and the
     * meal's container (the recipe's, else what the meal leaves behind); a cut's result is the
     * first it always gives. Empty without the mod. */
    public static List<Recipes.Rule> rules(RecipeManager manager, HolderLookup.Provider registries) { return LOADED ? Inner.rules(manager, registries) : List.of(); }

    /** requires: {@code recipe} a cutting recipe's id. effects: cuts {@code input} with
     * {@code knife} at the board at {@code pos}, the knife worn by one and the cut heard: what the
     * cut gives, its chances rolled; empty when the recipe is gone or refuses them. */
    public static List<ItemStack> cut(ServerLevel level, BlockPos pos, ResourceLocation recipe, ItemStack input, ItemStack knife, LivingEntity cook) {
        return Inner.cut(level, pos, recipe, input, knife, cook);
    }

    private static final class Inner {
        static boolean crop(BlockState state) {
            var block = state.getBlock();
            return block instanceof vectorwing.farmersdelight.common.block.TomatoBlock || block instanceof vectorwing.farmersdelight.common.block.RicePaniclesBlock;
        }
        static boolean budding(BlockState state) { return state.getBlock() instanceof vectorwing.farmersdelight.common.block.BuddingTomatoBlock; }
        static boolean ripe(BlockState state) {
            return state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && crop.isMaxAge(state);
        }
        static Picked pick(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
            if (state.getBlock() instanceof vectorwing.farmersdelight.common.block.TomatoBlock tomato) {
                var out = new ArrayList<ItemStack>();
                out.add(new ItemStack(vectorwing.farmersdelight.common.registry.ModItems.TOMATO.get(), 1 + level.random.nextInt(2)));
                if (level.random.nextFloat() < 0.05F) out.add(new ItemStack(vectorwing.farmersdelight.common.registry.ModItems.ROTTEN_TOMATO.get()));
                return new Picked(out, () -> {
                    level.playSound(null, pos, vectorwing.farmersdelight.common.registry.ModSounds.BLOCK_TOMATOES_PICK_TOMATOES.get(), SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                    level.setBlock(pos, state.setValue(tomato.getAgeProperty(), 0), 2);
                });
            }
            return new Picked(drops, () -> level.destroyBlock(pos, false));
        }

        static Optional<Station> station(BlockEntity be) {
            if (be instanceof vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity) return Optional.of(Station.POT);
            if (be instanceof vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity) return Optional.of(Station.BOARD);
            if (be instanceof vectorwing.farmersdelight.common.block.entity.StoveBlockEntity) return Optional.of(Station.CAMPFIRE);
            return Optional.empty();
        }
        static boolean heated(BlockEntity pot) { return ((vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity) pot).isHeated(); }
        static IItemHandlerModifiable potSlots(BlockEntity pot) { return ((vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity) pot).getInventory(); }
        static IItemHandlerModifiable stoveSlots(BlockEntity stove) { return ((vectorwing.farmersdelight.common.block.entity.StoveBlockEntity) stove).getItems(); }
        static boolean stovePlace(BlockEntity be, LivingEntity cook, ItemStack one) {
            var stove = (vectorwing.farmersdelight.common.block.entity.StoveBlockEntity) be;
            var recipe = stove.getCookingRecipe(one);
            return recipe.isPresent() && stove.placeFood(cook, one, recipe.get());
        }
        static boolean boardEmpty(BlockEntity board) { return ((vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity) board).getStoredItem().isEmpty(); }

        static List<Recipes.Rule> rules(RecipeManager manager, HolderLookup.Provider registries) {
            var out = new ArrayList<Recipes.Rule>();
            for (var holder : manager.getAllRecipesFor(vectorwing.farmersdelight.common.registry.ModRecipeTypes.COOKING.get())) {
                var recipe = holder.value();
                var result = recipe.getResultItem(registries);
                if (result.isEmpty()) continue;
                var cells = com.chunkworks.serfdom.job.RecipeBook.cells(recipe.getIngredients(), false);
                if (cells == null || cells.isEmpty()) continue;
                var container = recipe.getOutputContainer().isEmpty() ? result.getCraftingRemainingItem() : recipe.getOutputContainer();
                if (!container.isEmpty()) cells.add(List.of(com.chunkworks.serfdom.job.RecipeBook.key(container.getItem())));
                out.add(new Recipes.Rule(holder.id().toString(), Station.POT, cells, com.chunkworks.serfdom.job.RecipeBook.key(result.getItem()), result.getCount()));
            }
            for (var holder : manager.getAllRecipesFor(vectorwing.farmersdelight.common.registry.ModRecipeTypes.CUTTING.get())) {
                var recipe = holder.value();
                var sure = recipe.getRollableResults().stream().filter(r -> r.chance() >= 1.0F).findFirst();
                if (sure.isEmpty() || sure.get().stack().isEmpty()) continue;
                var cells = com.chunkworks.serfdom.job.RecipeBook.cells(recipe.getIngredients(), false);
                if (cells == null || cells.size() != 1) continue;
                var stack = sure.get().stack();
                out.add(new Recipes.Rule(holder.id().toString(), Station.BOARD, cells, com.chunkworks.serfdom.job.RecipeBook.key(stack.getItem()), stack.getCount()));
            }
            return out;
        }

        static List<ItemStack> cut(ServerLevel level, BlockPos pos, ResourceLocation id, ItemStack input, ItemStack knife, LivingEntity cook) {
            var holder = level.getRecipeManager().byKey(id).orElse(null);
            if (holder == null || !(holder.value() instanceof vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe recipe)) return List.of();
            var in = new vectorwing.farmersdelight.common.crafting.CuttingBoardRecipeInput(input, knife);
            if (!recipe.matches(in, level)) return List.of();
            var handler = new net.neoforged.neoforge.items.ItemStackHandler(net.minecraft.core.NonNullList.of(ItemStack.EMPTY, input.copy()));
            var results = recipe.rollResults(level.random, 0, new net.neoforged.neoforge.items.wrapper.RecipeWrapper(handler));
            var sound = recipe.getSoundEvent().orElse(vectorwing.farmersdelight.common.registry.ModSounds.BLOCK_CUTTING_BOARD_KNIFE.get());
            level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
            knife.hurtAndBreak(1, level, cook, item -> cook.onEquippedItemBroken(item, net.minecraft.world.entity.EquipmentSlot.MAINHAND));
            return results.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
        }
    }
}
