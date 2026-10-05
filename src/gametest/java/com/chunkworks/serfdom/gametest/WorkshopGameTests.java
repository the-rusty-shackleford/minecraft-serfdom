/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.Stock;
import com.chunkworks.serfdom.domain.WorkDay;
import com.chunkworks.serfdom.domain.Workshop;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 1b (D-0002), with Farmer's Delight, Ranged Weapons Mod and Metals
 * and Materials loaded:
 * <ul>
 * <li>the blacksmith: two pickaxes from raw iron (the blast furnace before the furnace, one coal for
 * six, the sticks from planks, no third); a netherite sword four stations deep keeping the diamond
 * sword's enchantment; raw copper smelted beside gear never melted; a worn axe mended at the anvil
 * in whole quarters; charcoal made from logs when there is no coal; a player's furnace left alone;
 * two smiths making one sword between them; a rifle at the weapons workbench, three levels deep;
 * no fuel shown until coal is stored;</li>
 * <li>the cook: bread at the table; steaks in the smoker to the number kept and no further; beef
 * stew in a heated pot, and "no station" once its fire is out; minced beef cut on the board with a
 * knife fetched from the chest; a cake whose buckets come back.</li>
 * </ul>
 * Every test runs in the working hours, set and held before its batch. */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class WorkshopGameTests {
    @BeforeBatch(batch = "smithy") public static void smithy(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 200); }
    @BeforeBatch(batch = "kitchen") public static void kitchen(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 200); }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)); }
    private static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)); }
    private static void keep(WorkPostBlockEntity post, String item, int n) {
        post.setStock(post.stock().with(post.stock().rows().size(), new Stock.Row(item, n)));
    }
    private static AbstractFurnaceBlockEntity furnace(GameTestHelper h, BlockPos pos) { return (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(pos); }
    private static boolean empty(GameTestHelper h, BlockPos furnace) {
        var f = furnace(h, furnace);
        return f.getItem(0).isEmpty() && f.getItem(1).isEmpty() && f.getItem(2).isEmpty();
    }
    private static int carried(Villager v, Item item) {
        int n = 0;
        for (int i = 0; i < v.getInventory().getContainerSize(); i++) if (v.getInventory().getItem(i).is(item)) n += v.getInventory().getItem(i).getCount();
        return n;
    }

    /** One worker of {@code owner}'s at a new post of the job at (12, 12), with a bed. */
    private record Shop(ServerPlayer owner, WorkPostBlockEntity post, Villager worker) {}
    private static Shop shop(GameTestHelper h, String job, VillagerProfession profession, String name) {
        Yard.floor(h);
        var owner = Yard.player(h, 40, 40, name);
        var bed = Yard.bed(h, 20, 20);
        var post = Yard.post(h, 12, 12, owner, job, 8);
        var worker = Yard.worker(h, 12, 15, profession, owner, bed, post);
        return new Shop(owner, post, worker);
    }

    // ---- the blacksmith ----------------------------------------------------------------------

    /** Two iron pickaxes from six raw iron, two coal and two planks: the raw iron goes into the blast
     * furnace, not the furnace beside it, with one coal; the planks become four sticks; two
     * pickaxes end in the chest, and no third is made. The list shows the row stocked. */
    @GameTest(template = "yard", timeoutTicks = 3600, batch = "smithy") public void aBlacksmithSmeltsRawIronAndMakesTwoPickaxes(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.TOOLSMITH, "smith");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.RAW_IRON, 6), new ItemStack(Items.COAL, 2), new ItemStack(Items.OAK_PLANKS, 2));
        var blast = Yard.block(h, 14, 10, Blocks.BLAST_FURNACE);
        var plain = Yard.block(h, 15, 10, Blocks.FURNACE);
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        keep(s.post(), "minecraft:iron_pickaxe", 2);
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(furnace(h, blast).getItem(0).isEmpty(), "the raw iron is in the blast furnace"))
                .thenExecute(() -> {
                    h.assertTrue(furnace(h, blast).getItem(0).getCount() == 6, "all six at once: " + furnace(h, blast).getItem(0));
                    h.assertTrue(furnace(h, blast).getItem(1).getCount() <= 1, "one coal for six, in the slot or already burning: " + furnace(h, blast).getItem(1));
                    h.assertTrue(empty(h, plain), "the furnace beside it is not used");
                })
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_PICKAXE) == 2, "two pickaxes: " + Yard.count(h, chest, Items.IRON_PICKAXE)))
                .thenIdle(300)
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chest, Items.IRON_PICKAXE) == 2 && carried(s.worker(), Items.IRON_PICKAXE) == 0, "no third");
                    h.assertTrue(Yard.count(h, chest, Items.RAW_IRON) == 0 && Yard.count(h, chest, Items.IRON_INGOT) == 0, "the six went into the two");
                    h.assertTrue(Yard.count(h, chest, Items.COAL) == 1, "one coal is left: " + Yard.count(h, chest, Items.COAL));
                    h.assertTrue(Yard.count(h, chest, Items.STICK) == 0 && Yard.count(h, chest, Items.OAK_PLANKS) == 0, "two planks, four sticks, all used");
                    h.assertTrue(empty(h, blast) && empty(h, plain), "the furnaces are empty");
                    var view = Screens.postView(h.getLevel(), s.post());
                    h.assertTrue(view.rows().size() == 1 && view.rows().get(0).status() == Workshop.Status.MET.ordinal() && view.rows().get(0).have() == 2, "the list shows it stocked: " + view.rows());
                }).thenSucceed();
    }

    /** A netherite sword from four ancient debris, four gold, a diamond sword with Sharpness and a
     * template: scrap from the blast furnace, the ingot at the table, the upgrade at the smithing
     * table; the sword keeps its Sharpness. */
    @GameTest(template = "yard", timeoutTicks = 3600, batch = "smithy") public void aNetheriteSwordIsMadeFourStationsDeep(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.WEAPONSMITH, "netherite");
        var sharp = h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharp, 3);
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.ANCIENT_DEBRIS, 4), new ItemStack(Items.GOLD_INGOT, 4), sword,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.COAL));
        Yard.block(h, 14, 10, Blocks.BLAST_FURNACE);
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        Yard.block(h, 14, 14, Blocks.SMITHING_TABLE);
        keep(s.post(), "minecraft:netherite_sword", 1);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.NETHERITE_SWORD) == 1, "the netherite sword is made"))
                .thenExecute(() -> {
                    var made = ((Container) h.getLevel().getBlockEntity(chest));
                    ItemStack found = ItemStack.EMPTY;
                    for (int i = 0; i < made.getContainerSize(); i++) if (made.getItem(i).is(Items.NETHERITE_SWORD)) found = made.getItem(i);
                    h.assertTrue(found.getEnchantments().getLevel(sharp) == 3, "it keeps Sharpness III: " + found.getEnchantments());
                    for (var gone : List.of(Items.DIAMOND_SWORD, Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, Items.ANCIENT_DEBRIS, Items.GOLD_INGOT, Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT))
                        h.assertTrue(Yard.count(h, chest, gone) == 0, "spent: " + gone);
                }).thenSucceed();
    }

    /** Five raw copper are smelted with nothing on the list, while an iron sword and an iron
     * chestplate in the same chest stay whole. Then one iron nugget is kept: vanilla's
     * nugget-from-blasting takes iron tools and armour and, making one a craft, comes before the
     * table's nine from an ingot, so a sword would cover it; the nuggets come from the ingot and
     * the gear is never melted. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "smithy") public void rawCopperIsSmeltedAndGearIsNeverMelted(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.ARMORER, "copper");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.RAW_COPPER, 5), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.COAL));
        Yard.block(h, 14, 10, Blocks.BLAST_FURNACE);
        Yard.block(h, 15, 10, Blocks.FURNACE);
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.COPPER_INGOT) == 5, "five copper ingots: " + Yard.count(h, chest, Items.COPPER_INGOT)))
                .thenExecute(() -> {
                    ((Container) h.getLevel().getBlockEntity(chest)).setItem(10, new ItemStack(Items.IRON_INGOT));
                    keep(s.post(), "minecraft:iron_nugget", 1);
                })
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_NUGGET) == 9, "nine nuggets: " + Yard.count(h, chest, Items.IRON_NUGGET)))
                .thenIdle(300)
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chest, Items.IRON_SWORD) == 1 && Yard.count(h, chest, Items.IRON_CHESTPLATE) == 1, "the gear stays whole");
                    h.assertTrue(Yard.count(h, chest, Items.IRON_NUGGET) == 9 && Yard.count(h, chest, Items.IRON_INGOT) == 0, "the nuggets came from the ingot");
                }).thenSucceed();
    }

    /** An iron axe 150 worn of 250, five iron ingots, an anvil: the axe is mended by two ingots, two
     * whole quarters, to 26, and three ingots are left. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "smithy") public void aWornAxeIsMendedInWholeQuarters(GameTestHelper h) {
        shop(h, "blacksmith", VillagerProfession.TOOLSMITH, "mender");
        var axe = new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(150);
        var chest = Yard.chest(h, 10, 10, axe, new ItemStack(Items.IRON_INGOT, 5));
        var anvil = Yard.block(h, 14, 10, Blocks.ANVIL);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 3, "two ingots spent: " + Yard.count(h, chest, Items.IRON_INGOT)))
                .thenWaitUntil(() -> {
                    var c = (Container) h.getLevel().getBlockEntity(chest);
                    int damage = -1;
                    for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(Items.IRON_AXE)) damage = c.getItem(i).getDamageValue();
                    h.assertTrue(damage == 26, "mended to 26: " + damage);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 3, "nothing more spent on what is left");
                    var state = h.getLevel().getBlockState(anvil);
                    h.assertTrue(state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL), "the anvil stands, maybe chipped: " + state);
                }).thenSucceed();
    }

    /** No coal, sixteen logs, two raw iron and two furnaces: charcoal is made from the logs first,
     * then the raw iron is smelted with it. */
    @GameTest(template = "yard", timeoutTicks = 4000, batch = "smithy") public void charcoalIsMadeWhenThereIsNoCoal(GameTestHelper h) {
        shop(h, "blacksmith", VillagerProfession.TOOLSMITH, "collier");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.OAK_LOG, 16), new ItemStack(Items.RAW_IRON, 2));
        Yard.block(h, 14, 10, Blocks.FURNACE);
        Yard.block(h, 15, 10, Blocks.FURNACE);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 2, "the iron is smelted: " + Yard.count(h, chest, Items.IRON_INGOT)))
                .thenExecute(() -> h.assertTrue(Yard.count(h, chest, Items.OAK_LOG) < 16, "logs were burnt and charred: " + Yard.count(h, chest, Items.OAK_LOG)))
                .thenSucceed();
    }

    /** A player's furnace smelting cobblestone and another holding a player's finished stone stand
     * beside an empty one: the raw iron goes into the empty one, and the player's cobblestone and
     * stone in both are never touched. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "smithy") public void aPlayersFurnaceIsLeftAlone(GameTestHelper h) {
        shop(h, "blacksmith", VillagerProfession.TOOLSMITH, "neighbour");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.RAW_IRON, 3), new ItemStack(Items.COAL));
        var theirs = Yard.block(h, 14, 10, Blocks.FURNACE);
        furnace(h, theirs).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        furnace(h, theirs).setItem(1, new ItemStack(Items.COAL, 2));
        var leftover = Yard.block(h, 16, 10, Blocks.FURNACE);
        furnace(h, leftover).setItem(2, new ItemStack(Items.STONE, 5));
        Yard.block(h, 15, 10, Blocks.FURNACE);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 3, "the iron went into the other furnace: " + Yard.count(h, chest, Items.IRON_INGOT)))
                .thenIdle(200)
                .thenExecute(() -> {
                    var f = furnace(h, theirs);
                    int cobble = f.getItem(0).is(Items.COBBLESTONE) ? f.getItem(0).getCount() : 0;
                    int stone = f.getItem(2).is(Items.STONE) ? f.getItem(2).getCount() : 0;
                    h.assertTrue(cobble + stone == 10, "the player's ten are all there: " + cobble + " + " + stone);
                    h.assertTrue(furnace(h, leftover).getItem(2).is(Items.STONE) && furnace(h, leftover).getItem(2).getCount() == 5, "the player's finished stone stays: " + furnace(h, leftover).getItem(2));
                    h.assertTrue(Yard.count(h, chest, Items.STONE) == 0 && Yard.count(h, chest, Items.COBBLESTONE) == 0, "none taken to the chest");
                }).thenSucceed();
    }

    /** Two smiths on one post keeping one iron sword: one sword is made, not two. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "smithy") public void twoSmithsMakeOneSword(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.WEAPONSMITH, "twosmiths");
        var bed2 = Yard.bed(h, 24, 20);
        var second = Yard.worker(h, 13, 15, VillagerProfession.ARMORER, s.owner(), bed2, s.post());
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.IRON_INGOT, 10), new ItemStack(Items.STICK, 4));
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        Yard.block(h, 14, 14, Blocks.CRAFTING_TABLE);
        keep(s.post(), "minecraft:iron_sword", 1);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_SWORD) >= 1, "a sword"))
                .thenIdle(400)
                .thenExecute(() -> {
                    int swords = Yard.count(h, chest, Items.IRON_SWORD) + carried(s.worker(), Items.IRON_SWORD) + carried(second, Items.IRON_SWORD);
                    h.assertTrue(swords == 1, "one sword between them: " + swords);
                    h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 8, "two ingots spent: " + Yard.count(h, chest, Items.IRON_INGOT));
                }).thenSucceed();
    }

    /** A rifle kept, from twelve iron, three coal, a redstone, three planks and a stick: steel at the
     * table, then the receivers, the barrel, the stock and the rifle at the weapons workbench. */
    @GameTest(template = "yard", timeoutTicks = 4000, batch = "smithy") public void aRifleIsMadeAtTheWeaponsWorkbench(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.WEAPONSMITH, "gunsmith");
        var bench = block("rangedweaponsmod:weapons_workbench");
        h.assertTrue(bench != Blocks.AIR, "Ranged Weapons Mod's workbench is registered");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.IRON_INGOT, 12), new ItemStack(Items.COAL, 3), new ItemStack(Items.REDSTONE),
                new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.STICK));
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        Yard.block(h, 14, 14, bench);
        keep(s.post(), "rangedweaponsmod:rifle", 1);
        var rifle = item("rangedweaponsmod:rifle");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, rifle) == 1, "the rifle is made"))
                .thenExecute(() -> {
                    for (var part : List.of("rangedweaponsmod:upper_receiver", "rangedweaponsmod:lower_receiver", "rangedweaponsmod:barrel", "rangedweaponsmod:stock"))
                        h.assertTrue(Yard.count(h, chest, item(part)) == 0, "the part went into it: " + part);
                    h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 0 && Yard.count(h, chest, Items.REDSTONE) == 0, "the iron and redstone are spent");
                }).thenSucceed();
    }

    /** A smith keeping an iron ingot, with raw iron and a furnace but no fuel and no logs, shows "no
     * fuel"; coal put in the chest is fetched and the ingot is made. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "smithy") public void noFuelShowsUntilCoalIsStored(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.TOOLSMITH, "fuelless");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.RAW_IRON));
        Yard.block(h, 14, 10, Blocks.FURNACE);
        keep(s.post(), "minecraft:iron_ingot", 1);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Workers.shownNeed(s.worker()).equals(Optional.of(Need.NO_FUEL)), "no fuel: " + Workers.shownNeed(s.worker())))
                .thenExecute(() -> {
                    var view = Screens.postView(h.getLevel(), s.post());
                    h.assertTrue(view.rows().get(0).status() == Workshop.Status.NO_FUEL.ordinal(), "the list says why: " + view.rows());
                    ((Container) h.getLevel().getBlockEntity(chest)).setItem(5, new ItemStack(Items.COAL));
                })
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_INGOT) == 1, "the ingot is made"))
                .thenExecute(() -> h.assertTrue(Workers.shownNeed(s.worker()).isEmpty(), "the need clears: " + Workers.shownNeed(s.worker())))
                .thenSucceed();
    }

    /** The rows that cost a planner most, over the whole rule book with the chests nearly empty: a
     * beacon, a rifle, a netherite sword, a machine gun, a cake, a jukebox, a piston and a lantern,
     * each searched to the full depth twice (the usable stations, then all of them) and each short.
     * Working out every row stays under 50 ms (a worker asks every few seconds at most). */
    @GameTest(template = "yard", timeoutTicks = 200, batch = "smithy") public void theHardestRowsPlanQuickly(GameTestHelper h) {
        var s = shop(h, "blacksmith", VillagerProfession.TOOLSMITH, "planner");
        Yard.chest(h, 10, 10, new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.OAK_LOG, 3), new ItemStack(Items.COAL, 1), new ItemStack(Items.REDSTONE, 2));
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        Yard.block(h, 14, 10, Blocks.FURNACE);
        var rows = new java.util.ArrayList<Stock.Row>();
        for (var id : List.of("minecraft:beacon", "rangedweaponsmod:rifle", "minecraft:netherite_sword", "rangedweaponsmod:machine_gun", "minecraft:cake",
                "minecraft:jukebox", "minecraft:piston", "minecraft:lantern")) rows.add(new Stock.Row(id, 4));
        s.post().setStock(new Stock(rows));
        var job = com.chunkworks.serfdom.job.Jobs.get(s.post().job()).orElseThrow();
        h.startSequence().thenExecute(() -> {
            var read = com.chunkworks.serfdom.job.WorkshopJob.read(h.getLevel(), s.post(), job, Optional.empty(), p -> false);
            Workshop.rows(read.facts());
            long worst = 0;
            for (int i = 0; i < 20; i++) {
                long t0 = System.nanoTime();
                var states = Workshop.rows(read.facts());
                worst = Math.max(worst, System.nanoTime() - t0);
                h.assertTrue(states.stream().noneMatch(st -> st.status() == Workshop.Status.MET), "every row is short: " + states);
            }
            org.slf4j.LoggerFactory.getLogger("Serfdom").info("Serfdom: the eight hardest rows planned in {} us at worst over {} rules", worst / 1000, read.facts().rules().size());
            h.assertTrue(worst < 50_000_000L, "under 50 ms: " + worst / 1000 + " us");
        }).thenSucceed();
    }

    // ---- the cook ----------------------------------------------------------------------------

    /** Two bread from six wheat at the crafting table. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "kitchen") public void breadIsBakedAtTheTable(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "baker");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.WHEAT, 6));
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        keep(s.post(), "minecraft:bread", 2);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.BREAD) == 2, "two bread: " + Yard.count(h, chest, Items.BREAD)))
                .thenExecute(() -> h.assertTrue(Yard.count(h, chest, Items.WHEAT) == 0, "the wheat is spent"))
                .thenSucceed();
    }

    /** Twelve raw beef, a smoker and a furnace, eight steaks kept: the beef goes into the smoker, eight
     * steaks are made, and four beef stay raw. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "kitchen") public void steaksAreSmokedToTheNumberKept(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "griller");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.BEEF, 12), new ItemStack(Items.COAL, 2));
        var smoker = Yard.block(h, 14, 10, Blocks.SMOKER);
        var plain = Yard.block(h, 15, 10, Blocks.FURNACE);
        keep(s.post(), "minecraft:cooked_beef", 8);
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(furnace(h, smoker).getItem(0).isEmpty(), "the beef is in the smoker"))
                .thenExecute(() -> h.assertTrue(empty(h, plain), "not the furnace"))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.COOKED_BEEF) == 8, "eight steaks: " + Yard.count(h, chest, Items.COOKED_BEEF)))
                .thenIdle(400)
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chest, Items.COOKED_BEEF) == 8, "no more");
                    h.assertTrue(Yard.count(h, chest, Items.BEEF) == 4, "four beef stay raw: " + Yard.count(h, chest, Items.BEEF));
                }).thenSucceed();
    }

    /** Four raw beef and a lit campfire, nothing else to cook on: the four go onto the campfire
     * (no fuel needed), and the steaks it pops out are picked up and stored. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "kitchen") public void steaksOffACampfireArePickedUp(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "camper");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.BEEF, 4));
        var fire = Yard.block(h, 14, 10, Blocks.CAMPFIRE);
        keep(s.post(), "minecraft:cooked_beef", 4);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((net.minecraft.world.level.block.entity.CampfireBlockEntity) h.getLevel().getBlockEntity(fire)).getItems().stream().filter(i -> !i.isEmpty()).count() == 4, "four on the fire"))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.COOKED_BEEF) == 4, "four steaks stored: " + Yard.count(h, chest, Items.COOKED_BEEF)))
                .thenExecute(() -> h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(fire).inflate(4)).isEmpty(), "nothing left on the ground"))
                .thenSucceed();
    }

    /** Two raw chicken and a lit Farmer's Delight stove: both cook on the stove and are picked up. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "kitchen") public void aStovesFoodIsPickedUp(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "stover");
        var stove = block("farmersdelight:stove");
        h.assertTrue(stove != Blocks.AIR, "Farmer's Delight's stove is registered");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.CHICKEN, 2));
        var at = Yard.at(h, 14, 1, 10);
        h.getLevel().setBlock(at, stove.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), 3);
        keep(s.post(), "minecraft:cooked_chicken", 2);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.COOKED_CHICKEN) == 2, "two cooked chicken stored: " + Yard.count(h, chest, Items.COOKED_CHICKEN)))
                .thenSucceed();
    }

    /** A cooking pot on a lit campfire, beef, carrot, potato and a bowl: a beef stew. Then the fire
     * is put out and a second stew asked for: "no station". */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "kitchen") public void beefStewInAHeatedPotAndNoneCold(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "stewer");
        var pot = block("farmersdelight:cooking_pot");
        h.assertTrue(pot != Blocks.AIR, "Farmer's Delight's pot is registered");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.BEEF, 2), new ItemStack(Items.CARROT, 2), new ItemStack(Items.POTATO, 2), new ItemStack(Items.BOWL, 2));
        var fire = Yard.block(h, 14, 10, Blocks.CAMPFIRE);
        h.getLevel().setBlock(fire.above(), pot.defaultBlockState(), 3);
        keep(s.post(), "farmersdelight:beef_stew", 1);
        var stew = item("farmersdelight:beef_stew");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, stew) == 1, "a stew: " + Yard.count(h, chest, stew)))
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chest, Items.BOWL) == 1, "one bowl spent");
                    h.getLevel().setBlock(fire, h.getLevel().getBlockState(fire).setValue(CampfireBlock.LIT, false), 3);
                    s.post().setStock(new Stock(List.of(new Stock.Row("farmersdelight:beef_stew", 2))));
                })
                .thenWaitUntil(() -> h.assertTrue(Workers.shownNeed(s.worker()).equals(Optional.of(Need.NO_STATION)), "no station: " + Workers.shownNeed(s.worker())))
                .thenExecute(() -> {
                    var view = Screens.postView(h.getLevel(), s.post());
                    h.assertTrue(view.rows().get(0).status() == Workshop.Status.NO_STATION.ordinal(), "the list says why: " + view.rows());
                    h.assertTrue(Yard.count(h, chest, stew) == 1, "no second stew from a cold pot");
                }).thenSucceed();
    }

    /** Two beef and a flint knife in the chest, a cutting board, four minced beef kept: the knife is
     * fetched into the cook's hand and both beef are cut, two minced each. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "kitchen") public void mincedBeefIsCutOnTheBoardWithAKnife(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "cutter");
        var knife = item("farmersdelight:flint_knife");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.BEEF, 2), new ItemStack(knife));
        Yard.block(h, 14, 10, block("farmersdelight:cutting_board"));
        keep(s.post(), "farmersdelight:minced_beef", 4);
        var minced = item("farmersdelight:minced_beef");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, minced) == 4, "four minced beef: " + Yard.count(h, chest, minced)))
                .thenExecute(() -> {
                    var hand = s.worker().getItemBySlot(EquipmentSlot.MAINHAND);
                    h.assertTrue(hand.is(knife) && hand.getDamageValue() >= 2, "the knife in hand, worn by two cuts: " + hand);
                    h.assertTrue(Yard.count(h, chest, Items.BEEF) == 0, "both beef cut");
                }).thenSucceed();
    }

    /** A cake from three milk, two sugar, an egg and three wheat: the three buckets come back. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "kitchen") public void aCakeGivesItsBucketsBack(GameTestHelper h) {
        var s = shop(h, "cooking", VillagerProfession.BUTCHER, "confectioner");
        var chest = Yard.chest(h, 10, 10, new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR, 2), new ItemStack(Items.EGG), new ItemStack(Items.WHEAT, 3));
        Yard.block(h, 10, 14, Blocks.CRAFTING_TABLE);
        keep(s.post(), "minecraft:cake", 1);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.CAKE) == 1, "a cake"))
                .thenExecute(() -> h.assertTrue(Yard.count(h, chest, Items.BUCKET) == 3 && Yard.count(h, chest, Items.MILK_BUCKET) == 0, "three buckets back: " + Yard.count(h, chest, Items.BUCKET)))
                .thenSucceed();
    }
}
