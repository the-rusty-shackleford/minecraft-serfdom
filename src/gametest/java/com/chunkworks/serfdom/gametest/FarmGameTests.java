/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.domain.Farm;
import com.chunkworks.serfdom.domain.WorkDay;
import com.chunkworks.serfdom.job.Holding;
import com.chunkworks.serfdom.job.Job;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of shared work (D-0008):
 * <ul>
 * <li>one post: two farmers at one field, two woodcutters at two trees;</li>
 * <li>a farm of posts: touching (a farmer helps where no farmer is), a block apart, another owner's
 * touching; its own area first; the harvest into the nearest post's chests;</li>
 * <li>filling in: a holder killed, a holder's shift ended, a holder frozen past its lapse and back;</li>
 * <li>sowing: a gap in a row after the wait (not before), with carried seed; with seed from the farm's
 * chest; two kinds near, only stems near; a spot that grew a carrot among wheat.</li>
 * </ul>
 * Crops don't grow on the GameTest server (no random ticks), so ages set stay set. */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class FarmGameTests {
    /** The batches a test that moves the clock stands alone in: the hour is the level's, and a shift
     * ended for one test would end every farmer's. */
    @BeforeBatch(batch = "farm") public static void farm(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }
    @BeforeBatch(batch = "farmDeposit") public static void farmDeposit(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }
    @BeforeBatch(batch = "farmNearest") public static void farmNearest(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }
    @BeforeBatch(batch = "farmEnd") public static void farmEnd(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }

    /** effects: farmland at the yard position, with {@code crop} at {@code age} on it, or bare when
     * {@code crop} is null; returns the spot above the farmland. */
    private static BlockPos plant(GameTestHelper h, int x, int z, Block crop, int age) {
        h.getLevel().setBlock(Yard.at(h, x, 0, z), Blocks.FARMLAND.defaultBlockState(), 3);
        var spot = Yard.at(h, x, 1, z);
        if (crop instanceof CropBlock c) h.getLevel().setBlock(spot, c.getStateForAge(age), 3);
        else if (crop != null) h.getLevel().setBlock(spot, crop.defaultBlockState().setValue(StemBlock.AGE, age), 3);
        return spot;
    }

    private static boolean ripe(GameTestHelper h, BlockPos spot) {
        var s = h.getLevel().getBlockState(spot);
        return s.getBlock() instanceof CropBlock c && c.isMaxAge(s);
    }

    private static boolean taken(GameTestHelper h, BlockPos spot) {
        var s = h.getLevel().getBlockState(spot);
        return s.getBlock() instanceof CropBlock c && c.getAge(s) == 0;
    }

    /** effects: the plot among those of {@code spots} that {@code worker} holds now, if any. */
    private static Optional<Farm.Plot> heldBy(GameTestHelper h, Villager worker, List<BlockPos> spots) {
        for (var spot : spots) {
            var plot = Farm.Plot.of(spot.getX(), spot.getZ());
            if (Holding.holder(h.getLevel(), Job.Place.plot(plot)).filter(worker.getUUID()::equals).isPresent()) return Optional.of(plot);
        }
        return Optional.empty();
    }

    private static Optional<UUID> holder(GameTestHelper h, Farm.Plot plot) { return Holding.holder(h.getLevel(), Job.Place.plot(plot)); }

    /** effects: where the worker is walking now, if anywhere. */
    private static java.util.Optional<BlockPos> walkingTo(Villager v) {
        return v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getTarget().currentBlockPosition());
    }

    /** Two farmers on one post, a 16x8 field of ripe wheat: once either holds a plot the other holds
     * another within 60 ticks, neither ever walks to a crop the other has already taken, and every
     * crop is harvested and replanted. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "farm") public void twoFarmersOnOnePostNeverChaseOneCrop(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "two-farmers");
        var level = h.getLevel();
        var field = new ArrayList<BlockPos>();
        for (int x = 8; x < 24; x++) for (int z = 4; z < 12; z++) {
            level.setBlock(Yard.at(h, x, 0, z), Blocks.FARMLAND.defaultBlockState(), 3);
            var crop = Yard.at(h, x, 1, z);
            level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
            field.add(crop);
        }
        Yard.chest(h, 15, 16, new ItemStack(Items.IRON_HOE), new ItemStack(Items.IRON_HOE));
        Yard.chest(h, 17, 16);
        var post = Yard.post(h, 16, 14, owner, "farming", 12);
        var a = Yard.worker(h, 16, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 30, 18), post);
        var b = Yard.worker(h, 17, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 32, 18), post);
        int[] chased = {0};
        long[] first = {-1}, both = {-1};
        h.onEachTick(() -> {
            for (var v : List.of(a, b)) walkingTo(v).ifPresent(p -> {
                var s = level.getBlockState(p);
                if (s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) < 7) chased[0]++;
            });
            boolean aHolds = heldBy(h, a, field).isPresent(), bHolds = heldBy(h, b, field).isPresent();
            if (first[0] < 0 && (aHolds || bHolds)) first[0] = h.getTick();
            if (both[0] < 0 && aHolds && bHolds) both[0] = h.getTick();
        });
        h.startSequence().thenWaitUntil(() -> {
            for (var c : field) {
                var s = level.getBlockState(c);
                h.assertTrue(s.is(Blocks.WHEAT) && s.getValue(CropBlock.AGE) == 0, "harvested and replanted: " + c + " " + s);
            }
        }).thenExecute(() -> {
            h.assertTrue(chased[0] == 0, "a farmer walked toward wheat the other had taken, for " + chased[0] + " ticks");
            h.assertTrue(both[0] >= 0 && both[0] - first[0] <= 60, "both at work at once: the first took a plot at " + first[0] + ", both held plots at " + both[0]);
        }).thenSucceed();
    }

    /** Two woodcutters on one post and two oaks, both nearer the one: once either holds a tree the other
     * holds the other tree within 60 ticks, and each fells a different oak. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "farm") public void twoWoodcuttersFellTwoTrees(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "two-woodcutters");
        var near = Yard.oak(h, 5, 6);
        var far = Yard.oak(h, 21, 6);
        Yard.chest(h, 12, 16, new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_AXE));
        Yard.chest(h, 14, 16);
        var post = Yard.post(h, 13, 14, owner, "woodcutting", 14);
        var a = Yard.worker(h, 11, 16, VillagerProfession.FLETCHER, owner, Yard.bed(h, 30, 20), post);
        var b = Yard.worker(h, 12, 17, VillagerProfession.FLETCHER, owner, Yard.bed(h, 32, 20), post);
        a.getInventory().addItem(new ItemStack(Items.OAK_SAPLING));
        b.getInventory().addItem(new ItemStack(Items.OAK_SAPLING));
        Set<BlockPos> roots = Set.of(near, far);
        Set<BlockPos> byA = new HashSet<>(), byB = new HashSet<>();
        long[] firstHeld = {-1}, bothHeld = {-1};
        h.onEachTick(() -> {
            walkingTo(a).filter(roots::contains).ifPresent(byA::add);
            walkingTo(b).filter(roots::contains).ifPresent(byB::add);
            int held = 0;
            for (var root : roots) if (Holding.holder(h.getLevel(), Job.Place.tree(root)).isPresent()) held++;
            if (firstHeld[0] < 0 && held >= 1) firstHeld[0] = h.getTick();
            if (bothHeld[0] < 0 && held == 2) bothHeld[0] = h.getTick();
        });
        h.startSequence().thenWaitUntil(() -> {
            for (var root : roots) h.assertFalse(h.getLevel().getBlockState(root).is(BlockTags.LOGS), "felled: " + root);
        }).thenExecute(() -> {
            var both = new HashSet<>(byA);
            both.retainAll(byB);
            h.assertTrue(both.isEmpty(), "both woodcutters went for the same tree: " + both + " (one: " + byA + ", the other: " + byB + ")");
            h.assertTrue(bothHeld[0] >= 0 && bothHeld[0] - firstHeld[0] <= 60, "both at work at once: the first took a tree at " + firstHeld[0] + ", both held trees at " + bothHeld[0]);
        }).thenSucceed();
    }

    /** Two posts whose areas touch, a farmer on the first only, ripe wheat only in the second's area
     * and chests only at the first: the farmer harvests it all and the wheat goes into the first's
     * chest. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "farmDeposit") public void aFarmerHelpsInATouchingPostsArea(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "touching");
        var field = new ArrayList<BlockPos>();
        for (int x = 16; x <= 22; x++) for (int z = 10; z <= 14; z++) field.add(plant(h, x, z, Blocks.WHEAT, 7));
        var chest = Yard.chest(h, 8, 16, new ItemStack(Items.IRON_HOE));
        var a = Yard.post(h, 8, 12, owner, "farming", 5);
        Yard.post(h, 19, 15, owner, "farming", 5);
        var farmer = Yard.worker(h, 8, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 4, 20), a);
        h.startSequence().thenWaitUntil(() -> {
            for (var c : field) h.assertTrue(taken(h, c), "harvested and replanted: " + c);
        }).thenExecute(() -> h.getLevel().setDayTime(WorkDay.WORK_END - 500))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.WHEAT) == field.size(), "the wheat is in the first post's chest: " + Yard.count(h, chest, Items.WHEAT)))
                .thenExecute(() -> h.assertTrue(farmer.getInventory().isEmpty(), "everything put away"))
                .thenSucceed();
    }

    /** A post one block too far from the farmer's, and another owner's post touching it: their ripe
     * wheat stands while the farmer, its own wheat taken, waits by its post. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "farm") public void aGapOrAnotherOwnerKeepsFarmsApart(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "apart");
        var stranger = Yard.player(h, 44, 40, "stranger");
        var own = plant(h, 6, 6, Blocks.WHEAT, 7);
        var apart = new ArrayList<BlockPos>();
        for (int x = 18; x <= 22; x++) apart.add(plant(h, x, 8, Blocks.WHEAT, 7));
        var strangers = new ArrayList<BlockPos>();
        for (int x = 6; x <= 10; x++) strangers.add(plant(h, x, 20, Blocks.WHEAT, 7));
        Yard.chest(h, 8, 11, new ItemStack(Items.IRON_HOE));
        var a = Yard.post(h, 8, 8, owner, "farming", 5);
        Yard.post(h, 20, 11, owner, "farming", 5);
        Yard.post(h, 8, 19, stranger, "farming", 5);
        Yard.worker(h, 9, 12, VillagerProfession.FARMER, owner, Yard.bed(h, 2, 12), a);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(taken(h, own), "its own wheat is taken"))
                .thenIdle(600)
                .thenExecute(() -> {
                    for (var c : apart) h.assertTrue(ripe(h, c), "a block too far: left alone " + c);
                    for (var c : strangers) h.assertTrue(ripe(h, c), "another owner's: left alone " + c);
                }).thenSucceed();
    }

    /** Farmers on two touching posts, ripe wheat in the second's area. The second's farmer is killed
     * while it holds a plot: the plot is free at once, and the first's farmer finishes the field. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "farm") public void aFarmerFillsInForOneKilled(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "fill-in");
        var field = new ArrayList<BlockPos>();
        for (int x = 16; x <= 24; x++) for (int z = 11; z <= 16; z++) field.add(plant(h, x, z, Blocks.WHEAT, 7));
        Yard.chest(h, 8, 16, new ItemStack(Items.IRON_HOE), new ItemStack(Items.IRON_HOE));
        Yard.chest(h, 9, 16);
        var a = Yard.post(h, 8, 12, owner, "farming", 6);
        var b = Yard.post(h, 20, 17, owner, "farming", 6);
        var first = Yard.worker(h, 6, 20, VillagerProfession.FARMER, owner, Yard.bed(h, 2, 22), a);
        var second = Yard.worker(h, 10, 20, VillagerProfession.FARMER, owner, Yard.bed(h, 4, 22), b);
        Farm.Plot[] held = new Farm.Plot[1];
        h.startSequence().thenWaitUntil(() -> {
            var plot = heldBy(h, second, field);
            h.assertTrue(plot.isPresent(), "the second farmer holds a plot");
            long left = field.stream().filter(c -> Farm.Plot.of(c.getX(), c.getZ()).equals(plot.get()) && ripe(h, c)).count();
            h.assertTrue(left >= 3, "its plot has work left: " + left);
            held[0] = plot.get();
        }).thenExecute(second::kill)
                .thenExecuteAfter(2, () -> h.assertFalse(holder(h, held[0]).filter(second.getUUID()::equals).isPresent(), "the dead farmer's plot is free at once"))
                .thenWaitUntil(() -> {
                    for (var c : field) h.assertTrue(taken(h, c), "harvested: " + c);
                }).thenExecute(() -> h.assertTrue(first.isAlive(), "the first farmer did it"))
                .thenSucceed();
    }

    /** A farmer whose shift ends while it holds a plot lets go of it at once. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "farmEnd") public void aFarmerWhoseShiftEndsLetsGo(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "shift-end");
        var field = new ArrayList<BlockPos>();
        for (int x = 8; x <= 24; x++) for (int z = 4; z <= 10; z++) field.add(plant(h, x, z, Blocks.WHEAT, 7));
        Yard.chest(h, 15, 16, new ItemStack(Items.IRON_HOE));
        var post = Yard.post(h, 16, 14, owner, "farming", 12);
        var farmer = Yard.worker(h, 16, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 30, 18), post);
        Farm.Plot[] held = new Farm.Plot[1];
        h.startSequence().thenWaitUntil(() -> {
            var plot = heldBy(h, farmer, field);
            h.assertTrue(plot.isPresent() && field.stream().anyMatch(c -> taken(h, c)), "at work in a plot");
            held[0] = plot.get();
        }).thenExecute(() -> h.getLevel().setDayTime(WorkDay.WORK_END + 100))
                // Vanilla reads a villager's schedule at most every 20 ticks; the shift stops with it.
                .thenWaitUntil(() -> h.assertFalse(farmer.getBrain().isActive(com.chunkworks.serfdom.Serfdom.WORK.get()), "the shift is over"))
                .thenExecute(() -> h.assertTrue(holder(h, held[0]).isEmpty(), "let go as the shift ended, not at its lapse"))
                .thenSucceed();
    }
    /** Two farmers on one post. One is frozen while it holds a plot, long enough for its hold to lapse,
     * and the other takes the plot; unfrozen, the first leaves that plot to it. */
    @GameTest(template = "yard", timeoutTicks = 4000, batch = "farm") public void aFarmerWhoseHoldLapsedLeavesThePlot(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "lapsed");
        var level = h.getLevel();
        // Two whole plots of the world's grid: the yard's local coordinates from where its absolute ones
        // are a multiple of 8.
        var corner = Yard.at(h, 0, 0, 0);
        int ox = Math.floorMod(-corner.getX(), Farm.PLOT), oz = Math.floorMod(-corner.getZ(), Farm.PLOT);
        var field = new ArrayList<BlockPos>();
        for (int x = ox + 8; x < ox + 24; x++) for (int z = oz + 8; z < oz + 16; z++) field.add(plant(h, x, z, Blocks.WHEAT, 7));
        Yard.chest(h, ox + 15, oz + 22, new ItemStack(Items.IRON_HOE), new ItemStack(Items.IRON_HOE));
        Yard.chest(h, ox + 17, oz + 22);
        var post = Yard.post(h, ox + 16, oz + 20, owner, "farming", 12);
        var a = Yard.worker(h, ox + 16, oz + 24, VillagerProfession.FARMER, owner, Yard.bed(h, ox + 4, oz + 26), post);
        var b = Yard.worker(h, ox + 17, oz + 24, VillagerProfession.FARMER, owner, Yard.bed(h, ox + 8, oz + 26), post);
        Farm.Plot[] held = new Farm.Plot[1];
        int[] intrusions = {0};
        long[] thawed = {Long.MAX_VALUE};
        h.onEachTick(() -> {
            // A farmer walking to a crop in a plot the other holds. A frozen farmer keeps the walk it
            // had, and on its first ticks back it has not yet looked at its hold: neither is counted.
            for (var v : List.of(a, b)) walkingTo(v).ifPresent(p -> {
                if (v.isNoAi() || (v == a && h.getTick() < thawed[0] + 3)) return;
                var other = v == a ? b : a;
                if (holder(h, Farm.Plot.of(p.getX(), p.getZ())).filter(other.getUUID()::equals).isPresent() && level.getBlockState(p).getBlock() instanceof CropBlock) intrusions[0]++;
            });
        });
        h.startSequence().thenWaitUntil(() -> {
            var plot = heldBy(h, a, field);
            h.assertTrue(plot.isPresent(), "the first farmer holds a plot");
            long left = field.stream().filter(c -> Farm.Plot.of(c.getX(), c.getZ()).equals(plot.get()) && ripe(h, c)).count();
            h.assertTrue(left >= 8, "with work left: " + left);
            held[0] = plot.get();
        }).thenExecute(() -> a.setNoAi(true))
                .thenWaitUntil(() -> h.assertTrue(holder(h, held[0]).filter(b.getUUID()::equals).isPresent(), "the other farmer took the plot"))
                .thenExecute(() -> { a.setNoAi(false); thawed[0] = h.getTick(); })
                .thenWaitUntil(() -> {
                    for (var c : field) h.assertTrue(taken(h, c), "harvested: " + c);
                }).thenExecute(() -> h.assertTrue(intrusions[0] == 0, "a farmer walked into the other's plot for " + intrusions[0] + " ticks"))
                .thenSucceed();
    }

    /** Two touching posts, the farmer on the first standing in the second's area: it takes the ripe
     * wheat of its own post's area before the nearer wheat of the other's. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "farm") public void aFarmerWorksItsOwnAreaFirst(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "own-first");
        var mine = new ArrayList<BlockPos>();
        for (int x = 4; x <= 6; x++) for (int z = 10; z <= 14; z++) mine.add(plant(h, x, z, Blocks.WHEAT, 7));
        var theirs = new ArrayList<BlockPos>();
        for (int x = 20; x <= 22; x++) for (int z = 10; z <= 14; z++) theirs.add(plant(h, x, z, Blocks.WHEAT, 7));
        var a = Yard.post(h, 10, 12, owner, "farming", 6);
        Yard.post(h, 23, 12, owner, "farming", 6);
        Yard.chest(h, 22, 17, new ItemStack(Items.IRON_HOE));
        var farmer = Yard.worker(h, 20, 16, VillagerProfession.FARMER, owner, Yard.bed(h, 26, 20), a);
        int[] early = {0};
        h.onEachTick(() -> {
            if (mine.stream().anyMatch(c -> ripe(h, c)) && theirs.stream().anyMatch(c -> taken(h, c))) early[0]++;
        });
        h.startSequence().thenWaitUntil(() -> {
            for (var c : mine) h.assertTrue(taken(h, c), "its own: " + c);
            for (var c : theirs) h.assertTrue(taken(h, c), "the other's: " + c);
        }).thenExecute(() -> h.assertTrue(early[0] == 0, "the other post's wheat was taken before its own was done, for " + early[0] + " ticks"))
                .thenSucceed();
    }

    /** Chests at two touching posts: a farmer at work in the second's area when its shift winds down
     * puts the harvest in the second's chest, the nearer. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "farmNearest") public void theHarvestGoesToTheNearestPostsChest(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "nearest-chest");
        var field = new ArrayList<BlockPos>();
        for (int x = 18; x <= 24; x++) for (int z = 6; z <= 14; z++) field.add(plant(h, x, z, Blocks.WHEAT, 7));
        var near = Yard.chest(h, 6, 16, new ItemStack(Items.IRON_HOE));
        var far = Yard.chest(h, 21, 17);
        var a = Yard.post(h, 8, 12, owner, "farming", 6);
        Yard.post(h, 21, 12, owner, "farming", 6);
        Yard.worker(h, 8, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 2, 20), a);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(field.stream().filter(c -> taken(h, c)).count() >= 12, "a dozen taken"))
                .thenExecute(() -> h.getLevel().setDayTime(WorkDay.WORK_END - 500))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, far, Items.WHEAT) >= 12, "the wheat is in the second post's chest: " + Yard.count(h, far, Items.WHEAT)))
                .thenExecute(() -> h.assertTrue(Yard.count(h, near, Items.WHEAT) == 0, "none in the first post's chest"))
                .thenSucceed();
    }

    /** A row of young wheat with two gaps, the farmer carrying seeds: the gaps are still bare after
     * 1100 ticks, then sown with wheat, two seeds spent. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "farm") public void aGapInARowIsSownAfterTheWait(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "gap");
        var gaps = new ArrayList<BlockPos>();
        for (int x = 8; x <= 16; x++) {
            var spot = plant(h, x, 10, x == 10 || x == 13 ? null : Blocks.WHEAT, x == 10 || x == 13 ? 0 : 3);
            if (x == 10 || x == 13) gaps.add(spot);
        }
        Yard.chest(h, 12, 16, new ItemStack(Items.IRON_HOE));
        var post = Yard.post(h, 12, 14, owner, "farming", 8);
        var farmer = Yard.worker(h, 12, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 20, 20), post);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        h.startSequence().thenExecuteAfter(1100, () -> {
            for (var g : gaps) h.assertTrue(h.getLevel().getBlockState(g).isAir(), "not sown before the wait: " + g);
        }).thenWaitUntil(() -> {
            for (var g : gaps) h.assertTrue(h.getLevel().getBlockState(g).is(Blocks.WHEAT), "sown with wheat: " + g);
        }).thenExecute(() -> h.assertTrue(com.chunkworks.serfdom.job.Storage.count(farmer.getInventory(), Items.WHEAT_SEEDS) == 6, "two seeds spent"))
                .thenSucceed();
    }

    /** Young wheat with three gaps, the farmer carrying nothing: it takes three seeds from the chest
     * and sows the gaps. */
    @GameTest(template = "yard", timeoutTicks = 3000, batch = "farm") public void seedIsTakenFromTheFarmsChest(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "seed-chest");
        var gaps = new ArrayList<BlockPos>();
        for (int x = 8; x <= 16; x++) {
            boolean gap = x == 9 || x == 12 || x == 15;
            var spot = plant(h, x, 10, gap ? null : Blocks.WHEAT, gap ? 0 : 3);
            if (gap) gaps.add(spot);
        }
        var chest = Yard.chest(h, 12, 16, new ItemStack(Items.IRON_HOE), new ItemStack(Items.WHEAT_SEEDS, 16));
        var post = Yard.post(h, 12, 14, owner, "farming", 8);
        Yard.worker(h, 12, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 20, 20), post);
        h.startSequence().thenWaitUntil(() -> {
            for (var g : gaps) h.assertTrue(h.getLevel().getBlockState(g).is(Blocks.WHEAT), "sown with wheat: " + g);
        }).thenExecute(() -> h.assertTrue(Yard.count(h, chest, Items.WHEAT_SEEDS) == 13, "three taken from the chest: " + Yard.count(h, chest, Items.WHEAT_SEEDS)))
                .thenSucceed();
    }

    /** Bare spots where wheat and carrots meet, and among pumpkin stems only, stay bare, while a gap
     * in a row of wheat nearby is sown. */
    @GameTest(template = "yard", timeoutTicks = 3500, batch = "farm") public void noCopyWhereKindsMeetOrStemsGrow(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "meet");
        plant(h, 6, 6, Blocks.WHEAT, 3);
        var met = plant(h, 7, 6, null, 0);
        plant(h, 9, 6, Blocks.CARROTS, 3);
        plant(h, 6, 18, Blocks.PUMPKIN_STEM, 3);
        var stem = plant(h, 7, 18, null, 0);
        plant(h, 8, 18, Blocks.PUMPKIN_STEM, 3);
        BlockPos control = null;
        for (int x = 18; x <= 22; x++) { var spot = plant(h, x, 12, x == 20 ? null : Blocks.WHEAT, 3); if (x == 20) control = spot; }
        var gap = control;
        Yard.chest(h, 12, 16, new ItemStack(Items.IRON_HOE));
        var post = Yard.post(h, 12, 12, owner, "farming", 12);
        var farmer = Yard.worker(h, 12, 20, VillagerProfession.FARMER, owner, Yard.bed(h, 26, 24), post);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        farmer.getInventory().addItem(new ItemStack(Items.CARROT, 8));
        farmer.getInventory().addItem(new ItemStack(Items.PUMPKIN_SEEDS, 8));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(h.getLevel().getBlockState(gap).is(Blocks.WHEAT), "the control gap is sown"))
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(h.getLevel().getBlockState(met).isAir(), "where wheat and carrots meet: bare");
                    h.assertTrue(h.getLevel().getBlockState(stem).isAir(), "among stems: bare");
                }).thenSucceed();
    }

    /** A carrot in a field of young wheat, seen by the farmer, then broken: its spot is sown with a
     * carrot again, never wheat. */
    @GameTest(template = "yard", timeoutTicks = 3500, batch = "farm") public void aSpotThatGrewACarrotGetsACarrot(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 44, 44, "carrot");
        BlockPos carrot = null;
        for (int x = 8; x <= 12; x++) for (int z = 8; z <= 12; z++) {
            var spot = plant(h, x, z, x == 10 && z == 10 ? Blocks.CARROTS : Blocks.WHEAT, 3);
            if (x == 10 && z == 10) carrot = spot;
        }
        var spot = carrot;
        Yard.chest(h, 12, 16, new ItemStack(Items.IRON_HOE));
        var post = Yard.post(h, 12, 14, owner, "farming", 8);
        var farmer = Yard.worker(h, 12, 18, VillagerProfession.FARMER, owner, Yard.bed(h, 20, 20), post);
        farmer.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        farmer.getInventory().addItem(new ItemStack(Items.CARROT, 4));
        h.onEachTick(() -> h.assertFalse(h.getLevel().getBlockState(spot).is(Blocks.WHEAT), "wheat sown where a carrot grew"));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(post.field().remembered().containsValue("minecraft:carrot"), "the farm has seen the carrot"))
                .thenExecute(() -> h.getLevel().setBlock(spot, Blocks.AIR.defaultBlockState(), 3))
                .thenWaitUntil(() -> h.assertTrue(h.getLevel().getBlockState(spot).is(Blocks.CARROTS), "a carrot again"))
                .thenSucceed();
    }

    /** The cost of a field (D-0008): a radius-16 post's whole area of ripe wheat (33 by 33, 1089
     * crops) swept twenty times, and a farmer's look for work over it, each timed; the median and
     * the worst go to the log for the gate's record. A sweep must stay under 20 ms, which only a
     * gross regression would pass (the budget the gate checks is 1 ms a tick). */
    @GameTest(template = "yard", timeoutTicks = 400, batch = "farmCost") public void aFieldsSweepIsCheap(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 46, 46, "cost");
        var level = h.getLevel();
        for (int x = 8; x <= 40; x++) for (int z = 8; z <= 40; z++) {
            if (x == 24 && z == 24) continue;
            plant(h, x, z, Blocks.WHEAT, 7);
        }
        var post = Yard.post(h, 24, 24, owner, "farming", 16);
        Yard.chest(h, 2, 2, new ItemStack(Items.IRON_HOE));
        var farmer = Yard.worker(h, 4, 4, VillagerProfession.FARMER, owner, Yard.bed(h, 2, 6), null);
        h.startSequence().thenExecuteAfter(2, () -> {
            var sweeps = new long[20];
            int work = 0;
            for (int i = 0; i < sweeps.length; i++) {
                long t = System.nanoTime();
                work = post.field().sweepNow(level, post).values().stream().mapToInt(List::size).sum();
                sweeps[i] = System.nanoTime() - t;
            }
            java.util.Arrays.sort(sweeps);
            h.assertTrue(work == 33 * 33 - 1, "every crop is work: " + work);
            var finds = new long[20];
            for (int i = 0; i < finds.length; i++) {
                long t = System.nanoTime();
                var found = com.chunkworks.serfdom.job.Farming.INSTANCE.find(level, farmer, post, pos -> false);
                finds[i] = System.nanoTime() - t;
                h.assertTrue(found.task().isPresent(), "a plot to work");
            }
            java.util.Arrays.sort(finds);
            com.mojang.logging.LogUtils.getLogger().info("Serfdom cost: a radius-16 field of {} ripe crops: sweep median {} us, worst {} us; a look for work (swept) median {} us, worst {} us",
                    work, sweeps[10] / 1000, sweeps[19] / 1000, finds[10] / 1000, finds[19] / 1000);
            h.assertTrue(sweeps[10] < 20_000_000L, "a sweep takes " + sweeps[10] / 1000 + " us");
        }).thenSucceed();
    }
    @BeforeBatch(batch = "farmCost") public static void farmCost(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START - 1500); }
}

