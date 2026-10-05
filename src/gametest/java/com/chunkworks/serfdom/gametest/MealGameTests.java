/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Appetite;
import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Hunger;
import com.chunkworks.serfdom.domain.Meals;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.WorkDay;
import com.chunkworks.serfdom.job.Jobs;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 3 (D-0005), with workers' brains running and the hour held:
 * <ul>
 * <li>where food comes from: ready food in the home chest (breakfast, eaten bite by bite until
 * full, the window noted, the schedule back); the canteen, a post's chest near the bed, when home is
 * empty; a dish cooked from home at a free smoker, a player's busy smoker left alone; a pot's meal,
 * its bowls back home;</li>
 * <li>when: a hungry woodcutter breaks off its shift and goes back to it;</li>
 * <li>what hunger does: a hungry worker's pace; a starved one works no more, shows it, keeps its
 * health, and eats once there is food;</li>
 * <li>who: a captive eats, one in chains never walks off to eat and keeps draining; an owned child
 * never hungers; the Worker Screen's hunger.</li>
 * </ul> */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class MealGameTests {
    private static final int SETTLE = 5;

    @BeforeBatch(batch = "breakfast") public static void breakfast(ServerLevel level) { Yard.hourWithHunger(level, 300); }
    @BeforeBatch(batch = "dinner") public static void dinner(ServerLevel level) { Yard.hourWithHunger(level, Meals.DINNER_START + 300); }
    @BeforeBatch(batch = "shift") public static void shift(ServerLevel level) { Yard.hourWithHunger(level, WorkDay.WORK_START + 1000); }

    static void hunger(Villager v, double points) { Appetite.set(v, new Appetite.Belly(new Hunger(points), Meals.Times.NONE)); }
    static double points(Villager v) { return Appetite.of(v).hunger().points(); }
    static ItemStack of(String id, int n) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)), n); }

    /** At breakfast a worker at 6 with ten loaves at home walks to its chest and eats three, to
     * full (6, 11, 16, 20); breakfast is noted, its hungry icon goes, and its idle hour is back. */
    @GameTest(template = "yard", timeoutTicks = 800, batch = "breakfast")
    public void aHungryWorkerEatsBreadFromItsHomeChestToFull(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "baker");
        var bed = Yard.bed(h, 10, 10);
        var home = Yard.chest(h, 13, 10, new ItemStack(Items.BREAD, 10));
        var worker = Yard.worker(h, 16, 16, VillagerProfession.FARMER, owner, bed, null);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            hunger(worker, 6);
            h.assertTrue(Workers.shownNeed(worker).equals(Optional.of(Need.HUNGRY)), "the hungry icon shows: " + Workers.shownNeed(worker));
        }).thenWaitUntil(() -> h.assertTrue(points(worker) >= 20, "fed to full: " + points(worker)))
                .thenWaitUntil(() -> h.assertTrue(worker.getBrain().isActive(Activity.IDLE), "its idle hour is back"))
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, home, Items.BREAD) == 7, "three loaves eaten: " + Yard.count(h, home, Items.BREAD) + " left");
                    h.assertTrue(Appetite.of(worker).times().ateWindow() == Meals.window(h.getLevel().getDayTime()).orElseThrow(), "breakfast is noted");
                    h.assertFalse(Workers.shownNeed(worker).equals(Optional.of(Need.HUNGRY)), "the hungry icon is gone");
                }).thenSucceed();
    }

    /** At dinner, with nothing at home, a worker at 9 eats from the canteen: a cooking post's chest
     * twenty blocks from its bed, one steak (9 to 17; a second would overshoot). */
    @GameTest(template = "yard", timeoutTicks = 800, batch = "dinner")
    public void theCanteenFeedsAWorkerWhoseHomeChestIsEmpty(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "host");
        var bed = Yard.bed(h, 10, 10);
        var home = Yard.chest(h, 12, 10);
        Yard.post(h, 30, 10, owner, "cooking", 4);
        var kitchen = Yard.chest(h, 32, 10, new ItemStack(Items.COOKED_BEEF, 5));
        var worker = Yard.worker(h, 14, 14, VillagerProfession.MASON, owner, bed, null);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> hunger(worker, 9))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, kitchen, Items.COOKED_BEEF) == 4, "a steak from the canteen: " + Yard.count(h, kitchen, Items.COOKED_BEEF)))
                .thenWaitUntil(() -> h.assertTrue(points(worker) >= 16.9, "eaten: " + points(worker)))
                .thenExecute(() -> {
                    h.assertTrue(points(worker) < 17.5, "one steak only: " + points(worker));
                    h.assertTrue(Yard.count(h, home, Items.BREAD) == 0, "home was empty and stays so");
                }).thenSucceed();
    }

    /** At breakfast, with six raw beef and two coal at home and two smokers near it, a worker at 4
     * cooks two steaks at the free smoker (the nearer one holds a player's porkchop and is left
     * alone), eats both (4, 12, 20, less the wait) and leaves the smoker empty; one coal burnt,
     * four beef left. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "breakfast")
    public void aWorkerCooksItsBreakfastAtAFreeSmoker(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "griller");
        var bed = Yard.bed(h, 10, 10);
        var home = Yard.chest(h, 12, 10, new ItemStack(Items.BEEF, 6), new ItemStack(Items.COAL, 2));
        var busy = Yard.block(h, 12, 13, Blocks.SMOKER);
        var free = Yard.block(h, 16, 13, Blocks.SMOKER);
        var worker = Yard.worker(h, 14, 16, VillagerProfession.BUTCHER, owner, bed, null);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            ((AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(busy)).setItem(0, new ItemStack(Items.PORKCHOP));
            hunger(worker, 4);
            // Two steaks onto 4, less what the wait by the smoker cost (a quarter of a point).
        }).thenWaitUntil(() -> h.assertTrue(points(worker) >= 19.5, "fed on its own cooking: " + points(worker)))
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, home, Items.BEEF) == 4, "two beef cooked: " + Yard.count(h, home, Items.BEEF) + " left");
                    h.assertTrue(Yard.count(h, home, Items.COAL) == 1, "one coal burnt: " + Yard.count(h, home, Items.COAL) + " left");
                    var smoker = (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(free);
                    h.assertTrue(smoker.getItem(0).isEmpty() && smoker.getItem(2).isEmpty(), "the smoker is left empty");
                    var player = (AbstractFurnaceBlockEntity) h.getLevel().getBlockEntity(busy);
                    h.assertTrue(player.getItem(0).is(Items.PORKCHOP) && player.getItem(1).isEmpty(), "the player's smoker is untouched");
                    h.assertTrue(Yard.count(h, home, Items.COOKED_BEEF) == 0, "nothing cooked was left over");
                }).thenSucceed();
    }

    /** At dinner, with mushrooms and bowls at home and Farmer's Delight's pot on a lit campfire near
     * it, a worker at 9 cooks two mushroom stews in the pot, eats both (9, 15, 20), and puts the two
     * bowls back in its home chest. */
    @GameTest(template = "yard", timeoutTicks = 2000, batch = "dinner")
    public void aWorkerCooksAPotMealAndPutsTheBowlsBack(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "stewer");
        var bed = Yard.bed(h, 10, 10);
        var home = Yard.chest(h, 12, 10, new ItemStack(Items.BROWN_MUSHROOM, 4), new ItemStack(Items.RED_MUSHROOM, 4), new ItemStack(Items.BOWL, 2));
        var fire = Yard.block(h, 15, 13, Blocks.CAMPFIRE);
        h.getLevel().setBlock(fire.above(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:cooking_pot")).defaultBlockState(), 3);
        var worker = Yard.worker(h, 13, 15, VillagerProfession.FARMER, owner, bed, null);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.assertTrue(h.getLevel().getBlockState(fire).getValue(BlockStateProperties.LIT), "the campfire is lit");
            hunger(worker, 9);
        }).thenWaitUntil(() -> h.assertTrue(points(worker) >= 20, "fed on stew: " + points(worker)))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, home, Items.BOWL) == 2, "the bowls are back home: " + Yard.count(h, home, Items.BOWL)))
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, home, Items.BROWN_MUSHROOM) == 2 && Yard.count(h, home, Items.RED_MUSHROOM) == 2, "two of each mushroom used");
                    h.assertTrue(Yard.count(h, home, Items.MUSHROOM_STEW) == 0, "no stew left over");
                }).thenSucceed();
    }

    /** Mid-shift a woodcutter at 9 breaks off its work, eats bread at home, and goes back to work. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "shift")
    public void aHungryWorkerBreaksOffItsShiftToEatAndGoesBack(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "foreman");
        var bed = Yard.bed(h, 10, 10);
        var home = Yard.chest(h, 12, 10, new ItemStack(Items.BREAD, 4));
        var post = Yard.post(h, 24, 24, owner, "woodcutting", 6);
        Yard.chest(h, 26, 24, new ItemStack(Items.IRON_AXE));
        Yard.oak(h, 28, 28);
        var worker = Yard.worker(h, 14, 14, VillagerProfession.FLETCHER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenWaitUntil(() -> h.assertTrue(worker.getBrain().isActive(Serfdom.WORK.get()), "at work"))
                .thenExecute(() -> hunger(worker, 9))
                .thenWaitUntil(() -> h.assertTrue(worker.getBrain().isActive(Serfdom.MEAL.get()), "it breaks off to eat"))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, home, Items.BREAD) < 4 && points(worker) > 9, "eaten: " + points(worker)))
                .thenWaitUntil(() -> h.assertTrue(worker.getBrain().isActive(Serfdom.WORK.get()), "back at work"))
                .thenSucceed();
    }

    /** A worker at 5 works at three quarters of the pace of a fed one at the same job (the floor's
     * default 0.5: half-way down from half); the pace is what every action's time is divided by. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "shift")
    public void aHungryWorkerWorksSlower(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "taskmaster");
        var fed = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var hungry = Yard.villager(h, 14, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(h.getLevel(), fed, owner, Optional.empty());
            Workers.hire(h.getLevel(), hungry, owner, Optional.empty());
            hunger(hungry, 5);
            var job = Jobs.get(Serfdom.id("farming")).orElseThrow();
            double ratio = Jobs.speed(job, hungry) / Jobs.speed(job, fed);
            h.assertTrue(Math.abs(ratio - 0.75) < 1e-9, "three quarters: " + ratio);
        }).thenSucceed();
    }

    /** A starved woodcutter with nothing at home fells nothing, never even fetches its axe, shows
     * it is hungry, and keeps its health; bread put in its home chest, it eats at its next look and is starved no more. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "shift")
    public void aStarvedWorkerStopsUntilItHasEaten(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "neglectful");
        var bed = Yard.bed(h, 10, 10);
        var home = Yard.chest(h, 12, 10);
        var post = Yard.post(h, 24, 24, owner, "woodcutting", 6);
        var tools = Yard.chest(h, 26, 24, new ItemStack(Items.IRON_AXE));
        var root = Yard.oak(h, 28, 28);
        var worker = Yard.worker(h, 14, 14, VillagerProfession.FLETCHER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> hunger(worker, 0)).thenIdle(300).thenExecute(() -> {
            h.assertTrue(Yard.is(h, root, Blocks.OAK_LOG), "the oak stands");
            h.assertTrue(Yard.count(h, tools, Items.OAK_LOG) == 0, "no logs brought in");
            // A woodcutter fells top first and brings its logs in at the end; its first act is the axe.
            h.assertTrue(Yard.count(h, tools, Items.IRON_AXE) == 1, "the axe was never fetched");
            h.assertTrue(Workers.shownNeed(worker).equals(Optional.of(Need.HUNGRY)), "it shows it is hungry: " + Workers.shownNeed(worker));
            h.assertTrue(worker.getHealth() == worker.getMaxHealth(), "hunger never harms");
            ((net.minecraft.world.Container) h.getLevel().getBlockEntity(home)).setItem(0, new ItemStack(Items.BREAD, 3));
        }).thenWaitUntil(() -> h.assertTrue(points(worker) > 0, "fed once there is food: " + points(worker)))
                .thenExecute(() -> h.assertFalse(Appetite.starved(worker), "starved no more")).thenSucceed();
    }

    /** At breakfast a captive at 8 eats from its home chest; a captive in chains at 8 with bread at
     * home never sits down to a meal (its meal times untouched), and its hunger keeps draining. */
    @GameTest(template = "yard", timeoutTicks = 800, batch = "breakfast")
    public void aCaptiveEatsAndOneInChainsDoesNot(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "jailer");
        var level = h.getLevel();
        var freeBed = Yard.bed(h, 10, 10);
        var freeHome = Yard.chest(h, 12, 10, new ItemStack(Items.BREAD, 5));
        var chainedBed = Yard.bed(h, 30, 30);
        var chainedHome = Yard.chest(h, 32, 30, new ItemStack(Items.BREAD, 5));
        var captive = Yard.villager(h, 14, 14, VillagerProfession.FARMER, 2);
        var chained = Yard.villager(h, 34, 34, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            for (var v : java.util.List.of(captive, chained)) {
                Workers.capture(level, v, owner, new ItemStack(Serfdom.CHAIN_LEAD.get()));
                v.dropLeash(true, false);
                h.assertTrue(Workers.assignBed(level, v, v == captive ? freeBed : chainedBed) == Workers.Picked.OK, "a bed");
            }
            Workers.set(level, captive, Workers.of(captive).withCuffs(false));
            hunger(captive, 8);
            hunger(chained, 8);
        }).thenWaitUntil(() -> h.assertTrue(Yard.count(h, freeHome, Items.BREAD) < 5, "the captive ate"))
                .thenIdle(300)
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chainedHome, Items.BREAD) == 5, "the one in chains ate nothing");
                    h.assertTrue(points(chained) < 8, "and grows hungrier: " + points(chained));
                    h.assertTrue(Appetite.of(chained).times().equals(Meals.Times.NONE), "it never so much as sat down to a meal: " + Appetite.of(chained).times());
                }).thenSucceed();
    }

    /** An owned child never hungers, and the Worker Screen says so; a grown worker's screen shows its
     * hunger in half drumsticks. */
    @GameTest(template = "yard", timeoutTicks = 400, batch = "breakfast")
    public void anOwnedChildNeverHungersAndTheScreenShowsHunger(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "parent");
        var child = Yard.villager(h, 10, 10, VillagerProfession.NONE, 1);
        var grown = Yard.villager(h, 14, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            child.setAge(-24000);
            Workers.hire(h.getLevel(), child, owner, Optional.empty());
            Workers.hire(h.getLevel(), grown, owner, Optional.empty());
            hunger(grown, 13.5);
            h.assertFalse(Appetite.hungers(child), "a child does not hunger");
            Appetite.drain(child, 24000);
            h.assertTrue(points(child) == Hunger.MAX, "and draining it changes nothing");
            h.assertTrue(Screens.view(child).hunger() == -1, "its screen says it does not hunger");
            h.assertTrue(Screens.view(grown).hunger() == 14, "a grown worker's shows 14 half drumsticks: " + Screens.view(grown).hunger());
        }).thenSucceed();
    }
}
