/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.serfdom.Captures;
import com.chunkworks.serfdom.Humming;
import com.chunkworks.serfdom.Remedies;
import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.Worker;
import com.chunkworks.serfdom.WorkerBrain;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Area;
import com.chunkworks.serfdom.domain.Bond;
import com.chunkworks.serfdom.domain.Remedy;
import com.chunkworks.serfdom.domain.WorkDay;
import com.chunkworks.serfdom.domain.WorkSong;
import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.villagedeed.Claims;
import com.chunkworks.villagedeed.api.VillageProviders;
import com.chunkworks.villagedeed.domain.Deed;
import com.chunkworks.villagelaw.Docket;
import com.chunkworks.villagelaw.Law;
import com.chunkworks.villagelaw.LawConfig;
import com.chunkworks.villagelaw.Summons;
import com.chunkworks.villagelaw.api.Cases;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 2a (D-0003), with Thief, Village Deed, Village Law, Guard
 * Villagers, Vanilla Wheels and the trailer loaded:
 * <ul>
 * <li>the hold: done at its time, let go early, stepped off, looked away; a nitwit and a child
 * refused;</li>
 * <li>the chain: a snap leaves the cuffs on; anyone takes a loose chain and lets go of one they
 * hold; only the owner takes the cuffs off; a villager in chains does not trade;</li>
 * <li>the law: a capture a guard saw is owed to the case and paying frees it and keeps the chain;
 * fleeing keeps the captive and a later debt frees nothing; a capture only villagers saw opens no
 * case; a capture outside a village is no crime; a bought village's owner takes freely, a
 * stranger not at all;</li>
 * <li>the captive's day and pace, golems and cats, a child of one owner's workers, a worker turned
 * zombie, setting free, the work song, the night's escape;</li>
 * <li>four captives in the trailer, a fifth refused, inside its body as it turns, out on a crouch
 * with the chain.</li>
 * </ul> */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class CaptiveGameTests {
    private static final int SETTLE = 5;

    @BeforeBatch(batch = "captives") public static void captives(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START - 1500); }
    @BeforeBatch(batch = "midnight") public static void midnight(ServerLevel level) { Yard.hour(level, 18000); }

    private static ItemStack chains(int n) { return new ItemStack(Serfdom.CHAIN_LEAD.get(), n); }
    private static void face(ServerPlayer p, Villager v) { p.lookAt(EntityAnchorArgument.Anchor.EYES, v.getEyePosition()); }
    private static int held(ServerPlayer p) { return p.getMainHandItem().is(Serfdom.CHAIN_LEAD.get()) ? p.getMainHandItem().getCount() : 0; }
    private static int carried(ServerPlayer p) { return Carried.count(p, Serfdom.CHAIN_LEAD.get()); }

    /** effects: a survival player at the world position, joined as Yard's are, carrying {@code holding}. */
    private static ServerPlayer playerAt(GameTestHelper h, BlockPos at, String name, ItemStack... holding) {
        var server = h.getLevel().getServer();
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
        };
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        for (int i = 0; i < holding.length; i++) player.getInventory().setItem(9 + i, holding[i]);
        return player;
    }

    /** effects: an adult villager standing still at the world position, at that trade and level. */
    private static Villager villagerAt(GameTestHelper h, BlockPos at, VillagerProfession profession, int level) {
        var v = EntityType.VILLAGER.create(h.getLevel());
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        v.setNoAi(true);
        v.setPersistenceRequired();
        v.setVillagerData(v.getVillagerData().setProfession(profession).setLevel(level));
        h.getLevel().addFreshEntity(v);
        return v;
    }

    /** effects: {@code v} with its record as {@code w} would be with the bond, home and cuffs given. */
    private static Worker remade(Worker w, Bond bond, Optional<Area> home, boolean cuffed, Optional<GlobalPos> taken) {
        return new Worker(w.owner(), w.ownerName(), w.bed(), w.post(), w.homeVillage(), w.tool(), bond, cuffed, taken, home, Worker.Night.NONE);
    }

    // ---- the hold -------------------------------------------------------------------------

    /** Held for a second and let go, a farmer stays free and no chain is used; held for the whole
     * hold, it is the taker's captive, cuffed on their chain, one chain used, walking at 0.9, with a
     * held villager's day and where it was taken kept. The key still held, the client's repeated
     * uses (with the chain, or empty-handed once the last chain is spent) leave the cuffs on (the
     * booth found the repeat taking them off); let go, a use takes them off as ever. */
    @GameTest(template = "yard", timeoutTicks = 200, batch = "captives")
    public void holdingTheChainTwoSecondsTakesAFreeVillagerAndLettingGoEarlyDoesNot(GameTestHelper h) {
        Yard.floor(h);
        var farmer = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        farmer.setNoAi(true);
        var taker = Yard.player(h, 10, 11, "taker");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            taker.setItemInHand(InteractionHand.MAIN_HAND, chains(2));
            face(taker, farmer);
            h.assertTrue(Yard.use(taker, farmer) == InteractionResult.SUCCESS, "the use is taken");
            h.assertTrue(taker.isUsingItem() && Captures.holding(taker, farmer), "the chain is held on the farmer");
        }).thenIdle(20).thenExecute(() -> {
            h.assertFalse(Workers.of(farmer).owned(), "not yet taken at one second");
            taker.stopUsingItem();
        }).thenIdle(2).thenExecute(() -> {
            h.assertFalse(Workers.of(farmer).owned(), "let go at one second: still free");
            h.assertFalse(Captures.holding(taker, farmer), "the hold is over");
            h.assertTrue(held(taker) == 2, "no chain used");
            face(taker, farmer);
            Yard.use(taker, farmer);
        }).thenIdle(SerfdomConfig.CAPTURE_TICKS.get() + 3).thenExecute(() -> {
            var w = Workers.of(farmer);
            h.assertTrue(w.ownedBy(taker.getUUID()) && w.captive() && w.cuffed(), "the taker's captive, in chains: " + w);
            h.assertTrue(farmer.getLeashHolder() == taker, "on the taker's chain");
            h.assertTrue(Workers.cuffed(farmer), "the chains are the synced flag too");
            h.assertTrue(held(taker) == 1, "one chain used: " + held(taker));
            h.assertFalse(Captures.holding(taker, farmer), "the hold is done");
            h.assertTrue(farmer.getBrain().getSchedule() == WorkerBrain.scheduleOf(w), "a held villager's day");
            h.assertTrue(farmer.getBrain().getSchedule().getActivityAt(5000) == Serfdom.HELD.get(), "held all day");
            h.assertTrue(w.takenFrom().map(g -> g.pos().equals(farmer.blockPosition())).orElse(false), "where it was taken is kept: " + w.takenFrom());
            double ratio = farmer.getAttributeValue(Attributes.MOVEMENT_SPEED) / farmer.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(Math.abs(ratio - 0.9) < 1e-6, "a captive walks at 0.9: " + ratio);
            Yard.use(taker, farmer);
            h.assertTrue(Workers.of(farmer).cuffed() && farmer.getLeashHolder() == taker, "the key still held: a repeated use leaves the cuffs on");
            taker.stopUsingItem();
            taker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }).thenIdle(4).thenExecute(() -> {
            Yard.use(taker, farmer);
            h.assertTrue(Workers.of(farmer).cuffed(), "the last chain spent, the empty-handed repeat leaves them on too");
        }).thenIdle(Captures.RELEASED + 2).thenExecute(() -> {
            Yard.use(taker, farmer);
            h.assertFalse(Workers.of(farmer).cuffed(), "let go, a use takes the cuffs off");
            h.assertTrue(carried(taker) == 1, "and the chain comes back");
        }).thenSucceed();
    }

    /** A nitwit and a child are refused, the chain not held; stepping three blocks off, or looking
     * away, ends a hold and the villager stays free. */
    @GameTest(template = "yard", timeoutTicks = 200, batch = "captives")
    public void onlyAGrownTradedVillagerIsTakenAndSteppingOffOrLookingAwayEndsTheHold(GameTestHelper h) {
        Yard.floor(h);
        var nitwit = Yard.villager(h, 10, 10, VillagerProfession.NITWIT, 1);
        var child = Yard.villager(h, 14, 10, VillagerProfession.NONE, 1);
        child.setAge(-24000);
        var mason = Yard.villager(h, 18, 10, VillagerProfession.MASON, 2);
        for (var v : List.of(nitwit, child, mason)) v.setNoAi(true);
        var taker = Yard.player(h, 10, 11, "picky");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            taker.setItemInHand(InteractionHand.MAIN_HAND, chains(3));
            for (var v : List.of(nitwit, child)) {
                var at = v.blockPosition().south();
                taker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                face(taker, v);
                Yard.use(taker, v);
                h.assertFalse(taker.isUsingItem() || Captures.holding(taker, v), "refused: " + v.getVillagerData().getProfession());
            }
            var at = mason.blockPosition().south();
            taker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            face(taker, mason);
            Yard.use(taker, mason);
            h.assertTrue(Captures.holding(taker, mason), "the mason's hold begins");
        }).thenIdle(10).thenExecute(() -> {
            var away = mason.blockPosition().south(3);
            taker.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
        }).thenIdle(2).thenExecute(() -> {
            h.assertFalse(Captures.holding(taker, mason) || taker.isUsingItem(), "three blocks off ends it");
            var at = mason.blockPosition().south();
            taker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            face(taker, mason);
            Yard.use(taker, mason);
            h.assertTrue(Captures.holding(taker, mason), "held again");
        }).thenIdle(10).thenExecute(() -> {
            // A player's view follows its head.
            taker.setYRot(taker.getYRot() + 180.0F);
            taker.setYHeadRot(taker.getYRot());
        }).thenIdle(2).thenExecute(() -> {
            h.assertFalse(Captures.holding(taker, mason), "looking away ends it");
        }).thenIdle(SerfdomConfig.CAPTURE_TICKS.get()).thenExecute(() -> {
            for (var v : List.of(nitwit, child, mason)) h.assertFalse(Workers.of(v).owned(), "free: " + v.getVillagerData().getProfession());
            h.assertTrue(held(taker) == 3, "no chain used");
        }).thenSucceed();
    }

    // ---- the chain ------------------------------------------------------------------------

    /** Past ten blocks the chain snaps and the captive stands in its cuffs, nothing dropped. Another
     * player takes the loose chain and lets go, and it stays cuffed; a villager in chains will not
     * trade (the use is taken before anyone's normal-priority listener); a chain someone else holds
     * is not broken; the owner takes the cuffs off and the chain comes back. */
    @GameTest(template = "yard", timeoutTicks = 400, batch = "captives")
    public void theChainsStayOnUntilTheOwnerTakesThemOff(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "keeper");
        var other = Yard.player(h, 12, 12, "passer");
        var farmer = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.capture(level, farmer, owner, chains(1));
            h.assertTrue(farmer.getLeashHolder() == owner && Workers.of(farmer).cuffed(), "taken and held");
            Yard.use(other, farmer);
            h.assertTrue(farmer.getLeashHolder() == owner, "someone else's hold is not broken");
            var far = Yard.at(h, 40, 1, 40);
            owner.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        }).thenWaitUntil(() -> h.assertFalse(farmer.isLeashed(), "snapped past ten blocks")).thenExecute(() -> {
            h.assertTrue(Workers.of(farmer).cuffed() && Workers.cuffed(farmer), "still in its cuffs");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(farmer.blockPosition()).inflate(6));
            h.assertTrue(drops.isEmpty(), "nothing dropped: " + drops);
            var near = farmer.blockPosition().south(2);
            other.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
            Yard.use(other, farmer);
            h.assertTrue(farmer.getLeashHolder() == other, "a passer takes the loose chain");
            Yard.use(other, farmer);
            h.assertFalse(farmer.isLeashed(), "and lets go");
            h.assertTrue(Workers.of(farmer).cuffed(), "a passer cannot take the cuffs off");
            int before = TestMod.reached(farmer.getUUID());
            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
            h.assertTrue(Yard.use(other, farmer) == InteractionResult.SUCCESS, "the use is taken");
            h.assertTrue(TestMod.reached(farmer.getUUID()) == before, "and goes no further: no trade");
            h.assertTrue(other.containerMenu == other.inventoryMenu, "no trade screen");
            var back = farmer.blockPosition().north(2);
            owner.moveTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
            Yard.use(owner, farmer);
            h.assertTrue(farmer.getLeashHolder() == owner, "the owner takes the chain");
            h.assertTrue(carried(owner) == 0, "the owner's chain is on the captive");
            Yard.use(owner, farmer);
            h.assertFalse(Workers.of(farmer).cuffed() || farmer.isLeashed() || Workers.cuffed(farmer), "the owner takes the cuffs off");
            h.assertTrue(carried(owner) == 1, "and has the chain back: " + carried(owner));
            h.assertTrue(farmer.getBrain().getSchedule() == WorkerBrain.scheduleOf(Workers.of(farmer)), "a captive's day out of chains");
            h.assertTrue(Workers.of(farmer).captive(), "still a captive");
        }).thenSucceed();
    }

    // ---- the law --------------------------------------------------------------------------

    /** A capture a guard saw: Thief's heavy crime, a case with the hut's village, the captive owed to
     * it; the guard never sets on the taker. Paying the fine (15 of 20 emeralds) frees the captive
     * where it stands, and the chain on it is not given back; the chestplate its taker put on it, it
     * keeps (D-0004). */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "captives")
    public void aCaptureAGuardSawIsOwedToTheCaseAndPayingFreesItAndKeepsTheChain(GameTestHelper h) {
        var hut = Huts.plant(h, 0);
        var guard = Huts.guard(h, hut.at(6, 1, 6));
        var victim = villagerAt(h, hut.at(2, 1, 2), VillagerProfession.FARMER, 2);
        var taker = playerAt(h, hut.at(2, 1, 3), "kidnapper", new ItemStack(Items.EMERALD, 20));
        h.onEachTick(() -> h.assertFalse(guard.getTarget() == taker, "the guard never sets on the taker"));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            taker.setItemInHand(InteractionHand.MAIN_HAND, chains(2));
            face(taker, victim);
            Yard.use(taker, victim);
        }).thenIdle(SerfdomConfig.CAPTURE_TICKS.get() + 3).thenExecute(() -> {
            h.assertTrue(Workers.of(victim).ownedBy(taker.getUUID()), "taken");
            h.assertTrue(Cases.openHere(taker).isPresent(), "the guard saw it: a case is open; " + sight(guard, taker, hut));
            var crimes = TestMod.crimes(taker.getUUID());
            h.assertTrue(crimes.size() == 1 && crimes.get(0).grade().equals("HEAVY") && crimes.get(0).witnesses() >= 1, "Thief's heavy crime, seen: " + crimes);
            var village = VillageProviders.at(h.getLevel(), hut.centre()).orElseThrow().id().toString();
            h.assertTrue(Cases.openHere(taker).map(Object::toString).equals(Optional.of(village)), "a case with the hut's village");
            var owed = Remedies.get(h.getLevel().getServer()).ledger().owedTo(new Remedy.Case(taker.getUUID(), village));
            h.assertTrue(owed.contains(victim.getUUID()), "the captive is owed to it: " + owed);
            EquipmentGameTests.dress(taker, victim, ItemStack.EMPTY, new ItemStack(Items.IRON_CHESTPLATE));
            h.assertTrue(Summons.use(taker, guard).isPresent(), "the guard serves the summons");
            Summons.answer(taker, new Summons.Answer(village, Summons.Choice.PAY));
            h.assertTrue(Carried.count(taker, Items.EMERALD) == 5, "the heavy fine of 15 is paid: " + Carried.count(taker, Items.EMERALD));
            h.assertFalse(Workers.of(victim).owned(), "the captive is free");
            h.assertFalse(victim.isLeashed() || Workers.cuffed(victim), "its chains off");
            h.assertTrue(held(taker) == 1 && carried(taker) == 1, "the chain on it is not given back: " + carried(taker));
            h.assertTrue(Remedies.get(h.getLevel().getServer()).ledger().owed().keySet().stream().noneMatch(c -> c.player().equals(taker.getUUID())),
                    "nothing owed to the taker's cases now (the ledger is the server's, shared by every test)");
            h.assertTrue(victim.getAttributeValue(Attributes.MOVEMENT_SPEED) == victim.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), "a free villager's pace");
            h.assertTrue(victim.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "freed by the law, it leaves wearing the chestplate (D-0004)");
            h.assertTrue(EquipmentGameTests.dropChance(victim, EquipmentSlot.CHEST) > 1.0F, "still marked to drop whole");
        }).thenSucceed();
    }

    /** Leaving instead: once out (banished, FLED), the case forgets the captive and it stays taken;
     * the debt paid when the banishment is over frees nothing. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "captives")
    public void fleeingKeepsTheCaptiveAndALaterDebtFreesNothing(GameTestHelper h) {
        var hut = Huts.plant(h, 0);
        var guard = Huts.guard(h, hut.at(6, 1, 6));
        var victim = villagerAt(h, hut.at(2, 1, 2), VillagerProfession.CLERIC, 2);
        var taker = playerAt(h, hut.at(2, 1, 3), "runaway", new ItemStack(Items.EMERALD, 20));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            taker.setItemInHand(InteractionHand.MAIN_HAND, chains(1));
            face(taker, victim);
            Yard.use(taker, victim);
        }).thenIdle(SerfdomConfig.CAPTURE_TICKS.get() + 3).thenExecute(() -> {
            var village = VillageProviders.at(h.getLevel(), hut.centre()).orElseThrow();
            h.assertTrue(Cases.openHere(taker).isPresent(), "the guard saw it: a case is open; " + sight(guard, taker, hut));
            h.assertTrue(Summons.use(taker, guard).isPresent(), "summoned");
            Summons.answer(taker, new Summons.Answer(village.id().toString(), Summons.Choice.LEAVE));
            var docket = Docket.get(h.getLevel().getServer());
            long now = h.getLevel().getServer().overworld().getGameTime();
            var fleeing = docket.get(taker.getUUID(), village.id()).orElseThrow();
            var banished = Law.move(taker, fleeing, fleeing.lawCase().ticked(now, false, LawConfig.banishTicks()));
            h.assertTrue(banished.lawCase().state() == com.chunkworks.villagelaw.domain.Case.State.BANISHED, "out in time: banished");
            var ledger = Remedies.get(h.getLevel().getServer()).ledger();
            h.assertTrue(ledger.owedTo(new Remedy.Case(taker.getUUID(), village.id().toString())).isEmpty(), "the case forgot the captive: " + ledger);
            h.assertTrue(Workers.of(victim).captive(), "it stays taken");
            var debt = Law.move(taker, banished, banished.lawCase().ticked(banished.lawCase().until(), false, LawConfig.banishTicks()));
            h.assertTrue(debt.lawCase().state() == com.chunkworks.villagelaw.domain.Case.State.DEBT, "a debt once the banishment is over");
            Summons.answer(taker, new Summons.Answer(village.id().toString(), Summons.Choice.PAY));
            h.assertTrue(docket.get(taker.getUUID(), village.id()).isEmpty(), "the debt is paid");
            h.assertTrue(Workers.of(victim).ownedBy(taker.getUUID()) && Workers.of(victim).cuffed(), "and frees nothing");
        }).thenSucceed();
    }

    /** Only a villager saw it: Thief's crime and its reputation hit, and no case, so nothing is
     * owed. Outside any village no crime is committed at all. */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "captives")
    public void aCaptureOnlyVillagersSawOpensNoCaseAndOneOutsideAVillageIsNoCrime(GameTestHelper h) {
        var hut = Huts.plant(h, 0);
        var victim = villagerAt(h, hut.at(2, 1, 2), VillagerProfession.FARMER, 2);
        var bystander = villagerAt(h, hut.at(5, 1, 5), VillagerProfession.NONE, 1);
        var taker = playerAt(h, hut.at(2, 1, 3), "quiet");
        var outside = new BlockPos(hut.box().minX() - 12, hut.box().minY() + 1, hut.box().minZ() - 12);
        for (int dz = -1; dz <= 2; dz++) h.getLevel().setBlock(outside.offset(0, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        var loner = villagerAt(h, outside, VillagerProfession.FARMER, 2);
        var wanderer = playerAt(h, outside.south(), "wanderer");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            h.assertTrue(VillageProviders.at(h.getLevel(), loner.blockPosition()).isEmpty(), "the loner stands outside the hut");
            Workers.capture(h.getLevel(), victim, taker, chains(1));
            var crimes = TestMod.crimes(taker.getUUID());
            h.assertTrue(crimes.size() == 1 && crimes.get(0).grade().equals("HEAVY"), "Thief's heavy crime: " + crimes);
            h.assertTrue(bystander.getPlayerReputation(taker) == -150, "the bystander's reputation hit: " + bystander.getPlayerReputation(taker));
            h.assertTrue(Cases.openHere(taker).isEmpty(), "no case");
            h.assertTrue(Remedies.get(h.getLevel().getServer()).ledger().owed().keySet().stream().noneMatch(c -> c.player().equals(taker.getUUID())), "nothing owed");
            Workers.capture(h.getLevel(), loner, wanderer, chains(1));
            h.assertTrue(Workers.of(loner).captive(), "the loner is taken");
            h.assertTrue(TestMod.crimes(wanderer.getUUID()).isEmpty(), "outside the village's structure: no crime");
            h.assertTrue(Workers.of(loner).home().isEmpty(), "taken 17 blocks from the hut, past the law's 16, it knows no village: " + Workers.of(loner).home());
        }).thenSucceed();
    }

    /** In a bought village, a stranger's chain is refused; the owner takes one of its people with a
     * guard watching and commits no crime: no case, nothing owed (Rusty's call, D-0003). */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "captives")
    public void aBoughtVillagesOwnerTakesItsPeopleFreelyAndAStrangerNotAtAll(GameTestHelper h) {
        var hut = Huts.plant(h, 0);
        Huts.guard(h, hut.at(6, 1, 6));
        var one = villagerAt(h, hut.at(2, 1, 2), VillagerProfession.FARMER, 2);
        var two = villagerAt(h, hut.at(5, 1, 2), VillagerProfession.FARMER, 2);
        var owner = playerAt(h, hut.at(2, 1, 3), "deedholder");
        var stranger = playerAt(h, hut.at(5, 1, 3), "stranger");
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var v = VillageProviders.at(h.getLevel(), hut.centre()).orElseThrow();
            h.assertTrue(Claims.get(h.getLevel()).claim(new Claims.Claim(v.id(), v.name(), Deed.of(owner.getUUID()), Map.of(owner.getUUID(), "deedholder"), v.centre(), 15,
                    h.getLevel().getServer().overworld().getGameTime())), "the owner holds the deed");
            stranger.setItemInHand(InteractionHand.MAIN_HAND, chains(1));
            face(stranger, two);
            Yard.use(stranger, two);
            h.assertFalse(stranger.isUsingItem() || Captures.holding(stranger, two), "the stranger's chain is refused");
            owner.setItemInHand(InteractionHand.MAIN_HAND, chains(1));
            face(owner, one);
            Yard.use(owner, one);
            h.assertTrue(Captures.holding(owner, one), "the owner's hold begins");
        }).thenIdle(SerfdomConfig.CAPTURE_TICKS.get() + 3).thenExecute(() -> {
            h.assertTrue(Workers.of(one).ownedBy(owner.getUUID()), "the owner took it");
            h.assertTrue(TestMod.crimes(owner.getUUID()).isEmpty(), "no crime");
            h.assertTrue(Cases.openHere(owner).isEmpty(), "no case");
            h.assertFalse(Workers.of(two).owned(), "the stranger took nobody");
        }).thenSucceed();
    }

    // ---- the captive's life ---------------------------------------------------------------

    /** At 9000 a hired worker meets and a captive at the same post works; a captive's idle hours are
     * standing about and its nights asleep; a captive farmer farms at 1.25 × 0.9, a hired one at 1.25. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "captives")
    public void aCaptiveWorksThroughTheMeetingAndWorksSlower(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 5, 5, "overseer");
        var post = Yard.post(h, 20, 10, owner, "farming", 8);
        var hired = Yard.worker(h, 18, 14, VillagerProfession.FARMER, owner, Yard.bed(h, 16, 16), post);
        var captive = Yard.villager(h, 22, 14, VillagerProfession.FARMER, 2);
        var bed = Yard.bed(h, 24, 16);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.capture(level, captive, owner, chains(1));
            Yard.use(owner, captive);
            h.assertFalse(Workers.of(captive).cuffed(), "out of chains");
            h.assertTrue(Workers.assignBed(level, captive, bed) == Workers.Picked.OK, "a bed");
            h.assertTrue(Workers.link(level, captive, owner, post.getBlockPos()) == Workers.Picked.OK, "the post");
            var mine = captive.getBrain().getSchedule();
            var theirs = hired.getBrain().getSchedule();
            h.assertTrue(theirs.getActivityAt(9000) == Activity.MEET, "the hired worker meets at 9000");
            h.assertTrue(mine.getActivityAt(9000) == Serfdom.WORK.get(), "the captive works at 9000");
            h.assertTrue(mine.getActivityAt(3000) == Serfdom.WORK.get(), "and at 3000");
            h.assertTrue(mine.getActivityAt(10500) == Serfdom.STAY.get() && mine.getActivityAt(1000) == Serfdom.STAY.get(), "idle in place");
            h.assertTrue(mine.getActivityAt(13000) == Activity.REST, "asleep at night");
            var job = Jobs.get(Serfdom.id("farming")).orElseThrow();
            h.assertTrue(Math.abs(Jobs.speed(job, captive) - 1.125) < 1e-9, "a captive farmer: " + Jobs.speed(job, captive));
            h.assertTrue(Math.abs(Jobs.speed(job, hired) - 1.25) < 1e-9, "a hired one: " + Jobs.speed(job, hired));
        }).thenSucceed();
    }

    /** Five owned villagers asleep lately in five beds: hired, each wants a golem and their beds bring
     * a cat; made captives, none wants a golem and their beds bring none (Rusty's call, D-0003). */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "captives")
    public void captivesWantNoGolemAndTheirBedsBringNoCat(GameTestHelper h) throws Exception {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 5, 5, "catless");
        var five = new ArrayList<Villager>();
        for (int i = 0; i < 5; i++) {
            var v = Yard.villager(h, 10 + 3 * i, 12, VillagerProfession.FARMER, 2);
            v.setNoAi(true);
            Workers.hire(level, v, owner, Optional.empty());
            h.assertTrue(Workers.assignBed(level, v, Yard.bed(h, 10 + 3 * i, 16)) == Workers.Picked.OK, "a bed");
            five.add(v);
        }
        var spawner = new net.minecraft.world.entity.npc.CatSpawner();
        var spawnInVillage = net.minecraft.world.entity.npc.CatSpawner.class.getDeclaredMethod("spawnInVillage", ServerLevel.class, BlockPos.class);
        spawnInVillage.setAccessible(true);
        var middle = Yard.at(h, 16, 1, 14);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            long now = level.getGameTime();
            for (var v : five) {
                v.getBrain().setMemory(MemoryModuleType.LAST_SLEPT, now);
                h.assertTrue(v.wantsToSpawnGolem(now), "a hired worker who slept wants a golem");
            }
            try {
                h.assertTrue((int) spawnInVillage.invoke(spawner, level, middle) == 1, "five hired beds bring a cat");
                for (var cat : level.getEntitiesOfClass(Cat.class, new AABB(middle).inflate(60))) cat.discard();
                for (var v : five) {
                    var w = Workers.of(v);
                    Workers.set(level, v, remade(w, Bond.CAPTIVE, Optional.empty(), false, Optional.of(GlobalPos.of(level.dimension(), v.blockPosition()))));
                    h.assertFalse(v.wantsToSpawnGolem(now), "a captive wants no golem");
                }
                h.assertTrue(Workers.captiveBedsNear(level, middle, 48) == 5, "five captives' beds");
                h.assertTrue((int) spawnInVillage.invoke(spawner, level, middle) == 0, "five captives' beds bring no cat");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }).thenSucceed();
    }

    /** A child of two of one owner's workers is the owner's, hired, keeps the bed it is born into
     * and cannot take a post; a child of a worker and another owner's is free. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "captives")
    public void aChildOfOneOwnersWorkersIsTheirsAndTakesNoPost(GameTestHelper h) throws Exception {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 5, 5, "parent-owner");
        var neighbour = Yard.player(h, 7, 5, "neighbour");
        var mother = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var father = Yard.villager(h, 12, 10, VillagerProfession.MASON, 2);
        var stranger = Yard.villager(h, 14, 10, VillagerProfession.CLERIC, 2);
        var post = Yard.post(h, 20, 10, owner, "farming", 8);
        var bed = Yard.bed(h, 10, 16);
        var giveBed = net.minecraft.world.entity.ai.behavior.VillagerMakeLove.class.getDeclaredMethod("giveBedToChild", ServerLevel.class, Villager.class, BlockPos.class);
        giveBed.setAccessible(true);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, mother, owner, Optional.empty());
            Workers.hire(level, father, owner, Optional.empty());
            Workers.hire(level, stranger, neighbour, Optional.empty());
            var child = mother.getBreedOffspring(level, father);
            h.assertTrue(child != null && Workers.of(child).ownedBy(owner.getUUID()) && !Workers.of(child).captive(), "the owner's, hired");
            var mixed = mother.getBreedOffspring(level, stranger);
            h.assertFalse(Workers.of(mixed).owned(), "a child of two owners' workers is free");
            child.setAge(-24000);
            child.moveTo(Vec3.atBottomCenterOf(Yard.at(h, 10, 1, 12)));
            level.addFreshEntity(child);
            try {
                giveBed.invoke(new net.minecraft.world.entity.ai.behavior.VillagerMakeLove(), level, child, bed);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            h.assertTrue(Workers.of(child).bed().map(b -> b.pos().equals(bed)).orElse(false), "the bed it is born into is its own: " + Workers.of(child).bed());
            h.assertTrue(Workers.link(level, child, owner, post.getBlockPos()) == Workers.Picked.CHILD, "a child takes no post");
        }).thenSucceed();
    }

    /** A worker turned into a zombie villager, in chains, with an axe and logs, wearing a helmet and
     * leggings: the axe, the logs, the chain and both pieces drop (the game would delete what it
     * wore, D-0004), the zombie villager wears nothing and is nobody's. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "captives")
    public void aWorkerTurnedZombieDropsItsThings(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 5, 5, "bereft");
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FLETCHER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, worker, owner, Optional.empty());
            Workers.set(level, worker, Workers.of(worker).withTool(new ItemStack(Items.IRON_AXE)).withCuffs(true));
            worker.getInventory().addItem(new ItemStack(Items.OAK_LOG, 5));
            EquipmentGameTests.dress(owner, worker, new ItemStack(Items.IRON_HELMET), ItemStack.EMPTY, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            var where = worker.blockPosition();
            var zombie = worker.convertTo(EntityType.ZOMBIE_VILLAGER, false);
            h.assertTrue(zombie != null, "converted");
            EventHooks.onLivingConvert(worker, zombie);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3));
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.IRON_AXE)).count() == 1, "the axe: " + drops);
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum() == 5, "the logs: " + drops);
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Serfdom.CHAIN_LEAD.get())).count() == 1, "the chain: " + drops);
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.IRON_HELMET)).count() == 1, "the helmet: " + drops);
            h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.CHAINMAIL_LEGGINGS)).count() == 1, "the leggings: " + drops);
            for (var slot : com.chunkworks.serfdom.WorkerMenu.WORN) h.assertTrue(zombie.getItemBySlot(slot).isEmpty(), "the zombie villager wears nothing on its " + slot);
            h.assertTrue(zombie.getExistingData(Serfdom.WORKER).isEmpty(), "the zombie villager is nobody's");
        }).thenSucceed();
    }

    /** Set free from the Worker Screen: a captive in chains goes free, its owner's chain comes back
     * to them, it walks at a free villager's pace, and it takes off its helmet and boots and drops
     * them where it stands (D-0004). */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "captives")
    public void settingACaptiveFreeLetsItGoAndGivesTheOwnersChainBack(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "liberator");
        var captive = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.capture(level, captive, owner, chains(1));
            h.assertTrue(carried(owner) == 0, "the chain is on the captive");
            EquipmentGameTests.dress(owner, captive, new ItemStack(Items.GOLDEN_HELMET), ItemStack.EMPTY, ItemStack.EMPTY, new ItemStack(Items.LEATHER_BOOTS));
            Screens.pressed(owner, new Screens.WorkerAction(captive.getId(), Screens.WorkerButton.SET_FREE));
            h.assertFalse(Workers.of(captive).owned() || captive.isLeashed() || Workers.cuffed(captive), "free, chains off");
            h.assertTrue(carried(owner) == 1, "the owner's chain is back");
            h.assertTrue(captive.getAttributeValue(Attributes.MOVEMENT_SPEED) == captive.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), "a free villager's pace");
            for (var slot : com.chunkworks.serfdom.WorkerMenu.WORN) h.assertTrue(captive.getItemBySlot(slot).isEmpty(), "it wears nothing on its " + slot);
            var drops = EquipmentGameTests.dropsNear(h, captive);
            h.assertTrue(EquipmentGameTests.count(drops, Items.GOLDEN_HELMET) == 1 && EquipmentGameTests.count(drops, Items.LEATHER_BOOTS) == 1, "the helmet and boots lie where it stood: " + drops);
        }).thenSucceed();
    }

    /** A captive at work sings whole phrases of the work song, note by note in order, each a cue of
     * one of the phrases; a hired worker never does, nor a captive that only walks. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "captives")
    public void aCaptiveAtWorkHumsTheWorkSongAndAHiredWorkerDoesNot(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 5, 5, "listener");
        var captive = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var hired = Yard.villager(h, 20, 10, VillagerProfession.FARMER, 2);
        var walker = Yard.villager(h, 30, 10, VillagerProfession.FARMER, 2);
        for (var v : List.of(captive, hired, walker)) v.setNoAi(true);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.capture(level, captive, owner, chains(1));
            Workers.capture(level, walker, owner, chains(1));
            Workers.hire(level, hired, owner, Optional.empty());
            long t0 = 1_000_000L;
            for (long now = t0; now < t0 + 6000; now++) {
                Humming.tick(level, captive, now, true);
                Humming.tick(level, hired, now, true);
                Humming.tick(level, walker, now, false);
            }
            var notes = TestMod.heard(Serfdom.HUM.get(), new Vec3(captive.getX(), captive.getEyeY(), captive.getZ()));
            h.assertTrue(notes.size() >= 6, "the captive sang: " + notes.size() + " notes");
            var phrase = WorkSong.PHRASES.stream().map(WorkSong::cues).filter(c -> c.size() <= notes.size() && matches(c, notes)).findFirst();
            h.assertTrue(phrase.isPresent(), "its first notes are a phrase in order: " + notes.stream().map(TestMod.Heard::pitch).toList());
            h.assertTrue(TestMod.heard(Serfdom.HUM.get(), new Vec3(hired.getX(), hired.getEyeY(), hired.getZ())).isEmpty(), "the hired worker never hums");
            h.assertTrue(TestMod.heard(Serfdom.HUM.get(), new Vec3(walker.getX(), walker.getEyeY(), walker.getZ())).isEmpty(), "a captive that only walks begins nothing");
        }).thenSucceed();
    }

    /** effects: where the guard stands against the hut and the taker, for a failure's message. */
    private static String sight(net.minecraft.world.entity.LivingEntity guard, ServerPlayer taker, Huts.Hut hut) {
        return "guard at " + guard.blockPosition().subtract(hut.at(0, 0, 0)).toShortString() + " in the hut, " + String.format("%.1f", guard.distanceTo(taker))
                + " from the taker, line of sight " + guard.hasLineOfSight(taker) + ", alive " + guard.isAlive();
    }

    private static boolean matches(List<WorkSong.Cue> cues, List<TestMod.Heard> notes) {
        for (int i = 0; i < cues.size(); i++) if (Math.abs(cues.get(i).pitch() - notes.get(i).pitch()) > 1e-4) return false;
        return true;
    }

    /** At midnight, with the escape certain: two captives asleep in their beds roll, get up when the
     * roll said and walk home. One reaches the village it was taken from and is free, still wearing
     * the helmet its owner gave it, still marked to drop whole (D-0004); the other is cuffed by a
     * passer on the way, which ends its escape, and stays a captive. */
    @GameTest(template = "yard", timeoutTicks = 2400, batch = "midnight")
    public void aCaptiveSlipsHomeAtNightAndAChainOnTheWayEndsIt(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 2, 44, "sleeper-owner");
        var catcher = Yard.player(h, 2, 40, "catcher");
        var runner = Yard.villager(h, 4, 10, VillagerProfession.FARMER, 2);
        var caught = Yard.villager(h, 4, 20, VillagerProfession.FARMER, 2);
        var a = Yard.at(h, 30, 0, 4);
        var b = Yard.at(h, 40, 4, 26);
        var home = new Area(level.dimension().location().toString(), a.getX(), a.getY() - 4, a.getZ(), b.getX(), b.getY() + 4, b.getZ());
        double chance = SerfdomConfig.ESCAPE_CHANCE.get();
        // Where the runner stood on its last tick as a captive: where its walk home was judged, before
        // the free villager it became walked on.
        var lastTaken = new BlockPos[1];
        h.onEachTick(() -> { if (Workers.of(runner).owned()) lastTaken[0] = runner.blockPosition(); });
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            SerfdomConfig.ESCAPE_CHANCE.set(1.0);
            for (var v : List.of(runner, caught)) {
                Workers.capture(level, v, owner, chains(1));
                v.dropLeash(true, false);
                var w = Workers.of(v);
                Workers.set(level, v, remade(w, Bond.CAPTIVE, Optional.of(home), false, w.takenFrom()));
                var bed = Yard.bed(h, v == runner ? 4 : 6, v == runner ? 12 : 22);
                h.assertTrue(Workers.assignBed(level, v, bed) == Workers.Picked.OK, "a bed");
                v.startSleeping(bed);
            }
            EquipmentGameTests.dress(owner, runner, new ItemStack(Items.IRON_HELMET));
        }).thenWaitUntil(() -> {
            for (var v : List.of(runner, caught)) h.assertTrue(Workers.of(v).night().getUpAt().isPresent(), "it rolled and means to go");
        }).thenExecute(() -> level.setDayTime(Math.floorDiv(level.getDayTime(), 24000L) * 24000L + 19999))
                .thenWaitUntil(() -> {
                    for (var v : List.of(runner, caught)) h.assertTrue(Workers.of(v).escaping(), "it got up and is on its way");
                }).thenWaitUntil(() -> h.assertTrue(caught.position().distanceTo(Vec3.atBottomCenterOf(Yard.at(h, 4, 1, 20))) > 3, "the other is off"))
                .thenExecute(() -> {
                    var by = caught.blockPosition().south();
                    catcher.moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
                    catcher.setItemInHand(InteractionHand.MAIN_HAND, chains(1));
                    Yard.use(catcher, caught);
                    h.assertTrue(Workers.of(caught).cuffed() && !Workers.of(caught).escaping(), "cuffed on the way, the escape is over");
                }).thenWaitUntil(() -> h.assertFalse(Workers.of(runner).owned(), "the runner is home and free"))
                .thenExecute(() -> {
                    h.assertTrue(home.coversColumn(Worker.spot(GlobalPos.of(level.dimension(), lastTaken[0]))), "freed inside its village: " + lastTaken[0]);
                    h.assertTrue(runner.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "it escaped wearing the helmet");
                    h.assertTrue(EquipmentGameTests.dropChance(runner, EquipmentSlot.HEAD) > 1.0F, "still marked to drop whole");
                    h.assertTrue(Workers.of(caught).ownedBy(owner.getUUID()) && Workers.of(caught).captive(), "the caught one is still the owner's captive");
                    SerfdomConfig.ESCAPE_CHANCE.set(chance);
                }).thenSucceed();
    }

    // ---- the trailer ----------------------------------------------------------------------

    /** Five captives on their owner's chains at the trailer's open doors: an empty-handed click
     * boards the four nearest, which stand inside the trailer's body as it turns, and the fifth stays
     * on its chain; a crouch with a chain at the doors lets them out behind, still cuffed, nobody
     * holding them. */
    @GameTest(template = "yard", timeoutTicks = 200, batch = "captives")
    public void fourCaptivesRideInTheTrailerAndAChainLetsThemOut(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var trailer = com.chunkworks.vanillawheels.Vehicle.create(level, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trailer", "trailer"),
                Vec3.atBottomCenterOf(Yard.at(h, 24, 1, 24)), -90.0F);
        h.assertTrue(trailer != null, "the trailer's profile is loaded");
        level.addFreshEntity(trailer);
        var owner = Yard.player(h, 19, 24, "drover");
        var five = new ArrayList<Villager>();
        for (int i = 0; i < 5; i++) five.add(Yard.villager(h, 17 - i, 22 + (i % 3) * 2, VillagerProfession.FARMER, 2));
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            for (var v : five) Workers.capture(level, v, owner, chains(1));
            trailer.toggleDoors();
            h.assertTrue(trailer.doorsOpen(), "the doors are open");
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            h.assertTrue(trailer.interact(owner, InteractionHand.MAIN_HAND).consumesAction(), "an empty-handed click is a load");
            h.assertTrue(trailer.cargoAboard().size() == 4, "four aboard: " + trailer.cargoAboard().size());
            var left = five.stream().filter(v -> v.getVehicle() == null).toList();
            h.assertTrue(left.size() == 1 && left.get(0).getLeashHolder() == owner, "the fifth stays on its chain");
            for (var v : trailer.cargoAboard()) h.assertTrue(Workers.of((Villager) v).cuffed() && !((Villager) v).isLeashed(), "aboard in chains, nobody holding them");
        }).thenIdle(3).thenExecute(() -> inside(h, trailer)).thenExecute(() -> trailer.setYRot(-45.0F)).thenIdle(3).thenExecute(() -> {
            inside(h, trailer);
            owner.setShiftKeyDown(true);
            owner.setItemInHand(InteractionHand.MAIN_HAND, chains(1));
            var door = trailer.rotate(trailer.profile().localBlocks(trailer.profile().doors().get(0).hinge()));
            h.assertTrue(trailer.interactAt(owner, door, InteractionHand.MAIN_HAND).consumesAction(), "the crouch with the chain is taken");
            h.assertTrue(trailer.cargoAboard().isEmpty(), "everyone is out");
            for (var v : five) h.assertTrue(Workers.of(v).cuffed(), "still in chains");
        }).thenSucceed();
    }

    /** effects: asserts every captive aboard stands inside the trailer's body: its box, turned into
     * the trailer's frame, within the body's width and length, and its head under the roof. */
    private static void inside(GameTestHelper h, com.chunkworks.vanillawheels.Vehicle trailer) {
        var body = trailer.profile().body();
        float yaw = (float) Math.toRadians(trailer.getYRot());
        for (var e : trailer.cargoAboard()) {
            var d = e.position().subtract(trailer.position());
            // Into the body's frame (+Z forward, +X left): Vehicle.rotate turns the body's frame into
            // the world's by yRot(-yaw), so the world's turns back by yRot(yaw).
            var local = d.yRot(yaw);
            double lx = local.x, lz = local.z;
            double half = e.getBbWidth() / 2.0;
            h.assertTrue(Math.abs(lx) + half <= body.width() / 2.0, "across the body: " + lx + " of " + body.width());
            h.assertTrue(Math.abs(lz) + half <= body.length() / 2.0, "along it: " + lz + " of " + body.length());
            h.assertTrue(d.y >= 0 && d.y + e.getBbHeight() <= body.height(), "under the roof: " + d.y + " + " + e.getBbHeight() + " of " + body.height());
        }
    }
}
