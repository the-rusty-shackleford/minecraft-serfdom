/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Climate;
import com.chunkworks.serfdom.domain.Household;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Taste;
import com.chunkworks.serfdom.domain.Taste.Category;
import com.chunkworks.serfdom.domain.Verdict.Reaction;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.market.Counter;
import com.chunkworks.serfdom.market.Households;
import com.chunkworks.serfdom.market.Needs;
import com.chunkworks.serfdom.market.Prices;
import com.chunkworks.serfdom.market.Tastes;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.chunkworks.serfdom.gametest.MarketGameTests.LIKES_FOOD;
import static com.chunkworks.serfdom.gametest.MarketGameTests.proceeds;
import static com.chunkworks.serfdom.gametest.MarketGameTests.purse;
import static com.chunkworks.serfdom.gametest.MarketGameTests.stall;
import static com.chunkworks.serfdom.gametest.MarketGameTests.stock;
import static com.chunkworks.serfdom.gametest.MarketGameTests.townsman;

/** Real-server partitions of phase 4b (D-0006), with villagers' brains running and the hour held:
 * <ul>
 * <li>climate: a bed in the desert, in the snow and on the plains; a good from the snow, one from the
 * badlands, and what each will pay for them;</li>
 * <li>taste: drawn from the villager and leaning by its trade's data; what an item of a category, and
 * of two, is worth to it; what a farmer sells;</li>
 * <li>window shopping: a villager that needs nothing glances at a stall whose goods it doesn't want,
 * then buys at one whose goods it likes best, celebrating with a hop, and sees neither again that
 * day; a worker buys what it likes for its home chest;</li>
 * <li>a villager selling: a townsman short of food buys a free farmer's spare bread rather than a
 * dearer stall's, while a hired farmer with more beside it sells nothing;</li>
 * <li>a For Sale block is never a worker's home chest, nor its post's storage; a villager plans its
 * way around one, never over it.</li>
 * </ul> */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class TasteGameTests {
    private static final int SETTLE = 5;

    @BeforeBatch(batch = "taste") public static void taste(ServerLevel level) { Yard.hour(level, 6000); Yard.economy(); }
    // Each trip stands alone in its batch, as 4a's do: a villager shops anywhere within 64 blocks.
    @BeforeBatch(batch = "browse") public static void browse(ServerLevel level) { MarketGameTests.town(level); }
    @BeforeBatch(batch = "peddler") public static void peddler(ServerLevel level) { MarketGameTests.town(level); }
    @BeforeBatch(batch = "worker_browse") public static void workerBrowse(ServerLevel level) { Yard.hour(level, Shopping.HIRED_START + 300); Yard.economy(); }

    /** effects: the biome at and around {@code at} set, as the {@code fillbiome} command sets it. */
    static void biome(GameTestHelper h, BlockPos at, String biome) {
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput().withLevel(h.getLevel());
        server.getCommands().performPrefixedCommand(source, "fillbiome %d %d %d %d %d %d %s".formatted(
                at.getX() - 3, at.getY() - 3, at.getZ() - 3, at.getX() + 3, at.getY() + 3, at.getZ() + 3, biome));
    }

    private static void near(GameTestHelper h, double expected, double actual, String what) {
        h.assertTrue(Math.abs(expected - actual) < 1e-9, what + ": " + actual + ", not " + expected);
    }

    // ---- climate and taste ------------------------------------------------------------------

    /** Three townsmen, their beds in the desert, the snow and the plains: hot, cold, temperate. Raw
     * salmon is from the cold, orange terracotta from the badlands, neither a matter of taste: the
     * snow's townsman pays the salmon's base value and half again for the terracotta, the desert's the
     * reverse, the plains' half again for both. A carpet is decor: worth its value times the taste for
     * decor, and half again that needed. A golden carrot, food and luxury, goes by the higher. */
    @GameTest(template = "yard", timeoutTicks = 60, batch = "taste")
    public void aVillagesClimateIsItsBedsBiomeAndGoodsFromElsewhereAreWorthMore(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var hotBed = Yard.bed(h, 8, 8);
        var coldBed = Yard.bed(h, 20, 8);
        var plainBed = Yard.bed(h, 32, 8);
        biome(h, hotBed, "minecraft:desert");
        biome(h, coldBed, "minecraft:snowy_plains");
        biome(h, plainBed, "minecraft:plains");
        var hot = Yard.villager(h, 8, 12, VillagerProfession.MASON, 2);
        var cold = Yard.villager(h, 20, 12, VillagerProfession.MASON, 2);
        var plain = Yard.villager(h, 32, 12, VillagerProfession.MASON, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            townsman(h, hot, hotBed, 5);
            townsman(h, cold, coldBed, 5);
            townsman(h, plain, plainBed, 5);
            h.assertTrue(Tastes.climate(level, hot) == Climate.HOT, "the desert is hot: " + Tastes.climate(level, hot));
            h.assertTrue(Tastes.climate(level, cold) == Climate.COLD, "the snow is cold: " + Tastes.climate(level, cold));
            h.assertTrue(Tastes.climate(level, plain) == Climate.TEMPERATE, "the plains are temperate: " + Tastes.climate(level, plain));
            var salmon = new ItemStack(Items.SALMON);
            var clay = new ItemStack(Items.ORANGE_TERRACOTTA);
            double s = Prices.value(level, salmon).orElseThrow(), t = Prices.value(level, clay).orElseThrow();
            h.assertTrue(Tastes.categories(salmon).isEmpty() && Tastes.categories(clay).isEmpty(), "neither is a matter of taste");
            near(h, s, Counter.willing(level, cold, salmon, false), "salmon in the snow");
            near(h, 1.5 * s, Counter.willing(level, hot, salmon, false), "salmon in the desert");
            near(h, 1.5 * s, Counter.willing(level, plain, salmon, false), "salmon on the plains");
            near(h, t, Counter.willing(level, hot, clay, false), "terracotta in the desert");
            near(h, 1.5 * t, Counter.willing(level, cold, clay, false), "terracotta in the snow");
            near(h, 1.5 * t, Counter.willing(level, plain, clay, false), "terracotta on the plains");
            near(h, 2.25 * s, Counter.willing(level, hot, salmon, true), "salmon needed in the desert: the climate's and the need's");
            var carpet = new ItemStack(Items.WHITE_CARPET);
            double c = Prices.value(level, carpet).orElseThrow();
            var taste = Tastes.of(plain);
            near(h, c * taste.decor(), Counter.willing(level, plain, carpet, false), "a carpet, by its taste for decor");
            near(h, 1.5 * c * taste.decor(), Counter.willing(level, plain, carpet, true), "needed");
            var carrot = new ItemStack(Items.GOLDEN_CARROT);
            near(h, Prices.value(level, carrot).orElseThrow() * Math.max(taste.food(), taste.luxury()), Counter.willing(level, plain, carrot, false),
                    "a golden carrot, food and luxury, by the higher");
        }).thenSucceed();
    }

    /** A villager's taste is its UUID's draw, leaning as its trade's data says: a farmer toward food and
     * tools, a nitwit toward food and away from tools. A farmer sells bread, potatoes, carrots and
     * beetroot, keeping three, eight, eight and none; a librarian sells nothing. */
    @GameTest(template = "yard", timeoutTicks = 40, batch = "taste")
    public void aTasteIsTheVillagersOwnAndLeansByItsTrade(GameTestHelper h) {
        Yard.floor(h);
        var v = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var id = v.getUUID();
            var expected = Taste.of(id.getMostSignificantBits(), id.getLeastSignificantBits(), Map.of(Category.FOOD, 0.5, Category.TOOLS, 0.5), 0.5);
            h.assertTrue(Tastes.of(v).equals(expected), "the farmer's lean: " + Tastes.of(v) + ", not " + expected);
            var nid = UUID.randomUUID();
            var nitwit = Taste.of(nid.getMostSignificantBits(), nid.getLeastSignificantBits(), Map.of(Category.FOOD, 1.0, Category.TOOLS, -1.0), 0.5);
            h.assertTrue(Tastes.of(VillagerProfession.NITWIT, nid).equals(nitwit), "the nitwit's lean");
            h.assertTrue(Needs.sells(VillagerProfession.FARMER).equals(Map.of(Items.BREAD, 3, Items.POTATO, 8, Items.CARROT, 8, Items.BEETROOT, 0)),
                    "a farmer's wares: " + Needs.sells(VillagerProfession.FARMER));
            h.assertTrue(Needs.sells(VillagerProfession.LIBRARIAN).isEmpty(), "a librarian sells nothing");
        }).thenSucceed();
    }

    // ---- window shopping --------------------------------------------------------------------

    /** A farmer whose favourite is decor, with all it needs at home and five emeralds: in its social
     * time it looks at the nearer stall, paper, which no need or taste of its wants (a glance, "not
     * interested"), then at the farther, carpets at their base value: a bargain, one sale of four for
     * its household, and its favourite, so it celebrates and hops. Both seen, it goes to neither again
     * that day. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "browse")
    public void aWindowShopperBuysWhatItLikesAndGlancesAtWhatItDoesNot(GameTestHelper h) {
        MarketGameTests.clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "draper");
        var bed = Yard.bed(h, 8, 8);
        var paper = stall(h, 16, 8, owner, new ItemStack(Items.PAPER), 4, 1, new ItemStack(Items.PAPER, 32));
        var carpets = stall(h, 26, 8, owner, new ItemStack(Items.WHITE_CARPET), 4, 1, new ItemStack(Items.WHITE_CARPET, 32));
        var v = Yard.villager(h, 10, 12, VillagerProfession.FARMER, 2, t -> t.favourite() == Category.DECOR && t.likes(Category.DECOR));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            townsman(h, v, bed, 5);
            Households.set(v, Household.EMPTY.add("minecraft:bread", 4).add("minecraft:iron_hoe", 1).add("minecraft:bone_meal", 8));
            h.assertTrue(Needs.wants(h.getLevel(), v).isEmpty(), "it needs nothing: " + Needs.wants(h.getLevel(), v));
        }).thenWaitUntil(() -> h.assertFalse(paper.ledger().lines().isEmpty(), "a look at the paper"))
                .thenExecute(() -> {
                    h.assertTrue(paper.ledger().lines().getLast().reaction() == Reaction.NOT_INTERESTED, "not interested: " + paper.ledger().lines());
                    h.assertTrue(paper.ledger().days().getLast().notInterested() == 1, "the day's total");
                    h.assertTrue(stock(paper, Items.PAPER) == 32 && purse(v) == 5, "nothing changes hands");
                    h.assertTrue(carpets.ledger().lines().isEmpty(), "the nearer first");
                })
                .thenWaitUntil(() -> h.assertFalse(carpets.ledger().lines().isEmpty(), "a look at the carpets"))
                .thenExecute(() -> {
                    var line = carpets.ledger().lines().getLast();
                    h.assertTrue(line.reaction() == Reaction.BARGAIN && line.items() == 4 && line.paid() == 1, "a bargain, four for one: " + line);
                    h.assertTrue(purse(v) == 4 && proceeds(carpets) == 1, "an emerald paid");
                    h.assertTrue(TestMod.heardFrom(SoundEvents.VILLAGER_CELEBRATE, v) == 1, "its favourite: it celebrates");
                })
                .thenExecuteAfter(3, () -> h.assertFalse(v.onGround(), "and hops"))
                .thenWaitUntil(() -> h.assertTrue(Households.of(v).goods().getOrDefault("minecraft:white_carpet", 0) == 4, "the carpets taken home: " + Households.of(v).goods()))
                .thenIdle(300)
                .thenExecute(() -> {
                    h.assertTrue(paper.ledger().lines().size() == 1 && carpets.ledger().lines().size() == 1, "neither seen again today: "
                            + paper.ledger().lines().size() + ", " + carpets.ledger().lines().size());
                    var seen = Shopping.seen(com.chunkworks.serfdom.market.Purses.of(v).day(), h.getLevel().getDayTime());
                    h.assertTrue(seen.equals(java.util.Set.of(paper.getBlockPos().asLong(), carpets.getBlockPos().asLong())), "both seen: " + seen);
                }).thenSucceed();
    }

    /** A fletcher at its meeting, a day's food and one stone pickaxe in its home chest and no post,
     * whose taste runs to tools: at a stall of stone pickaxes at their base value it buys two, to make
     * the three it wants at most, and they land in its home chest. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "worker_browse")
    public void aWorkerBuysWhatItLikesForItsHomeChest(GameTestHelper h) {
        MarketGameTests.clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "patron");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(Items.BREAD, 20), new ItemStack(Items.STONE_PICKAXE));
        var s = stall(h, 22, 8, owner, new ItemStack(Items.STONE_PICKAXE), 1, 1, new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE),
                new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_PICKAXE));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FLETCHER, owner, bed, null, t -> t.tools() >= 1);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            MarketGameTests.purse(worker, 5);
            h.assertTrue(Needs.wants(h.getLevel(), worker).isEmpty(), "it needs nothing: " + Needs.wants(h.getLevel(), worker));
            h.assertTrue(Kitchen.home(h.getLevel(), worker).equals(Optional.of(chest)), "its home chest");
        }).thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.STONE_PICKAXE) == 3, "three pickaxes at home: " + Yard.count(h, chest, Items.STONE_PICKAXE)))
                .thenExecute(() -> {
                    var line = s.ledger().lines().getLast();
                    h.assertTrue(line.reaction() == Reaction.BARGAIN && line.sales() == 2, "two sales: " + line);
                    h.assertTrue(purse(worker) == 3 && stock(s, Items.STONE_PICKAXE) == 3, "two emeralds paid, three left on the stall: " + purse(worker));
                }).thenIdle(200)
                .thenExecute(() -> h.assertTrue(Yard.count(h, chest, Items.STONE_PICKAXE) == 3 && s.ledger().lines().size() == 1, "no more than three: "
                        + Yard.count(h, chest, Items.STONE_PICKAXE)))
                .thenSucceed();
    }

    // ---- villagers selling ------------------------------------------------------------------

    /** A librarian with no food at home and five emeralds: a free farmer twenty-odd blocks off carries
     * 64 bread (keeping three), a stall nearer sells four bread for an emerald, and a hired farmer
     * nearer still carries 64. It walks to the free farmer and buys six for an emerald (a sixth an
     * item, cheaper than the stall's quarter), which goes into its household; the farmer's purse takes
     * the emerald. The stall and the hired farmer sell nothing. */
    @GameTest(template = "yard", timeoutTicks = 1600, batch = "peddler")
    public void aTownsmanBuysAFreeFarmersSpareBreadRatherThanADearerStalls(GameTestHelper h) {
        MarketGameTests.clearOldStalls(h);
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 2, 2, "landlord");
        var bed = Yard.bed(h, 8, 8);
        var farmerBed = Yard.bed(h, 30, 30);
        var s = stall(h, 12, 4, owner, new ItemStack(Items.BREAD), 4, 1, new ItemStack(Items.BREAD, 32));
        var buyer = Yard.villager(h, 10, 12, VillagerProfession.LIBRARIAN, 2, LIKES_FOOD);
        var farmer = Yard.villager(h, 28, 28, VillagerProfession.FARMER, 2);
        var hired = Yard.villager(h, 12, 14, VillagerProfession.FARMER, 2);
        // Hired at once: a free villager takes a free bed near it, and this one stands by the buyer's.
        Workers.hire(level, hired, owner, Optional.empty());
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            townsman(h, buyer, bed, 5);
            townsman(h, farmer, farmerBed, 10);
            // The farmer needs nothing itself, so it stays a seller and buys nothing here.
            Households.set(farmer, Household.EMPTY.add("minecraft:bread", 4).add("minecraft:iron_hoe", 1).add("minecraft:bone_meal", 8));
            farmer.getInventory().addItem(new ItemStack(Items.BREAD, 64));
            hired.getInventory().addItem(new ItemStack(Items.BREAD, 64));
            h.assertTrue(com.chunkworks.serfdom.market.Peddlers.wares(level, hired).isEmpty(), "a worker sells nothing");
            h.assertFalse(com.chunkworks.serfdom.market.Peddlers.wares(level, farmer).isEmpty(), "the free farmer sells its bread");
        }).thenWaitUntil(() -> h.assertTrue(Households.of(buyer).goods().getOrDefault("minecraft:bread", 0) == 6, "six bread home: " + Households.of(buyer).goods()))
                .thenExecute(() -> {
                    h.assertTrue(purse(buyer) == 4, "an emerald paid: " + purse(buyer));
                    h.assertTrue(purse(farmer) == 11, "into the farmer's purse: " + purse(farmer));
                    // The farmer, needing nothing, may have looked it over: a look, never a sale.
                    h.assertTrue(stock(s, Items.BREAD) == 32 && s.ledger().lines().stream().noneMatch(l -> l.reaction().bought()), "the dearer stall sold nothing: " + s.ledger().lines());
                    h.assertTrue(hired.getInventory().countItem(Items.BREAD) == 64, "nor the hired farmer");
                    h.assertTrue(com.chunkworks.serfdom.market.Purses.of(buyer).day().sales() == 1, "one of its three sales today");
                    // The sellers leave with the test: a later batch's townsman would find their bread.
                    farmer.discard();
                    hired.discard();
                }).thenSucceed();
    }

    // ---- stalls and posts stand in the way --------------------------------------------------

    /** A villager just west of a stall, bound for another stall east of it, plans its way around the
     * first and ends beside the second, on the ground: no step of its path stands on either. Vanilla
     * took the stall, no full block, for ground a full block high (its sign) within a villager's jump,
     * and planned straight over it: a window shopper bound for the next stall stuck on the sign of the
     * one between (the gate's trace: 12 steps in a straight line, "can't reach"). */
    @GameTest(template = "yard", timeoutTicks = 60, batch = "taste")
    public void aVillagerPlansItsWayAroundAStallNeverOverIt(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "surveyor");
        var between = stall(h, 16, 8, owner, new ItemStack(Items.PAPER), 4, 1).getBlockPos();
        var bound = stall(h, 26, 8, owner, new ItemStack(Items.WHITE_CARPET), 4, 1).getBlockPos();
        // Alive, not frozen: a navigation plans only for a mob on the ground, and a frozen one never lands.
        var v = Yard.villager(h, 15, 8, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var from = Yard.at(h, 15, 1, 8);
            v.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
            // As a shopper's walk target asks: within a block of the stall.
            var path = v.getNavigation().createPath(bound, 1);
            h.assertTrue(path != null, "a way toward the far stall from " + v.blockPosition().toShortString() + " (on the ground " + v.onGround() + ")");
            var end = path.getEndNode().asBlockPos();
            h.assertTrue(end.getY() == bound.getY() && Math.abs(end.getX() - bound.getX()) <= 1 && Math.abs(end.getZ() - bound.getZ()) <= 1 && !end.equals(bound),
                    "it ends beside the far stall, on the ground: " + end.toShortString() + ", the stall " + bound.toShortString() + ", " + path.getNodeCount() + " steps");
            for (int i = 0; i < path.getNodeCount(); i++) {
                var n = path.getNode(i);
                h.assertFalse(n.x == between.getX() && n.z == between.getZ(), "a step on the stall between at " + n.asBlockPos().toShortString() + " of " + path.getNodeCount());
            }
        }).thenSucceed();
    }

    // ---- a stall is not storage -------------------------------------------------------------

    /** A For Sale block beside a worker's bed, nearer than its chest: the chest is its home. A stall
     * inside a post's area is not the post's storage; the chest beside it is. */
    @GameTest(template = "yard", timeoutTicks = 60, batch = "taste")
    public void aStallIsNeitherAHomeChestNorAPostsStorage(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "shopkeeper");
        var bed = Yard.bed(h, 8, 8);
        var s = stall(h, 9, 8, owner, new ItemStack(Items.BREAD), 4, 1, new ItemStack(Items.BREAD, 16));
        var chest = Yard.chest(h, 13, 8);
        var post = Yard.post(h, 24, 24, owner, "farming", 6);
        var inArea = stall(h, 26, 24, owner, new ItemStack(Items.WHEAT), 8, 1);
        var store = Yard.chest(h, 22, 24);
        var worker = Yard.worker(h, 10, 12, VillagerProfession.FARMER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var level = h.getLevel();
            h.assertTrue(Kitchen.home(level, worker).equals(Optional.of(chest)), "the chest is its home, not the stall: " + Kitchen.home(level, worker) + ", stall " + s.getBlockPos());
            var storage = com.chunkworks.serfdom.job.Storage.scan(level, post.getBlockPos(), 6);
            h.assertTrue(storage.contains(store) && !storage.contains(inArea.getBlockPos()), "the post's storage: " + storage);
        }).thenSucceed();
    }
}
