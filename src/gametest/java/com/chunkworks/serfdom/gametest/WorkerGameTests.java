/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.serfdom.Picks;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.WorkerBrain;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.WorkDay;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 1a (D-0001), Village Deed, Farmer's Delight and Backpacks+
 * loaded:
 * <ul>
 * <li>hiring: who (a level-3 farmer, a nitwit, a child), how paid (the hand, a bag, short);</li>
 * <li>a novice hired away from its workstation, against a free one;</li>
 * <li>owned villagers: sneak-use by the owner and by another, a plain use, Village Deed's
 * listener;</li>
 * <li>beds: the one given, slept in, over a nearer one; picked by a click on its foot;</li>
 * <li>posts: no bed, too far, full, linked; broken;</li>
 * <li>work: a natural oak felled whole beside a bare pillar and a player's build, replanted and
 * stored; ripe crops, unripe ones, a pumpkin, sorted into the chests by kind;</li>
 * <li>needs: no tool until an axe is stored; chests full until one is emptied;</li>
 * <li>the chain: on a worker and a free villager, snapping past ten blocks; vanilla's lead;</li>
 * <li>trades restocked at the post; a worker saved and loaded.</li>
 * </ul>
 * Tests that need an hour of the day have a batch of their own, set and held before it. */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class WorkerGameTests {
    private static final int SETTLE = 5;

    /** The tests without an hour of their own run held in the morning's idle hours, not wherever the
     * batch before them left the clock. */
    @BeforeBatch(batch = "defaultBatch") public static void morning(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START - 1500); }
    @BeforeBatch(batch = "night") public static void night(ServerLevel level) { Yard.hour(level, WorkDay.SLEEP_START + 1000); }
    @BeforeBatch(batch = "woodcutting") public static void woodcutting(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }
    @BeforeBatch(batch = "farming") public static void farming(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }
    @BeforeBatch(batch = "needs") public static void needs(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }
    @BeforeBatch(batch = "gates") public static void gates(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START + 500); }

    private static Schedule schedule(Villager v) { return v.getBrain().getSchedule(); }
    private static int emeralds(net.minecraft.server.level.ServerPlayer p) { return Carried.count(p, Items.EMERALD); }

    /** A level-3 farmer is hired for 24 out of 30 emeralds: the line is offered (the use taken), the
     * command pays and the farmer becomes the player's, following, its profession and level kept.
     * A nitwit and a child are offered nothing and cost nothing; a player short of a level-2 fee
     * pays nothing; a bag pays a level-2 cleric's 16. A free villager keeps vanilla's day. */
    @GameTest(template = "yard", timeoutTicks = 100) public void hiringTakesTheFeeAndMakesAFollower(GameTestHelper h) {
        Yard.floor(h);
        var farmer = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 3);
        var nitwit = Yard.villager(h, 14, 10, VillagerProfession.NITWIT, 1);
        var child = Yard.villager(h, 18, 10, VillagerProfession.NONE, 1);
        child.setAge(-24000);
        var mason = Yard.villager(h, 22, 10, VillagerProfession.MASON, 2);
        // Level 2: a novice with no workstation loses its trade to vanilla within ticks.
        var cleric = Yard.villager(h, 26, 10, VillagerProfession.CLERIC, 2);
        var free = Yard.villager(h, 30, 10, VillagerProfession.FARMER, 2);
        var player = Yard.player(h, 10, 12, "hirer", new ItemStack(Items.EMERALD, 30));
        var poor = Yard.player(h, 22, 12, "poor", new ItemStack(Items.EMERALD, 5));
        var bagged = Yard.bagged(h, 26, 12, "bagged", new ItemStack(Items.EMERALD, 20));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            player.setShiftKeyDown(true);
            h.assertTrue(Yard.use(player, farmer) == InteractionResult.SUCCESS, "the use is taken for the offer");
            Yard.command(player, "serfdom hire");
            h.assertTrue(emeralds(player) == 6, "24 taken: " + emeralds(player));
            h.assertTrue(Workers.of(farmer).ownedBy(player.getUUID()), "the farmer is the player's");
            h.assertTrue(farmer.getVillagerData().getProfession() == VillagerProfession.FARMER && farmer.getVillagerData().getLevel() == 3, "profession and level kept");
            h.assertTrue(schedule(farmer) == WorkerBrain.scheduleOf(Workers.of(farmer)) && farmer.getBrain().getSchedule() != Schedule.VILLAGER_DEFAULT, "it keeps a follower's day");
            h.assertTrue(Workers.shownNeed(farmer).equals(Optional.of(Need.NO_BED)), "it shows that it has no bed");
            for (var v : List.of(nitwit, child)) {
                Yard.use(player, v);
                Yard.command(player, "serfdom hire");
                h.assertFalse(Workers.of(v).owned(), "not hired: " + v.getVillagerData().getProfession());
            }
            h.assertTrue(emeralds(player) == 6, "nothing more taken");
            poor.setShiftKeyDown(true);
            Yard.use(poor, mason);
            Yard.command(poor, "serfdom hire");
            h.assertTrue(emeralds(poor) == 5 && !Workers.of(mason).owned(), "short of 16 with 5: nothing taken, not hired");
            bagged.setShiftKeyDown(true);
            Yard.use(bagged, cleric);
            Yard.command(bagged, "serfdom hire");
            h.assertTrue(Workers.of(cleric).ownedBy(bagged.getUUID()), "the cleric is hired from the bag");
            h.assertTrue(Carried.count(bagged, Items.EMERALD) == 4, "16 taken out of the bag's 20: " + Carried.count(bagged, Items.EMERALD));
            h.assertTrue(schedule(free) == Schedule.VILLAGER_DEFAULT, "a free villager keeps vanilla's day");
        }).thenSucceed();
    }

    /** A novice farmer (level 1, no experience) hired with no workstation keeps its trade for 200
     * ticks; a free one in the same place loses it, which is what vanilla's ResetProfession does
     * and why owned villagers skip it. */
    @GameTest(template = "yard", timeoutTicks = 300) public void aNoviceHiredAwayKeepsItsProfession(GameTestHelper h) {
        Yard.floor(h);
        var player = Yard.player(h, 5, 5, "keeper");
        var hired = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 1);
        var free = Yard.villager(h, 30, 30, VillagerProfession.FARMER, 1);
        h.startSequence().thenExecute(() -> Workers.hire(h.getLevel(), hired, player, Optional.empty()))
                .thenIdle(200).thenExecute(() -> {
                    h.assertTrue(hired.getVillagerData().getProfession() == VillagerProfession.FARMER, "the hired novice is still a farmer");
                    h.assertTrue(free.getVillagerData().getProfession() == VillagerProfession.NONE, "the free novice lost its trade, as vanilla does");
                }).thenSucceed();
    }

    /** Sneak-use on an owned villager is taken before normal priority, where Village Deed offers,
     * for its owner and for anyone else; a plain use is not taken. A free villager's sneak-use
     * reaches normal priority. */
    @GameTest(template = "yard", timeoutTicks = 100) public void ownedVillagersAreNotOfferedOrGifted(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 10, 12, "owner");
        var other = Yard.player(h, 12, 12, "other");
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 1);
        var free = Yard.villager(h, 20, 10, VillagerProfession.FARMER, 1);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(h.getLevel(), worker, owner, Optional.empty());
            owner.setShiftKeyDown(true);
            other.setShiftKeyDown(true);
            h.assertTrue(Yard.use(owner, worker) == InteractionResult.SUCCESS, "the owner's sneak-use is taken");
            h.assertTrue(Yard.use(other, worker) == InteractionResult.SUCCESS, "another's sneak-use is taken");
            h.assertTrue(TestMod.reached(worker.getUUID()) == 0, "neither reached normal priority");
            owner.setShiftKeyDown(false);
            Yard.use(owner, worker);
            h.assertTrue(TestMod.reached(worker.getUUID()) == 1, "a plain use goes on to trading: reached " + TestMod.reached(worker.getUUID()));
            owner.setShiftKeyDown(true);
            Yard.use(owner, free);
            h.assertTrue(TestMod.reached(free.getUUID()) == 1, "a free villager's sneak-use reaches Village Deed's priority");
        }).thenSucceed();
    }

    /** At night a worker walks past a nearer free bed to the one it was given and sleeps in it; the
     * nearer bed stays free. The bed was picked by a click on its foot after Assign bed, a click
     * the pick takes. */
    @GameTest(template = "yard", timeoutTicks = 600, batch = "night") public void aWorkerSleepsInTheBedItWasGiven(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 20, 20, "sleeper");
        var near = Yard.bed(h, 12, 10);
        var given = Yard.bed(h, 22, 10);
        var worker = Yard.villager(h, 10, 12, VillagerProfession.FARMER, 1);
        h.startSequence().thenExecute(() -> {
            Workers.hire(h.getLevel(), worker, owner, Optional.empty());
            Picks.start(owner, worker, Picks.Kind.BED);
            h.assertTrue(Yard.click(owner, given.south()), "the click on the bed's foot is the pick's");
            h.assertTrue(Workers.of(worker).bed().map(b -> b.pos().equals(given)).orElse(false), "the head is the bed: " + Workers.of(worker).bed());
            h.assertTrue(worker.getBrain().getSchedule() == WorkerBrain.scheduleOf(Workers.of(worker)), "a resident's day now");
        }).thenWaitUntil(() -> {
            h.assertTrue(worker.isSleeping(), "asleep");
            h.assertTrue(worker.getSleepingPos().map(given::equals).orElse(false), "in the given bed: " + worker.getSleepingPos());
        }).thenExecute(() -> h.assertTrue(h.getLevel().getPoiManager().getFreeTickets(near) == 1, "the nearer bed is free")).thenSucceed();
    }

    /** Linking: refused without a bed and for a post with four others; a post 10 blocks off,
     * picked by a click after Assign job, links, and the worker keeps a worker's day. */
    @GameTest(template = "yard", timeoutTicks = 100) public void linkingNeedsABedAReachablePostAndRoom(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 5, 5, "linker");
        var worker = Yard.villager(h, 4, 4, VillagerProfession.FARMER, 1);
        var bed = Yard.bed(h, 2, 2);
        var near = Yard.post(h, 12, 2, owner, "farming", 8);
        var full = Yard.post(h, 2, 12, owner, "farming", 8);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var level = h.getLevel();
            Workers.hire(level, worker, owner, Optional.empty());
            h.assertTrue(Workers.link(level, worker, owner, near.getBlockPos()) == Workers.Picked.NO_BED, "no bed: refused");
            h.assertTrue(Workers.assignBed(level, worker, bed) == Workers.Picked.OK, "bed given");
            for (int i = 0; i < 4; i++) full.addWorker(UUID.randomUUID(), net.minecraft.network.chat.Component.literal("other " + i));
            h.assertTrue(Workers.link(level, worker, owner, full.getBlockPos()) == Workers.Picked.POST_FULL, "four on it: refused");
            Picks.start(owner, worker, Picks.Kind.POST);
            h.assertTrue(Yard.click(owner, near.getBlockPos()), "the click on the post is the pick's");
            h.assertTrue(near.workers().contains(worker.getUUID()), "linked");
            h.assertTrue(worker.getBrain().getSchedule() == WorkerBrain.scheduleOf(Workers.of(worker)) && Workers.of(worker).post().isPresent(), "a worker's day");
        }).thenSucceed();
    }

    /** A post more than 48 blocks from the bed is refused (the yard's far corner is 63 away). */
    @GameTest(template = "yard", timeoutTicks = 100) public void aPostBeyondFortyEightBlocksIsRefused(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 5, 5, "distant");
        var worker = Yard.villager(h, 3, 3, VillagerProfession.FARMER, 1);
        var bed = Yard.bed(h, 1, 1);
        var corner = Yard.post(h, 46, 46, owner, "farming", 8);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(h.getLevel(), worker, owner, Optional.empty());
            Workers.assignBed(h.getLevel(), worker, bed);
            h.assertTrue(Math.sqrt(corner.getBlockPos().distSqr(bed)) > 48, "the corner is beyond 48");
            h.assertTrue(Workers.link(h.getLevel(), worker, owner, corner.getBlockPos()) == Workers.Picked.TOO_FAR, "too far: refused");
        }).thenSucceed();
    }

    /** Breaking the post sends its worker back to a villager's day at the base. */
    @GameTest(template = "yard", timeoutTicks = 100) public void breakingThePostSendsItsWorkerHome(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 5, 5, "breaker");
        var bed = Yard.bed(h, 2, 2);
        var post = Yard.post(h, 10, 2, owner, "farming", 8);
        var worker = Yard.worker(h, 4, 4, VillagerProfession.FARMER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.getLevel().destroyBlock(post.getBlockPos(), false);
            h.assertTrue(Workers.of(worker).post().isEmpty() && Workers.of(worker).bed().isPresent(), "no job, the bed kept");
            h.assertTrue(worker.getBrain().getSchedule() == WorkerBrain.scheduleOf(Workers.of(worker)), "a resident's day");
        }).thenSucceed();
    }

    /** The woodcutter fells the natural oak whole, top first, with the stored iron axe (one use a
     * log), clears the leaves only that oak fed, replants it, and leaves alone a bare log pillar and
     * a player's log column crowned with natural leaves. At the end of the shift the logs are in
     * the chest. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "woodcutting") public void aWoodcutterFellsTheOakAndLeavesBuildsAlone(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 30, 30, "woodsman", new ItemStack(Items.OAK_LOG, 4));
        var root = Yard.oak(h, 20, 20);
        var oakLogs = new ArrayList<BlockPos>();
        for (int y = 0; y < 10; y++) if (h.getLevel().getBlockState(root.above(y)).is(BlockTags.LOGS)) oakLogs.add(root.above(y));
        var pillar = new ArrayList<BlockPos>();
        for (int y = 1; y <= 5; y++) { var p = Yard.at(h, 12, y, 20); h.getLevel().setBlock(p, Blocks.OAK_LOG.defaultBlockState(), 3); pillar.add(p); }
        var built = new ArrayList<BlockPos>();
        var chest = Yard.chest(h, 17, 16, new ItemStack(Items.IRON_AXE));
        var bed = Yard.bed(h, 22, 12);
        var post = Yard.post(h, 18, 16, owner, "woodcutting", 8);
        var worker = Yard.worker(h, 18, 14, VillagerProfession.FLETCHER, owner, bed, post);
        worker.getInventory().addItem(new ItemStack(Items.OAK_SAPLING));
        h.startSequence().thenExecute(() -> {
            // A player builds a column of logs by hand and crowns it with natural leaves.
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_LOG, 4));
            for (int y = 0; y < 4; y++) {
                var below = Yard.at(h, 24, y, 10);
                var stand = Yard.at(h, 25, 1, 10);
                owner.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                owner.gameMode.useItemOn(owner, h.getLevel(), owner.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(below), net.minecraft.core.Direction.UP, below, false));
                built.add(below.above());
            }
            for (var p : built) h.assertTrue(h.getLevel().getBlockState(p).is(Blocks.OAK_LOG), "the player placed a log at " + p);
            var top = built.getLast().above();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                h.getLevel().setBlock(top.offset(dx, 0, dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1), 2);
            h.assertTrue(oakLogs.size() >= 4, "the oak has a trunk: " + oakLogs.size());
        }).thenWaitUntil(() -> {
            for (var p : oakLogs) h.assertFalse(h.getLevel().getBlockState(p).is(BlockTags.LOGS), "felled: " + p);
            h.assertTrue(Yard.is(h, root, Blocks.OAK_SAPLING), "replanted");
        }).thenIdle(400).thenExecute(() -> {
            // Four hundred ticks more of looking for work: time enough to walk to the pillar or the
            // column and fell either, were it taken for a tree.
            for (var p : pillar) h.assertTrue(Yard.is(h, p, Blocks.OAK_LOG), "the bare pillar stands: " + p);
            for (var p : built) h.assertTrue(Yard.is(h, p, Blocks.OAK_LOG), "the player's column stands: " + p);
            var axe = worker.getItemBySlot(EquipmentSlot.MAINHAND);
            h.assertTrue(axe.is(Items.IRON_AXE) && axe.getDamageValue() >= oakLogs.size(), "the axe wore a use a log at least: " + axe.getDamageValue() + " for " + oakLogs.size());
            int naturalLeft = 0;
            for (var p : BlockPos.betweenClosed(root.offset(-3, 0, -3), root.offset(3, 9, 3))) {
                var s = h.getLevel().getBlockState(p);
                if (s.is(BlockTags.LEAVES) && !s.getValue(LeavesBlock.PERSISTENT)) naturalLeft++;
            }
            h.assertTrue(naturalLeft == 0, "the oak's leaves are cleared: " + naturalLeft + " left");
            h.getLevel().setDayTime(WorkDay.WORK_END - 500);
        }).thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.OAK_LOG) >= oakLogs.size(), "the logs are stored: " + Yard.count(h, chest, Items.OAK_LOG)))
                .thenSucceed();
    }

    /** The farmer, a farmer by trade, takes ripe wheat, carrots and beetroot and replants each from
     * its own harvest, leaves young wheat, takes the pumpkin and leaves its stem, never tills the
     * grass; at the end of the shift wheat goes to the chest holding wheat, seeds to the chest
     * holding seeds. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "farming") public void aFarmerHarvestsRipeCropsAndSortsThem(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 30, 30, "farmer-owner");
        var level = h.getLevel();
        int[][] ripe = {{10, 10}, {11, 10}, {13, 10}, {14, 10}};
        for (int x = 10; x <= 14; x++) level.setBlock(Yard.at(h, x, 0, 10), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(Yard.at(h, 10, 1, 10), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        level.setBlock(Yard.at(h, 11, 1, 10), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        level.setBlock(Yard.at(h, 12, 1, 10), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3), 3);
        level.setBlock(Yard.at(h, 13, 1, 10), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        level.setBlock(Yard.at(h, 14, 1, 10), Blocks.BEETROOTS.defaultBlockState().setValue(net.minecraft.world.level.block.BeetrootBlock.AGE, 3), 3);
        level.setBlock(Yard.at(h, 10, 0, 8), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(Yard.at(h, 10, 1, 8), Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, net.minecraft.core.Direction.EAST), 3);
        level.setBlock(Yard.at(h, 11, 1, 8), Blocks.PUMPKIN.defaultBlockState(), 3);
        var wheatChest = Yard.chest(h, 15, 14, new ItemStack(Items.WHEAT));
        var seedChest = Yard.chest(h, 9, 14, new ItemStack(Items.WHEAT_SEEDS));
        var overflow = Yard.chest(h, 12, 13, new ItemStack(Items.IRON_HOE));
        var bed = Yard.bed(h, 20, 12);
        var post = Yard.post(h, 12, 12, owner, "farming", 6);
        var worker = Yard.worker(h, 12, 16, VillagerProfession.FARMER, owner, bed, post);
        h.startSequence().thenWaitUntil(() -> {
            for (var r : ripe) {
                var state = level.getBlockState(Yard.at(h, r[0], 1, r[1]));
                h.assertTrue(state.getBlock() instanceof CropBlock && age(state) == 0, "harvested and replanted: " + r[0] + " " + state);
            }
            h.assertTrue(Yard.is(h, Yard.at(h, 11, 1, 8), Blocks.AIR), "the pumpkin is taken");
        }).thenExecute(() -> {
            h.assertTrue(level.getBlockState(Yard.at(h, 12, 1, 10)).getValue(CropBlock.AGE) == 3, "the young wheat is left");
            h.assertTrue(level.getBlockState(Yard.at(h, 10, 1, 8)).getBlock() instanceof StemBlock, "the stem stays");
            h.assertTrue(Yard.is(h, Yard.at(h, 12, 0, 9), Blocks.GRASS_BLOCK), "no grass tilled");
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_HOE), "working with the stored hoe");
            level.setDayTime(WorkDay.WORK_END - 500);
        }).thenWaitUntil(() -> {
            h.assertTrue(worker.getInventory().isEmpty(), "everything is put away: " + worker.getInventory());
            h.assertTrue(Yard.count(h, wheatChest, Items.WHEAT) >= 3, "the wheat is with the wheat: " + Yard.count(h, wheatChest, Items.WHEAT));
            h.assertTrue(Yard.count(h, seedChest, Items.WHEAT_SEEDS) >= 1, "the seed chest keeps its seeds: " + Yard.count(h, seedChest, Items.WHEAT_SEEDS));
            h.assertTrue(Yard.count(h, wheatChest, Items.WHEAT_SEEDS) == 0 && Yard.count(h, overflow, Items.WHEAT_SEEDS) == 0, "every wheat seed went to the seeds");
        }).thenSucceed();
    }

    /** No axe stored: the woodcutter shows "no tool" and fells nothing; an axe put in the chest is
     * fetched and the need clears. A worker carrying a full load with every chest full shows
     * "chest full"; emptying the chest lets it deposit and the need clears. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "needs") public void needsShowUntilTheyAreMet(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 40, 40, "needy");
        var root = Yard.oak(h, 10, 20);
        var axeChest = Yard.chest(h, 9, 16);
        var bed = Yard.bed(h, 14, 12);
        var post = Yard.post(h, 10, 16, owner, "woodcutting", 8);
        var cutter = Yard.worker(h, 10, 14, VillagerProfession.FLETCHER, owner, bed, post);
        var fullStacks = new ItemStack[27];
        for (int i = 0; i < 27; i++) fullStacks[i] = new ItemStack(Items.DIRT, 64);
        var fullChest = Yard.chest(h, 31, 16, fullStacks);
        var bed2 = Yard.bed(h, 34, 12);
        var post2 = Yard.post(h, 30, 16, owner, "farming", 4);
        var carrier = Yard.worker(h, 30, 14, VillagerProfession.FARMER, owner, bed2, post2);
        for (int i = 0; i < 8; i++) carrier.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(Workers.shownNeed(cutter).equals(Optional.of(Need.NO_TOOL)), "no tool: " + Workers.shownNeed(cutter));
            h.assertTrue(Workers.shownNeed(carrier).equals(Optional.of(Need.CHEST_FULL)), "chest full: " + Workers.shownNeed(carrier));
        }).thenExecute(() -> {
            h.assertTrue(Yard.is(h, root, Blocks.OAK_LOG), "nothing felled without an axe");
            var chest = (net.minecraft.world.Container) h.getLevel().getBlockEntity(axeChest);
            chest.setItem(0, new ItemStack(Items.STONE_AXE));
            var full = (net.minecraft.world.Container) h.getLevel().getBlockEntity(fullChest);
            full.clearContent();
        }).thenWaitUntil(() -> {
            h.assertTrue(cutter.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STONE_AXE), "the axe is fetched");
            h.assertTrue(Workers.shownNeed(cutter).isEmpty(), "no need shown: " + Workers.shownNeed(cutter));
            h.assertTrue(Yard.count(h, fullChest, Items.COBBLESTONE) > 0, "the load went in");
            h.assertTrue(Workers.shownNeed(carrier).isEmpty() || Workers.shownNeed(carrier).equals(Optional.of(Need.NO_TOOL)), "chest full clears: " + Workers.shownNeed(carrier));
        }).thenSucceed();
    }

    /** The chain cuffs a worker and leads it, used up; on a free villager it begins a capture's hold
     * rather than leading it, and no chain is used until the hold is done; past ten blocks the chain
     * snaps and the worker stands in its cuffs, nothing dropped, neither chain nor lead (D-0003).
     * Vanilla's lead fails on a worker. */
    @GameTest(template = "yard", timeoutTicks = 200) public void theChainCuffsAWorkerAndASnapLeavesTheCuffsOn(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 10, 12, "chainer", new ItemStack(Serfdom.CHAIN_LEAD.get(), 2));
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 1);
        var free = Yard.villager(h, 14, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(h.getLevel(), worker, owner, Optional.empty());
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
            Yard.use(owner, worker);
            h.assertFalse(worker.isLeashed(), "vanilla's lead fails");
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Serfdom.CHAIN_LEAD.get(), 2));
            Yard.use(owner, free);
            h.assertFalse(free.isLeashed(), "a free villager is not led");
            h.assertTrue(owner.getMainHandItem().getCount() == 2, "the chain is kept while the hold begins");
            owner.stopUsingItem();
            Yard.use(owner, worker);
            h.assertTrue(worker.isLeashed() && worker.getLeashHolder() == owner && Workers.of(worker).cuffed(), "the worker is cuffed and led");
            h.assertTrue(owner.getMainHandItem().getCount() == 1, "one chain used");
            var far = Yard.at(h, 30, 1, 30);
            owner.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        }).thenWaitUntil(() -> h.assertFalse(worker.isLeashed(), "broken past ten blocks")).thenExecute(() -> {
            h.assertTrue(Workers.of(worker).cuffed(), "the cuffs stay on");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(worker.blockPosition()).inflate(4));
            h.assertTrue(drops.stream().noneMatch(e -> e.getItem().is(Serfdom.CHAIN_LEAD.get()) || e.getItem().is(Items.LEAD)), "nothing dropped: " + drops);
            h.assertFalse(Workers.of(free).owned(), "the free villager's hold came to nothing");
        }).thenSucceed();
    }

    /** A worker whose trades are used up restocks them on coming to its post at work time. */
    @GameTest(template = "yard", timeoutTicks = 600, batch = "needs") public void aWorkerRestocksAtItsPost(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 40, 40, "trader");
        var bed = Yard.bed(h, 4, 12);
        var post = Yard.post(h, 10, 10, owner, "farming", 4);
        Yard.chest(h, 9, 10, new ItemStack(Items.IRON_HOE));
        var worker = Yard.worker(h, 10, 14, VillagerProfession.FARMER, owner, bed, post);
        h.startSequence().thenExecute(() -> {
            h.assertTrue(!worker.getOffers().isEmpty(), "the farmer has trades");
            for (var offer : worker.getOffers()) offer.setToOutOfStock();
        }).thenWaitUntil(() -> h.assertTrue(worker.getOffers().stream().noneMatch(o -> o.isOutOfStock()), "restocked")).thenSucceed();
    }

    /** A worker saved and loaded again comes back owned, with its bed, post and tool, and with a
     * worker's brain and day. */
    @GameTest(template = "yard", timeoutTicks = 100) public void aSavedWorkerLoadsAsAWorker(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 5, 5, "saver");
        var bed = Yard.bed(h, 2, 2);
        var post = Yard.post(h, 10, 2, owner, "farming", 8);
        var worker = Yard.worker(h, 4, 4, VillagerProfession.FARMER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var tag = new CompoundTag();
            worker.save(tag);
            var copy = (Villager) EntityType.create(tag, h.getLevel()).orElseThrow();
            var was = Workers.of(worker);
            var is = Workers.of(copy);
            h.assertTrue(is.owner().equals(was.owner()) && is.bed().equals(was.bed()) && is.post().equals(was.post()), "the state came back: " + is);
            h.assertTrue(copy.getBrain().getSchedule() == WorkerBrain.scheduleOf(Workers.of(copy)), "a worker's day");
            h.assertTrue(copy.getBrain().getMemory(MemoryModuleType.HOME).isPresent(), "its bed memory came back");
        }).thenSucceed();
    }

    /** Ripe wheat in the middle of a fenced field whose one way in is a closed gate, too far
     * inside to be reached over the fence: the farmer opens the gate, harvests and replants all
     * three, and once it has walked back out to its post the gate is shut again. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "gates") public void aWorkerOpensTheGateAndShutsItBehindIt(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 40, 40, "gatekeeper");
        var level = h.getLevel();
        for (int x = 10; x <= 12; x++) {
            level.setBlock(Yard.at(h, x, 0, 7), Blocks.FARMLAND.defaultBlockState(), 3);
            level.setBlock(Yard.at(h, x, 1, 7), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
        }
        for (int x = 5; x <= 17; x++) for (int z = 2; z <= 12; z++)
            if (x == 5 || x == 17 || z == 2 || z == 12) level.setBlock(Yard.at(h, x, 1, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
        var gate = Yard.at(h, 11, 1, 12);
        level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, net.minecraft.core.Direction.SOUTH), 3);
        Yard.chest(h, 10, 15, new ItemStack(Items.IRON_HOE));
        var bed = Yard.bed(h, 20, 14);
        var post = Yard.post(h, 11, 15, owner, "farming", 8);
        var worker = Yard.worker(h, 14, 16, VillagerProfession.FARMER, owner, bed, post);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getBlockState(gate).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN), "the gate opens"))
                .thenWaitUntil(() -> {
                    for (int x = 10; x <= 12; x++) h.assertTrue(level.getBlockState(Yard.at(h, x, 1, 7)).getValue(CropBlock.AGE) == 0, "harvested and replanted: " + x);
                }).thenWaitUntil(() -> {
                    // Out of the field: its whole body past the gate block's far face. It rests where
                    // vanilla's arrival leaves it, two blocks from its post, its slide deciding the last
                    // hundredths (1.48 to 1.52 past the gate's near face; D-0008's gate record).
                    h.assertTrue(worker.getZ() - worker.getBbWidth() / 2 > gate.getZ() + 1, "back out of the field: " + worker.position());
                    h.assertFalse(level.getBlockState(gate).getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN), "the gate is shut");
                }).thenSucceed();
    }

    /** A worker that dies drops the tool in its hand and everything it carries, and leaves its
     * post; a post told of a worker who died while it was not loaded takes it off its list when it
     * loads. */
    @GameTest(template = "yard", timeoutTicks = 100) public void aDeadWorkerDropsItsThingsAndLeavesItsPost(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 5, 5, "mourner");
        var bed = Yard.bed(h, 2, 2);
        var post = Yard.post(h, 10, 2, owner, "woodcutting", 8);
        var worker = Yard.worker(h, 4, 4, VillagerProfession.FLETCHER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
            worker.getInventory().addItem(new ItemStack(Items.OAK_LOG, 5));
            var where = worker.blockPosition();
            worker.kill();
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3));
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.IRON_AXE)).count() == 1, "the axe dropped once: " + drops);
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum() == 5, "the logs dropped: " + drops);
            h.assertFalse(post.workers().contains(worker.getUUID()), "it left the post");
            var ghost = UUID.randomUUID();
            post.addWorker(ghost, net.minecraft.network.chat.Component.literal("ghost"));
            com.chunkworks.serfdom.post.Departed.get(h.getLevel().getServer()).add(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), post.getBlockPos()), ghost);
            post.onLoad();
            h.assertFalse(post.workers().contains(ghost), "a worker who died far away is taken off when the post loads");
        }).thenSucceed();
    }

    /** effects: a crop's age, by whichever age property its block has. */
    private static int age(net.minecraft.world.level.block.state.BlockState state) {
        return state.hasProperty(CropBlock.AGE) ? state.getValue(CropBlock.AGE) : state.getValue(net.minecraft.world.level.block.BeetrootBlock.AGE);
    }
}
