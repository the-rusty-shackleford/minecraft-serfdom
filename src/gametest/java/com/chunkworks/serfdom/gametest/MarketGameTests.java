/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Household;
import com.chunkworks.serfdom.domain.Meals;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.Purse;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.domain.Verdict.Reaction;
import com.chunkworks.serfdom.market.Baskets;
import com.chunkworks.serfdom.market.ClosedOffer;
import com.chunkworks.serfdom.market.ForSaleBlockEntity;
import com.chunkworks.serfdom.market.Households;
import com.chunkworks.serfdom.market.Prices;
import com.chunkworks.serfdom.market.Purses;
import com.chunkworks.villagedeed.Claims;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.Deed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 4a (D-0006), with villagers' brains running and the hour held:
 * <ul>
 * <li>trading with players through the real trade menu: a purse that pays for three sales and shows
 * the fourth sold out, the offer's uses untouched; a purchase that fills the purse and opens a
 * closed trade; the cap; the economy off;</li>
 * <li>purses: a new villager at the line, one found in the world full, one that had a purse keeping
 * it; the hire fee paid in; the morning deposit, once a morning, never to a captive or a child; a
 * household's morning use; the Worker Screen's purse;</li>
 * <li>a free villager's trip in its social time: a bargain bought and carried home, too pricey, can't
 * afford, the cheaper of two stalls, one customer at a time, a stall out of reach, a stranger's
 * stall in a bought village;</li>
 * <li>a worker: food bought at dinner when it has none, never by a captive; an axe bought at its
 * meeting for its post;</li>
 * <li>the stall: hoppers fill its stock and take its proceeds, a stranger can neither open nor break
 * it, an explosion leaves it; base values off the price lists, data and food.</li>
 * </ul> */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class MarketGameTests {
    private static final int SETTLE = 5;

    @BeforeBatch(batch = "trade") public static void trade(ServerLevel level) { Yard.hour(level, 6000); Yard.economy(); }
    @BeforeBatch(batch = "morning") public static void morning(ServerLevel level) { Yard.hour(level, 100); Yard.economy(); }
    // Each town trip runs alone in its batch: a villager shops at any stall within 64 blocks of its
    // bed, and the tests of one batch stand side by side, each a stall another's villager can see.
    static void town(ServerLevel level) { Yard.hour(level, Shopping.FREE_START + 300); Yard.economy(); }
    @BeforeBatch(batch = "town_bargain") public static void townBargain(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "town_pricey") public static void townPricey(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "town_broke") public static void townBroke(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "town_cheaper") public static void townCheaper(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "town_pair") public static void townPair(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "town_reach") public static void townReach(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "town_deed") public static void townDeed(ServerLevel level) { town(level); }
    @BeforeBatch(batch = "meeting") public static void meeting(ServerLevel level) { Yard.hour(level, Shopping.HIRED_START + 300); Yard.economy(); }
    @BeforeBatch(batch = "supper") public static void supper(ServerLevel level) { Yard.hourWithHunger(level, Meals.DINNER_START + 300); Yard.economy(); }
    @BeforeBatch(batch = "no_economy") public static void noEconomy(ServerLevel level) { Yard.hour(level, 6000); }

    // ---- fixtures ---------------------------------------------------------------------------

    static void purse(Villager v, int emeralds) { Purses.set(v, new Purses.Saved(new Purse(emeralds, Purse.NEVER), Shopping.Day.NONE)); }
    static int purse(Villager v) { return Purses.emeralds(v); }

    /** effects: a For Sale block at the yard position, {@code owner}'s, selling {@code quantity} of
     * {@code sells} for {@code price}, its stock {@code stock}. */
    static ForSaleBlockEntity stall(GameTestHelper h, BlockPos pos, ServerPlayer owner, ItemStack sells, int quantity, int price, ItemStack... stock) {
        h.getLevel().setBlock(pos, Serfdom.FOR_SALE.get().defaultBlockState(), 3);
        var s = (ForSaleBlockEntity) h.getLevel().getBlockEntity(pos);
        s.claim(owner.getUUID(), owner.getGameProfile().getName());
        s.setTemplate(sells);
        s.setQuantity(quantity);
        s.setPrice(price);
        for (int i = 0; i < stock.length; i++) s.stockContainer.setItem(i, stock[i]);
        return s;
    }
    static ForSaleBlockEntity stall(GameTestHelper h, int x, int z, ServerPlayer owner, ItemStack sells, int quantity, int price, ItemStack... stock) {
        return stall(h, Yard.at(h, x, 1, z), owner, sells, quantity, price, stock);
    }

    /** effects: how many of {@code item} the stall's stock holds, and its proceeds' emeralds. */
    static int stock(ForSaleBlockEntity s, Item item) { int n = 0; for (int i = 0; i < s.stockContainer.getContainerSize(); i++) if (s.stockContainer.getItem(i).is(item)) n += s.stockContainer.getItem(i).getCount(); return n; }
    static int proceeds(ForSaleBlockEntity s) { int n = 0; for (int i = 0; i < s.proceedsContainer.getContainerSize(); i++) n += s.proceedsContainer.getItem(i).getCount(); return n; }

    /** effects: a free villager of the town with {@code bed} for its home, as vanilla would have
     * claimed it, and {@code emeralds} in its purse. Requires the bed registered as a home. */
    static Villager townsman(GameTestHelper h, Villager v, BlockPos bed, int emeralds) {
        var level = h.getLevel();
        // Vanilla's own claim may have come first: the villager takes a free bed near it.
        boolean claimed = v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> g.pos().equals(bed)).orElse(false);
        h.assertTrue(claimed || level.getPoiManager().take(t -> t.is(PoiTypes.HOME), (t, p) -> p.equals(bed), bed, 1).isPresent(), "the bed is a home to take");
        v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
        purse(v, emeralds);
        return v;
    }

    /** effects: the For Sale blocks earlier batches left within reach of this test taken away: the
     * GameTest server leaves old tests' areas standing, and a shopper here would see them. Only for a
     * test alone in its batch. */
    static void clearOldStalls(GameTestHelper h) {
        var level = h.getLevel();
        var bounds = h.getBounds();
        var centre = BlockPos.containing(bounds.getCenter());
        var old = level.getPoiManager().findAll(t -> t.is(Serfdom.FOR_SALE_POI.getKey()), p -> !bounds.contains(p.getCenter()), centre, 128,
                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList();
        for (var p : old) level.removeBlock(p, false);
    }

    static int count(ServerPlayer p, Item item) { int n = 0; for (var s : p.getInventory().items) if (s.is(item)) n += s.getCount(); return n; }

    static Villager trader(GameTestHelper h, int x, int z, MerchantOffer... offers) {
        var v = Yard.villager(h, x, z, VillagerProfession.FARMER, 2);
        var list = new MerchantOffers();
        list.addAll(List.of(offers));
        v.setOffers(list);
        return v;
    }

    static boolean closed(MerchantOffer o) { return ((ClosedOffer) o).serfdom$closed(); }

    // ---- trading with players ---------------------------------------------------------------

    /** A farmer with three emeralds buys wheat from a player at ten for one: the player takes three
     * emeralds from its result slot in one shift-click, and the fourth trade is sold out though the
     * payment slot holds 34 more wheat; the offer counts three uses, as vanilla does. Closed, the
     * trade is open again. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "trade")
    public void aPurseOfThreePaysForThreeSalesAndTheFourthIsSoldOut(GameTestHelper h) {
        Yard.floor(h);
        var player = Yard.player(h, 10, 12, "seller", new ItemStack(Items.WHEAT, 64), new ItemStack(Items.WHEAT, 36));
        var offer = new MerchantOffer(new ItemCost(Items.WHEAT, 10), new ItemStack(Items.EMERALD), 16, 2, 0.05F);
        var v = trader(h, 10, 10, offer);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            purse(v, 3);
            Yard.use(player, v);
            h.assertTrue(player.containerMenu instanceof MerchantMenu, "the trade screen opens: " + player.containerMenu);
            var menu = (MerchantMenu) player.containerMenu;
            menu.setSelectionHint(0);
            menu.tryMoveItems(0);
            h.assertTrue(menu.getSlot(0).getItem().getCount() == 64, "a stack of wheat to pay with");
            menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(count(player, Items.EMERALD) == 3, "three sales: " + count(player, Items.EMERALD));
            h.assertTrue(purse(v) == 0, "the purse paid them: " + purse(v));
            h.assertTrue(menu.getSlot(0).getItem().getCount() == 34, "34 wheat left to pay with: " + menu.getSlot(0).getItem());
            h.assertTrue(menu.getSlot(2).getItem().isEmpty(), "the fourth is not offered");
            h.assertTrue(v.getOffers().get(0).isOutOfStock(), "it shows sold out");
            h.assertTrue(v.getOffers().get(0).getUses() == 3, "the offer counts three uses: " + v.getOffers().get(0).getUses());
            // What the client is sent: the packet copies the offers, then writes them.
            var packet = new net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket(menu.containerId, v.getOffers(), 2, 0, true, true);
            h.assertTrue(packet.getOffers().get(0).isOutOfStock(), "the packet's copy is sold out too (single player hands it over unwritten)");
            var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket.STREAM_CODEC.encode(buf, packet);
            var sent = net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket.STREAM_CODEC.decode(buf);
            h.assertTrue(sent.getOffers().get(0).isOutOfStock(), "and as a server's client reads it off the wire");
            player.closeContainer();
            h.assertFalse(v.getOffers().get(0).isOutOfStock(), "nobody trading: open again (3 of 16 used)");
            h.assertFalse(closed(v.getOffers().get(0)), "the purse's mark is gone");
        }).thenSucceed();
    }

    /** With an empty purse, a farmer's wheat trade is sold out; a player buys six bread for an
     * emerald, which goes into the purse, and the wheat trade opens; one wheat sale later it is shut
     * again. A purse at 63 taking five keeps 64. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "trade")
    public void aPlayersPurchaseFillsThePurseAndOpensATradeAndTheCapHolds(GameTestHelper h) {
        Yard.floor(h);
        var player = Yard.player(h, 10, 12, "buyer", new ItemStack(Items.EMERALD, 1), new ItemStack(Items.WHEAT, 20));
        var rich = Yard.player(h, 20, 12, "rich", new ItemStack(Items.EMERALD, 5));
        var bread = new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 16, 2, 0.05F);
        var wheat = new MerchantOffer(new ItemCost(Items.WHEAT, 10), new ItemStack(Items.EMERALD), 16, 2, 0.05F);
        var v = trader(h, 10, 10, bread, wheat);
        var full = trader(h, 20, 10, new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(Items.DIAMOND), 12, 2, 0.05F));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            purse(v, 0);
            purse(full, 63);
            Yard.use(player, v);
            var menu = (MerchantMenu) player.containerMenu;
            h.assertTrue(v.getOffers().get(1).isOutOfStock() && !v.getOffers().get(0).isOutOfStock(), "nothing to pay with: only the wheat trade is shut");
            menu.setSelectionHint(0);
            menu.tryMoveItems(0);
            menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(count(player, Items.BREAD) == 6 && purse(v) == 1, "bread bought, the emerald in the purse: " + purse(v));
            h.assertFalse(v.getOffers().get(1).isOutOfStock(), "the wheat trade opens");
            menu.setSelectionHint(1);
            menu.tryMoveItems(1);
            menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(count(player, Items.EMERALD) == 1 && purse(v) == 0, "one wheat sale, and no more: " + count(player, Items.EMERALD));
            h.assertTrue(v.getOffers().get(1).isOutOfStock(), "shut again");
            player.closeContainer();
            Yard.use(rich, full);
            var m2 = (MerchantMenu) rich.containerMenu;
            m2.setSelectionHint(0);
            m2.tryMoveItems(0);
            m2.clicked(2, 0, ClickType.QUICK_MOVE, rich);
            h.assertTrue(count(rich, Items.DIAMOND) == 1, "the diamond is bought");
            h.assertTrue(purse(full) == 64, "63 and 5 make the cap; the rest is lost: " + purse(full));
            rich.closeContainer();
        }).thenSucceed();
    }

    /** With the economy off, an empty purse shuts nothing and a sale moves nothing. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "no_economy")
    public void withTheEconomyOffVillagersTradeAsVanillas(GameTestHelper h) {
        Yard.floor(h);
        var player = Yard.player(h, 10, 12, "oldtimer", new ItemStack(Items.WHEAT, 64));
        var v = trader(h, 10, 10, new MerchantOffer(new ItemCost(Items.WHEAT, 10), new ItemStack(Items.EMERALD), 16, 2, 0.05F));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            purse(v, 0);
            Yard.use(player, v);
            var menu = (MerchantMenu) player.containerMenu;
            h.assertFalse(v.getOffers().get(0).isOutOfStock(), "nothing shut");
            menu.setSelectionHint(0);
            menu.tryMoveItems(0);
            menu.clicked(2, 0, ClickType.QUICK_MOVE, player);
            h.assertTrue(count(player, Items.EMERALD) == 6, "six sales from an empty purse: " + count(player, Items.EMERALD));
            h.assertTrue(purse(v) == 0, "the purse untouched");
            player.closeContainer();
        }).thenSucceed();
    }

    // ---- purses -----------------------------------------------------------------------------

    /** Made new, a villager starts at the deposit line: hatched from an egg, born of two villagers,
     * cured of a zombie. Found in the world, it starts full: placed by a village's structure, or with no
     * purse at all as it joins (saved before purses). One that already had a purse keeps it. */
    @GameTest(template = "yard", timeoutTicks = 200, batch = "trade")
    public void aVillagerMadeNewStartsAtFourAndOneFoundInTheWorldFull(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var egg = EntityType.VILLAGER.spawn(level, Yard.at(h, 10, 1, 10), MobSpawnType.SPAWN_EGG);
        var mum = Yard.villager(h, 14, 10, VillagerProfession.FARMER, 2);
        var dad = Yard.villager(h, 16, 10, VillagerProfession.FARMER, 2);
        var placed = EntityType.VILLAGER.create(level);
        var at = Yard.at(h, 18, 1, 10);
        placed.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(placed, level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
        level.addFreshEntity(placed);
        var old = Yard.villager(h, 20, 10, VillagerProfession.FARMER, 2);
        var kept = EntityType.VILLAGER.create(level);
        at = Yard.at(h, 22, 1, 10);
        kept.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        purse(kept, 7);
        level.addFreshEntity(kept);
        var zombie = EntityType.ZOMBIE_VILLAGER.create(level);
        var cureAt = Yard.at(h, 30, 1, 10);
        zombie.moveTo(cureAt.getX() + 0.5, cureAt.getY(), cureAt.getZ() + 0.5);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);
        var born = new java.util.concurrent.atomic.AtomicReference<Villager>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var child = mum.getBreedOffspring(level, dad);
            child.moveTo(mum.getX(), mum.getY(), mum.getZ() + 2);
            level.addFreshEntity(child);
            born.set(child);
            // A cure under way: the zombie's saved conversion time, as a golden apple starts it.
            var tag = new net.minecraft.nbt.CompoundTag();
            zombie.saveWithoutId(tag);
            tag.putInt("ConversionTime", 2);
            zombie.load(tag);
        }).thenWaitUntil(() -> h.assertTrue(zombie.isRemoved(), "the zombie is cured"))
                .thenExecute(() -> {
                    h.assertTrue(purse(egg) == Purse.BELOW, "an egg's: at the line: " + purse(egg));
                    h.assertTrue(purse(born.get()) == Purse.BELOW, "a child's: at the line: " + purse(born.get()));
                    var cured = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(cureAt).inflate(2));
                    h.assertTrue(cured.size() == 1 && purse(cured.getFirst()) == Purse.BELOW, "a cured villager's: at the line: " + cured.stream().map(MarketGameTests::purse).toList());
                    h.assertTrue(purse(placed) == Purse.CAP, "a structure's: full: " + purse(placed));
                    h.assertTrue(purse(old) == Purse.CAP, "one with none as it joins: full: " + purse(old));
                    h.assertTrue(purse(kept) == 7, "a purse it had is kept: " + purse(kept));
                }).thenSucceed();
    }

    /** Hiring a level 2 farmer for 16 puts the 16 in its purse. */
    @GameTest(template = "yard", timeoutTicks = 60, batch = "trade")
    public void theHireFeeGoesIntoThePurse(GameTestHelper h) {
        Yard.floor(h);
        var farmer = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var player = Yard.player(h, 10, 12, "employer", new ItemStack(Items.EMERALD, 20));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            purse(farmer, 10);
            player.setShiftKeyDown(true);
            Yard.use(player, farmer);
            Yard.command(player, "serfdom hire");
            h.assertTrue(Workers.of(farmer).ownedBy(player.getUUID()), "hired");
            h.assertTrue(count(player, Items.EMERALD) == 4, "16 paid: " + count(player, Items.EMERALD));
            h.assertTrue(purse(farmer) == 26, "into its purse: " + purse(farmer));
            h.assertTrue(Screens.view(farmer).purse() == 26, "and its Worker Screen says so");
        }).thenSucceed();
    }

    /** In the morning, the first time each is seen: a free villager at 1 and a hired one at 1 get 2;
     * one at 4 gets nothing; a captive at 1 gets nothing; a child keeps no shopping time and gets
     * nothing. Seen again that morning, nothing more. A free farmer's household eats two of its three
     * loaves. The next morning the free one, at 3, gets 2 more, and its last loaf goes. */
    @GameTest(template = "yard", timeoutTicks = 800, batch = "morning")
    public void theMorningDepositPaysThePoorOnceAMorningAndTheHouseholdEats(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 2, 2, "lord");
        var free = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var hired = Yard.villager(h, 14, 10, VillagerProfession.MASON, 2);
        var rich = Yard.villager(h, 18, 10, VillagerProfession.FARMER, 2);
        var captive = Yard.villager(h, 22, 10, VillagerProfession.FARMER, 2);
        var child = Yard.villager(h, 26, 10, VillagerProfession.NONE, 1);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            child.setAge(-24000);
            Workers.hire(level, hired, owner, Optional.empty());
            Workers.capture(level, captive, owner, new ItemStack(Serfdom.CHAIN_LEAD.get()));
            captive.dropLeash(true, false);
            Workers.set(level, captive, Workers.of(captive).withCuffs(false));
            for (var v : List.of(free, hired, captive, child)) purse(v, 1);
            purse(rich, 4);
            Households.set(free, Household.EMPTY.add("minecraft:bread", 3));
        }).thenWaitUntil(() -> h.assertTrue(purse(free) == 3 && purse(hired) == 3, "the poor are paid: " + purse(free) + ", " + purse(hired)))
                .thenIdle(2 * 100 + 10)
                .thenExecute(() -> {
                    h.assertTrue(purse(free) == 3 && purse(hired) == 3, "once a morning");
                    h.assertTrue(purse(rich) == 4, "4 is not under the line");
                    h.assertTrue(purse(captive) == 1, "a captive gets nothing");
                    h.assertTrue(Purses.of(captive).purse().lastMorning() == Purse.day(level.getDayTime()), "though its morning is seen");
                    h.assertTrue(purse(child) == 1, "a child gets nothing");
                    h.assertTrue(Households.of(free).goods().equals(Map.of("minecraft:bread", 1)), "two loaves eaten: " + Households.of(free).goods());
                    level.setDayTime(level.getDayTime() + 24000);
                }).thenWaitUntil(() -> h.assertTrue(purse(free) == 5 && purse(hired) == 5, "the next morning, 3 is under 4: " + purse(free) + ", " + purse(hired)))
                .thenExecute(() -> {
                    h.assertTrue(Households.of(free).goods().isEmpty(), "the last loaf is gone: " + Households.of(free).goods());
                }).thenIdle(2 * 100 + 10).thenExecute(() -> h.assertTrue(purse(rich) == 4, "the rich one gets nothing: " + purse(rich))).thenSucceed();
    }

    // ---- a free villager's trip ---------------------------------------------------------------

    /** In its social time a free farmer with no food at home and five emeralds walks to a stall twenty
     * blocks off selling six bread for an emerald (bread's base value: a bargain), buys one sale (it
     * keeps four), pays, carries the bread home and puts it in its household; the stall's ledger says
     * so. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "town_bargain")
    public void aTownsmanBuysTheBreadHeNeedsAndCarriesItHome(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "baker");
        var bed = Yard.bed(h, 8, 8);
        var s = stall(h, 30, 8, owner, new ItemStack(Items.BREAD), 6, 1, new ItemStack(Items.BREAD, 32));
        var v = Yard.villager(h, 10, 12, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> townsman(h, v, bed, 5))
                .thenWaitUntil(() -> h.assertTrue(stock(s, Items.BREAD) == 26, "a sale of six: " + stock(s, Items.BREAD)))
                .thenExecute(() -> {
                    h.assertTrue(proceeds(s) == 1 && purse(v) == 4, "an emerald paid: " + proceeds(s) + ", " + purse(v));
                    var line = s.ledger().lines().getLast();
                    h.assertTrue(line.reaction() == Reaction.BARGAIN && line.items() == 6 && line.paid() == 1 && line.profession().equals("minecraft:farmer"), "the ledger: " + line);
                    h.assertTrue(Baskets.of(v).isPresent() && v.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.BREAD), "it carries the bread home, one in its hand");
                    h.assertTrue(Baskets.shown(v.getItemBySlot(EquipmentSlot.MAINHAND)), "the loaf in its hand is only shown");
                })
                .thenWaitUntil(() -> h.assertTrue(Households.of(v).goods().getOrDefault("minecraft:bread", 0) == 6, "put away: " + Households.of(v).goods()))
                .thenExecute(() -> {
                    h.assertTrue(Baskets.of(v).isEmpty() && v.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "its hands are empty");
                    h.assertTrue(Purses.of(v).day().sales() == 1, "one of its three sales today");
                }).thenSucceed();
    }

    /** One bread for an emerald, six times its worth: the farmer shakes its head and walks off; nothing
     * changes hands and the ledger says too pricey. */
    @GameTest(template = "yard", timeoutTicks = 1000, batch = "town_pricey")
    public void aTownsmanShakesHisHeadAtTooDearAPrice(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "gouger");
        var bed = Yard.bed(h, 8, 8);
        var s = stall(h, 24, 8, owner, new ItemStack(Items.BREAD), 1, 1, new ItemStack(Items.BREAD, 32));
        var v = Yard.villager(h, 10, 12, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> townsman(h, v, bed, 5))
                .thenWaitUntil(() -> h.assertFalse(s.ledger().lines().isEmpty(), "a visit"))
                .thenExecute(() -> {
                    h.assertTrue(s.ledger().lines().getLast().reaction() == Reaction.TOO_PRICEY, "too pricey: " + s.ledger().lines());
                    h.assertTrue(v.getUnhappyCounter() > 0, "it shakes its head");
                    h.assertTrue(stock(s, Items.BREAD) == 32 && proceeds(s) == 0 && purse(v) == 5, "nothing changes hands");
                    h.assertTrue(s.ledger().days().getLast().tooPricey() == 1, "the day's total");
                }).thenSucceed();
    }

    /** Twelve bread for two emeralds, with one emerald: it looks at its emerald and leaves. */
    @GameTest(template = "yard", timeoutTicks = 1000, batch = "town_broke")
    public void aTownsmanWhoCantAffordItLooksAtHisEmerald(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "wholesaler");
        var bed = Yard.bed(h, 8, 8);
        var s = stall(h, 24, 8, owner, new ItemStack(Items.BREAD), 12, 2, new ItemStack(Items.BREAD, 32));
        var v = Yard.villager(h, 10, 12, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> townsman(h, v, bed, 1))
                .thenWaitUntil(() -> h.assertFalse(s.ledger().lines().isEmpty(), "a visit"))
                .thenExecute(() -> {
                    h.assertTrue(s.ledger().lines().getLast().reaction() == Reaction.CANT_AFFORD, "can't afford: " + s.ledger().lines());
                    var hand = v.getItemBySlot(EquipmentSlot.MAINHAND);
                    h.assertTrue(hand.is(Items.EMERALD) && Baskets.shown(hand), "it looks at an emerald in its hand: " + hand);
                    h.assertTrue(stock(s, Items.BREAD) == 32 && purse(v) == 1, "nothing changes hands");
                })
                .thenWaitUntil(() -> h.assertTrue(v.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "and puts it away"))
                .thenSucceed();
    }

    /** Two stalls sell bread: four for an emerald near the bed, eight for one farther off. It buys the
     * cheaper; then, the lock: a stall serves one customer, a second waits, and one that held it past
     * its time is forgotten. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "town_cheaper")
    public void aTownsmanGoesToTheCheaperStallAndAStallServesOneAtATime(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "rival");
        var bed = Yard.bed(h, 8, 8);
        var near = stall(h, 12, 4, owner, new ItemStack(Items.BREAD), 4, 1, new ItemStack(Items.BREAD, 32));
        var far = stall(h, 34, 8, owner, new ItemStack(Items.BREAD), 8, 1, new ItemStack(Items.BREAD, 32));
        var v = Yard.villager(h, 10, 12, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            townsman(h, v, bed, 5);
            UUID a = UUID.randomUUID(), b = UUID.randomUUID();
            long t = h.getLevel().getGameTime();
            h.assertTrue(near.serve(a, t), "the first is served");
            h.assertFalse(near.serve(b, t + 1), "a second waits");
            h.assertTrue(near.busy(b, t + 1) && !near.busy(a, t + 1), "busy to the second, not to the first");
            h.assertTrue(near.serve(b, t + 401), "one held past its time is forgotten");
            near.letGo(b);
            h.assertFalse(near.busy(a, t + 402), "let go");
        }).thenWaitUntil(() -> h.assertTrue(stock(far, Items.BREAD) == 24, "the cheaper stall sold: " + stock(far, Items.BREAD)))
                .thenExecute(() -> h.assertTrue(stock(near, Items.BREAD) == 32 && near.ledger().lines().isEmpty(), "the dearer one never saw it"))
                .thenSucceed();
    }

    /** Two farmers short of food go to one stall at once: each is served in turn and each buys. */
    @GameTest(template = "yard", timeoutTicks = 1400, batch = "town_pair")
    public void twoTownsmenAtOneStallAreServedInTurn(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "grocer");
        var bedA = Yard.bed(h, 8, 8);
        var bedB = Yard.bed(h, 8, 14);
        var s = stall(h, 26, 10, owner, new ItemStack(Items.BREAD), 6, 1, new ItemStack(Items.BREAD, 32));
        var a = Yard.villager(h, 10, 9, VillagerProfession.FARMER, 2);
        var b = Yard.villager(h, 10, 15, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> { townsman(h, a, bedA, 3); townsman(h, b, bedB, 3); })
                .thenWaitUntil(() -> h.assertTrue(s.ledger().lines().size() == 2, "two visits: " + s.ledger().lines().size()))
                .thenExecute(() -> {
                    h.assertTrue(s.ledger().lines().stream().allMatch(l -> l.reaction() == Reaction.BARGAIN), "both bought: " + s.ledger().lines());
                    h.assertTrue(stock(s, Items.BREAD) == 20 && proceeds(s) == 2 && purse(a) == 2 && purse(b) == 2, "two sales");
                }).thenSucceed();
    }

    /** A stall 86 blocks from a hungry townsman's bed, beyond the 64 it shops within, never sees it. */
    @GameTest(template = "arena", timeoutTicks = 500, batch = "town_reach")
    public void aStallBeyondReachOfTheBedIsNeverVisited(GameTestHelper h) {
        clearOldStalls(h);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) { h.setBlock(x, 0, z, Blocks.GRASS_BLOCK); h.setBlock(56 + x, 0, 56 + z, Blocks.GRASS_BLOCK); }
        var owner = Yard.player(h, 60, 60, "hermit");
        var bed = Yard.bed(h, 2, 2);
        var s = stall(h, 62, 62, owner, new ItemStack(Items.BREAD), 6, 1, new ItemStack(Items.BREAD, 32));
        var v = Yard.villager(h, 4, 4, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            townsman(h, v, bed, 5);
            h.assertTrue(Math.sqrt(s.getBlockPos().distSqr(bed)) > 64, "beyond reach: " + Math.sqrt(s.getBlockPos().distSqr(bed)));
        }).thenIdle(400).thenExecute(() -> {
            // The decision itself: with nothing between, a trip planned to the far stall would never
            // arrive, and "never visited" alone would hold either way.
            h.assertTrue(com.chunkworks.serfdom.market.Shoppers.choose(h.getLevel(), v, com.chunkworks.serfdom.market.Needs.wants(h.getLevel(), v)).isEmpty(),
                    "no stall it may use is within reach of its bed");
            h.assertTrue(s.ledger().lines().isEmpty() && stock(s, Items.BREAD) == 32, "never visited");
            h.assertTrue(Purses.of(v).day().tripDay() == Purse.day(h.getLevel().getDayTime()), "though its trip was made: nothing in reach");
        }).thenSucceed();
    }

    /** In a village bought through Village Deed, a townsman passes a stranger's cheaper stall and buys
     * at the owner's. */
    @GameTest(template = "arena", timeoutTicks = 1200, batch = "town_deed")
    public void inABoughtVillageATownsmanShopsOnlyAtTheOwnersStall(GameTestHelper h) {
        clearOldStalls(h);
        var hut = Huts.plant(h, 0);
        var owner = Yard.player(h, 2, 2, "squire");
        var stranger = Yard.player(h, 4, 2, "pedlar");
        var bed = hut.at(2, 1, 2);
        var floor = hut.box().minY();
        h.getLevel().setBlock(bed.south(), Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        h.getLevel().setBlock(bed, Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
        var cheap = stall(h, hut.at(5, 1, 2), stranger, new ItemStack(Items.BREAD), 12, 1, new ItemStack(Items.BREAD, 32));
        var dear = stall(h, hut.at(5, 1, 5), owner, new ItemStack(Items.BREAD), 6, 1, new ItemStack(Items.BREAD, 32));
        var v = EntityType.VILLAGER.create(h.getLevel());
        var at = hut.at(3, 1, 4);
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
        v.setPersistenceRequired();
        h.getLevel().addFreshEntity(v);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var village = VillageProviders.at(h.getLevel(), hut.centre()).orElseThrow();
            h.assertTrue(Claims.get(h.getLevel()).claim(new Claims.Claim(village.id(), village.name(), Deed.of(owner.getUUID()), Map.of(owner.getUUID(), "squire"),
                    village.centre(), 15, h.getLevel().getServer().overworld().getGameTime())), "the squire holds the deed");
            h.assertTrue(floor < bed.getY(), "the bed stands on the hut's floor");
            townsman(h, v, bed, 5);
        }).thenWaitUntil(() -> h.assertTrue(stock(dear, Items.BREAD) == 26, "bought at the owner's: " + stock(dear, Items.BREAD)))
                .thenExecute(() -> h.assertTrue(stock(cheap, Items.BREAD) == 32 && cheap.ledger().lines().isEmpty(), "the stranger's, cheaper, passed by"))
                .thenSucceed();
    }

    // ---- workers ------------------------------------------------------------------------------

    /** At dinner a hungry worker with nothing at home and no canteen walks to a stall, buys bread, and
     * eats; a captive in the same place with emeralds never buys. */
    @GameTest(template = "yard", timeoutTicks = 1400, batch = "supper")
    public void aHungryWorkerBuysItsDinnerAndACaptiveCannot(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 2, 2, "employer");
        var bed = Yard.bed(h, 8, 8);
        Yard.chest(h, 10, 8);
        var capBed = Yard.bed(h, 30, 30);
        Yard.chest(h, 32, 30);
        var s = stall(h, 20, 8, owner, new ItemStack(Items.BREAD), 4, 1, new ItemStack(Items.BREAD, 32));
        var cs = stall(h, 40, 30, owner, new ItemStack(Items.BREAD), 4, 1, new ItemStack(Items.BREAD, 32));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FARMER, owner, bed, null);
        var captive = Yard.villager(h, 34, 34, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.capture(level, captive, owner, new ItemStack(Serfdom.CHAIN_LEAD.get()));
            captive.dropLeash(true, false);
            h.assertTrue(Workers.assignBed(level, captive, capBed) == Workers.Picked.OK, "the captive's bed");
            Workers.set(level, captive, Workers.of(captive).withCuffs(false));
            purse(worker, 5);
            purse(captive, 5);
            MealGameTests.hunger(worker, 8);
            MealGameTests.hunger(captive, 8);
        }).thenWaitUntil(() -> h.assertTrue(stock(s, Items.BREAD) < 32, "the worker bought bread: " + stock(s, Items.BREAD)))
                .thenWaitUntil(() -> h.assertTrue(MealGameTests.points(worker) > 8, "and ate it: " + MealGameTests.points(worker)))
                .thenExecute(() -> {
                    h.assertTrue(s.ledger().lines().getLast().reaction().bought(), "the ledger: " + s.ledger().lines());
                    h.assertTrue(purse(worker) < 5, "paid: " + purse(worker));
                    h.assertTrue(stock(cs, Items.BREAD) == 32 && cs.ledger().lines().isEmpty() && purse(captive) == 5, "the captive bought nothing");
                }).thenSucceed();
    }

    /** At its meeting a woodcutter whose shift found no axe, with emeralds, buys a stone axe at a stall
     * (its base value: a bargain) and it lands in its post's chest. */
    @GameTest(template = "yard", timeoutTicks = 1400, batch = "meeting")
    public void aWorkerWithoutAToolBuysOneForItsPost(GameTestHelper h) {
        clearOldStalls(h);
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "forester");
        var bed = Yard.bed(h, 8, 8);
        var post = Yard.post(h, 24, 24, owner, "woodcutting", 6);
        var chest = Yard.chest(h, 26, 24);
        var s = stall(h, 16, 8, owner, new ItemStack(Items.STONE_AXE), 1, 1, new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_AXE));
        var worker = Yard.worker(h, 10, 12, VillagerProfession.FLETCHER, owner, bed, post);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            purse(worker, 5);
            Workers.shiftNeed(worker, Optional.of(Need.NO_TOOL));
        }).thenWaitUntil(() -> h.assertTrue(stock(s, Items.STONE_AXE) == 1, "an axe bought: " + stock(s, Items.STONE_AXE)))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.STONE_AXE) == 1, "in its post's chest"))
                .thenExecute(() -> {
                    h.assertTrue(s.ledger().lines().getLast().reaction() == Reaction.BARGAIN, "a bargain: " + s.ledger().lines());
                    h.assertTrue(purse(worker) == 4, "an emerald paid: " + purse(worker));
                }).thenSucceed();
    }

    // ---- the stall ----------------------------------------------------------------------------

    /** A hopper above fills the stall with its bread and keeps its dirt; a hopper below takes its
     * emeralds; a stranger can neither open nor break it and its owner can, its stock and proceeds
     * dropping; an explosion beside it leaves it standing. */
    @GameTest(template = "yard", timeoutTicks = 400, batch = "trade")
    public void hoppersServeTheStallAndOnlyItsOwnerOpensOrBreaksIt(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "merchant");
        var stranger = Yard.player(h, 12, 12, "thief");
        var pos = Yard.at(h, 10, 2, 10);
        h.setBlock(10, 1, 10, Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN));
        var below = Yard.at(h, 10, 1, 10);
        var s = stall(h, pos, owner, new ItemStack(Items.BREAD), 4, 1);
        var above = pos.above();
        level.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING, net.minecraft.core.Direction.DOWN), 3);
        var hopper = (net.minecraft.world.Container) level.getBlockEntity(above);
        hopper.setItem(0, new ItemStack(Items.BREAD, 5));
        hopper.setItem(1, new ItemStack(Items.DIRT, 5));
        s.proceedsContainer.setItem(0, new ItemStack(Items.EMERALD, 3));
        var lone = stall(h, 30, 10, owner, new ItemStack(Items.BREAD), 4, 1, new ItemStack(Items.BREAD, 8));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(stock(s, Items.BREAD) == 5, "the bread goes in: " + stock(s, Items.BREAD)))
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, below, Items.EMERALD) == 3, "the emeralds come out below"))
                // A hopper pushes its slots in order, the bread first: time for it to try the dirt too.
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, above, Items.DIRT) == 5 && stock(s, Items.DIRT) == 0, "the dirt stays in the hopper");
                    Yard.click(stranger, s.getBlockPos());
                    h.assertFalse(stranger.containerMenu instanceof com.chunkworks.serfdom.market.ForSaleMenu, "a stranger can't open it");
                    Yard.click(owner, s.getBlockPos());
                    h.assertTrue(owner.containerMenu instanceof com.chunkworks.serfdom.market.ForSaleMenu, "its owner can");
                    owner.closeContainer();
                    stranger.gameMode.destroyBlock(lone.getBlockPos());
                    h.assertTrue(level.getBlockState(lone.getBlockPos()).is(Serfdom.FOR_SALE.get()), "a stranger can't break it");
                    var boom = Yard.at(h, 30, 1, 12);
                    level.explode(null, boom.getX() + 0.5, boom.getY(), boom.getZ() + 0.5, 3F, Level.ExplosionInteraction.BLOCK);
                    h.assertTrue(level.getBlockState(lone.getBlockPos()).is(Serfdom.FOR_SALE.get()), "an explosion leaves it");
                    h.assertTrue(level.getBlockState(Yard.at(h, 30, 0, 12)).isAir(), "though it took the ground beside it");
                    owner.gameMode.destroyBlock(lone.getBlockPos());
                    h.assertFalse(level.getBlockState(lone.getBlockPos()).is(Serfdom.FOR_SALE.get()), "its owner breaks it");
                    var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(lone.getBlockPos()).inflate(2));
                    h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.BREAD)).mapToInt(e -> e.getItem().getCount()).sum() == 8, "its stock drops");
                }).thenSucceed();
    }

    /** Base values: bread off the farmer's price list (six for one), a stone axe off the toolsmith's,
     * bone meal from data, a baked potato as food at bread's rate, dirt none. */
    @GameTest(template = "yard", timeoutTicks = 20, batch = "trade")
    public void baseValuesComeFromThePriceListsDataAndFood(GameTestHelper h) {
        var level = h.getLevel();
        h.assertTrue(Math.abs(Prices.value(level, new ItemStack(Items.BREAD)).orElseThrow() - 1 / 6.0) < 1e-9, "bread: " + Prices.value(level, new ItemStack(Items.BREAD)));
        h.assertTrue(Prices.value(level, new ItemStack(Items.STONE_AXE)).orElseThrow() == 1.0, "a stone axe: " + Prices.value(level, new ItemStack(Items.STONE_AXE)));
        h.assertTrue(Prices.value(level, new ItemStack(Items.BONE_MEAL)).orElseThrow() == 0.1, "bone meal");
        h.assertTrue(Math.abs(Prices.value(level, new ItemStack(Items.BAKED_POTATO)).orElseThrow() - 1 / 6.0) < 1e-9, "a baked potato fills as bread does");
        h.assertTrue(Prices.value(level, new ItemStack(Items.DIRT)).isEmpty(), "dirt: none");
        h.succeed();
    }

    /** A worker's screen shows its purse. */
    @GameTest(template = "yard", timeoutTicks = 40, batch = "trade")
    public void theWorkerScreenShowsThePurse(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "accountant");
        var v = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(h.getLevel(), v, owner, Optional.empty());
            purse(v, 17);
            h.assertTrue(Screens.view(v).purse() == 17, "17: " + Screens.view(v).purse());
        }).thenSucceed();
    }
}
