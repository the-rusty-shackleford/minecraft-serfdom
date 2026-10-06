/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.defence.Defenders;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 5 (D-0007), with villagers' brains running and the hour held. A
 * raid is started as vanilla starts one (a player with the Raid Omen at a base whose beds are taken),
 * and its raiders joined to it by hand, which holds its own waves back while they live; each test
 * stops its raid. Each stands alone in its batch: raids within 96 blocks are one raid.
 * <ul>
 * <li>melee: a worker takes the better of two swords from its home chest, kills a raider, and after
 * the raid the sword goes back, its helmet staying on; nobody trades with it meanwhile; a live
 * vindicator against a worker in diamond, which never panics;</li>
 * <li>a bow: arrows from the chest spent on a raider, the bow, sword and the rest put back;</li>
 * <li>out of arrows: the sword drawn;</li>
 * <li>a gun: a Ranged Weapons Mod pistol loaded with rounds from the chest, fired, put back;</li>
 * <li>nothing to fight with (a bow without arrows, a launcher with rockets): nothing taken, it hides;</li>
 * <li>who: a captive arms, a free villager beside it doesn't; a zombie at night arms nobody;</li>
 * <li>a friend in the line: no shot until it steps aside;</li>
 * <li>a defender killed drops what it took.</li>
 * </ul> */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class DefenceGameTests {
    private static final int SETTLE = 5;

    static void day(ServerLevel level) { Yard.hour(level, 6000); }
    @BeforeBatch(batch = "defence_sword") public static void sword(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_live") public static void live(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_bow") public static void bow(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_switch") public static void swap(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_gun") public static void gun(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_none") public static void none(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_who") public static void who(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_friend") public static void friend(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_death") public static void death(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_zombie") public static void zombie(ServerLevel level) { Yard.hour(level, 18000); }
    @BeforeBatch(batch = "defence_freed") public static void freed(ServerLevel level) { day(level); }
    @BeforeBatch(batch = "defence_hid") public static void hid(ServerLevel level) { day(level); }

    /** The raids these tests started, so none outlives a failed test. */
    private static final java.util.Set<Raid> STARTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** effects: every raid these tests started stopped, its raiders gone. */
    static void endAll() {
        for (var r : STARTED) end(r);
        STARTED.clear();
    }

    // ---- fixtures ---------------------------------------------------------------------------

    /** effects: a raid at {@code at}, as a player carrying the Raid Omen there starts one. */
    static Raid raid(GameTestHelper h, ServerPlayer player, BlockPos at) {
        player.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 600, 0));
        var raid = h.getLevel().getRaids().createOrExtendRaid(player, at);
        h.assertTrue(raid != null, "a raid starts at the base");
        STARTED.add(raid);
        return raid;
    }

    /** effects: a raider of {@code type} joined to the raid at the yard position; frozen when
     * {@code still}. */
    static <T extends Raider> T raider(GameTestHelper h, Raid raid, EntityType<T> type, int x, int z, boolean still) {
        var r = type.create(h.getLevel());
        if (still) r.setNoAi(true);
        raid.joinRaid(1, r, Yard.at(h, x, 0, z), false);
        return r;
    }

    static Item item(String id) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        if (item == Items.AIR) throw new IllegalStateException("no " + id);
        return item;
    }

    static boolean defending(Villager v) { return v.getBrain().isActive(Serfdom.DEFEND.get()); }
    static ItemStack hand(Villager v) { return v.getItemBySlot(EquipmentSlot.MAINHAND); }

    /** effects: the raid stopped and its raiders gone: no wave of its own reaches a later test. */
    static void end(Raid raid) {
        for (var r : raid.getAllRaiders()) r.discard();
        raid.stop();
    }

    // ---- melee ------------------------------------------------------------------------------

    /** A worker with an iron helmet on, an iron sword and a stone sword in its home chest: the raid
     * comes, it takes the iron sword and kills a raider standing in the yard; a player can't trade with
     * it meanwhile. The raid stopped, the sword goes back in the chest, the stone sword never left, and
     * the helmet stays on. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "defence_sword")
    public void aSwordsmanArmsAtItsChestKillsARaiderAndPutsTheSwordBack(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "warlord");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(Items.STONE_SWORD), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BREAD, 8));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FARMER, owner, bed, null);
        worker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        var raider = new AtomicReference<Raider>();
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider.set(raider(h, raid.get(), EntityType.VINDICATOR, 22, 10, true));
        }).thenWaitUntil(() -> h.assertTrue(hand(worker).is(Items.IRON_SWORD), "the iron sword in its hand: " + hand(worker)))
                .thenExecute(() -> {
                    h.assertTrue(defending(worker), "out defending");
                    h.assertTrue(Yard.count(h, chest, Items.STONE_SWORD) == 1 && Yard.count(h, chest, Items.IRON_SWORD) == 0, "the better sword taken");
                    Yard.use(owner, worker);
                    h.assertFalse(owner.containerMenu instanceof MerchantMenu, "nobody trades with a defender");
                })
                .thenWaitUntil(() -> h.assertTrue(raider.get().isDeadOrDying(), "the raider falls: " + raider.get().getHealth()))
                .thenExecute(() -> raid.get().stop())
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.IRON_SWORD) == 1, "the sword goes back"))
                .thenExecute(() -> {
                    h.assertTrue(hand(worker).isEmpty() && !Defenders.arms(worker).carries(), "its hands empty: " + hand(worker));
                    h.assertTrue(worker.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet stays on");
                    h.assertTrue(Yard.count(h, chest, Items.STONE_SWORD) == 1 && Yard.count(h, chest, Items.BREAD) == 8, "the rest untouched");
                    end(raid.get());
                }).thenSucceed();
    }

    /** A worker in diamond armour with a diamond sword at home, against a live vindicator: it kills it,
     * and out defending it never panics. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "defence_live")
    public void aWorkerInDiamondKillsALiveVindicatorWithoutPanicking(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "marshal");
        var bed = Yard.bed(h, 8, 8);
        Yard.chest(h, 10, 8, new ItemStack(Items.DIAMOND_SWORD));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FARMER, owner, bed, null);
        worker.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        worker.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        worker.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
        worker.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        var raider = new AtomicReference<Raider>();
        var raid = new AtomicReference<Raid>();
        var panicked = new boolean[]{false};
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> raid.set(raid(h, owner, bed)))
                .thenWaitUntil(() -> h.assertTrue(hand(worker).is(Items.DIAMOND_SWORD), "armed"))
                .thenExecute(() -> raider.set(raider(h, raid.get(), EntityType.VINDICATOR, 22, 8, false)))
                .thenWaitUntil(() -> {
                    if (worker.getBrain().isActive(Activity.PANIC)) panicked[0] = true;
                    h.assertTrue(raider.get().isDeadOrDying() || !raider.get().isAlive(), "the vindicator falls: " + raider.get().getHealth() + ", the worker at " + worker.getHealth());
                })
                .thenExecute(() -> {
                    h.assertTrue(worker.isAlive(), "the worker lives");
                    h.assertFalse(panicked[0], "it never panicked");
                    end(raid.get());
                }).thenSucceed();
    }

    // ---- ranged -----------------------------------------------------------------------------

    /** A bow, 16 arrows and an iron sword at home: it takes them all, stands within range and shoots a
     * raider dead, then puts the bow, the sword and the arrows it has left back. */
    @GameTest(template = "yard", timeoutTicks = 1400, batch = "defence_bow")
    public void anArcherSpendsArrowsFromItsChestAndPutsTheRestBack(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "bowyer");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16), new ItemStack(Items.IRON_SWORD));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FLETCHER, owner, bed, null);
        var raider = new AtomicReference<Raider>();
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider.set(raider(h, raid.get(), EntityType.PILLAGER, 22, 8, true));
            raider.get().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }).thenWaitUntil(() -> h.assertTrue(hand(worker).is(Items.BOW), "the bow in its hand: " + hand(worker)))
                .thenExecute(() -> h.assertTrue(Yard.count(h, chest, Items.ARROW) == 0 && Yard.count(h, chest, Items.IRON_SWORD) == 0, "the arrows and the sword carried too"))
                .thenWaitUntil(() -> h.assertTrue(raider.get().isDeadOrDying(), "the raider falls: " + raider.get().getHealth()))
                .thenExecute(() -> raid.get().stop())
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.BOW) == 1 && Yard.count(h, chest, Items.IRON_SWORD) == 1, "the bow and the sword go back"))
                .thenExecute(() -> {
                    int left = Yard.count(h, chest, Items.ARROW);
                    h.assertTrue(left > 0 && left < 16, "some arrows spent, the rest back: " + left);
                    h.assertTrue(hand(worker).isEmpty() && !Defenders.arms(worker).carries(), "nothing carried");
                    end(raid.get());
                }).thenSucceed();
    }

    /** A bow, two arrows and an iron sword: two arrows into a ravager, then the sword. */
    @GameTest(template = "yard", timeoutTicks = 1400, batch = "defence_switch")
    public void outOfArrowsItDrawsItsSword(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "quartermaster");
        var bed = Yard.bed(h, 8, 8);
        Yard.chest(h, 10, 8, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 2), new ItemStack(Items.IRON_SWORD));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FLETCHER, owner, bed, null);
        var raider = new AtomicReference<Raider>();
        var raid = new AtomicReference<Raid>();
        var sawBow = new boolean[]{false};
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider.set(raider(h, raid.get(), EntityType.RAVAGER, 22, 8, true));
        }).thenWaitUntil(() -> {
            if (hand(worker).is(Items.BOW)) sawBow[0] = true;
            h.assertTrue(sawBow[0] && hand(worker).is(Items.IRON_SWORD), "the bow first, then the sword: " + hand(worker));
        }).thenExecute(() -> {
            var carried = Defenders.arms(worker).kept().stream().map(k -> k.stack()).toList();
            h.assertTrue(carried.stream().anyMatch(s -> s.is(Items.BOW)) && carried.stream().noneMatch(s -> s.is(Items.ARROW)), "the bow carried, no arrows: " + carried);
        }).thenWaitUntil(() -> h.assertTrue(raider.get().getHealth() < raider.get().getMaxHealth() - 12, "two arrows and a blow land: " + raider.get().getHealth()))
                .thenExecute(() -> end(raid.get())).thenSucceed();
    }

    /** A Ranged Weapons Mod pistol, empty, and 20 small rounds at home: it loads fifteen, shoots a raider
     * dead, and puts the pistol and every round it has left back. */
    @GameTest(template = "yard", timeoutTicks = 1400, batch = "defence_gun")
    public void aGunnerLoadsRoundsFromItsChestFiresAndPutsTheRestBack(GameTestHelper h) {
        Yard.floor(h);
        var pistol = item("rangedweaponsmod:pistol");
        var round = item("rangedweaponsmod:small_round");
        var owner = Yard.player(h, 2, 2, "gunsmith");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(pistol), new ItemStack(round, 20));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.WEAPONSMITH, owner, bed, null);
        var raider = new AtomicReference<Raider>();
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider.set(raider(h, raid.get(), EntityType.PILLAGER, 24, 8, true));
            raider.get().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }).thenWaitUntil(() -> h.assertTrue(hand(worker).is(pistol), "the pistol in its hand"))
                .thenWaitUntil(() -> h.assertTrue(com.chunkworks.serfdom.compat.GunsCompat.rounds(hand(worker)) > 0, "loaded from what it carried"))
                .thenWaitUntil(() -> h.assertTrue(raider.get().isDeadOrDying(), "the raider falls: " + raider.get().getHealth()))
                .thenExecute(() -> raid.get().stop())
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, pistol) == 1, "the pistol goes back"))
                .thenExecute(() -> {
                    var gun = new ItemStack(pistol);
                    var c = (net.minecraft.world.Container) h.getLevel().getBlockEntity(chest);
                    int loaded = 0;
                    for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(pistol)) loaded = com.chunkworks.serfdom.compat.GunsCompat.rounds(c.getItem(i));
                    int loose = Yard.count(h, chest, round);
                    h.assertTrue(loaded + loose < 20 && loaded + loose >= 20 - 15, "rounds fired, the rest back loaded and loose: " + loaded + " + " + loose);
                    h.assertTrue(hand(worker).isEmpty() && !Defenders.arms(worker).carries(), "nothing carried");
                    end(raid.get());
                }).thenSucceed();
    }

    // ---- nothing to fight with ----------------------------------------------------------------

    /** A bow without arrows and a rocket launcher with rockets: a launcher is never taken, a bow with
     * nothing to shoot is no weapon. It takes nothing and hides, as vanilla's villagers do. */
    @GameTest(template = "yard", timeoutTicks = 400, batch = "defence_none")
    public void withNothingToFightWithItTakesNothingAndHides(GameTestHelper h) {
        Yard.floor(h);
        var launcher = item("rangedweaponsmod:rocket_launcher");
        var rocket = item("rangedweaponsmod:rocket");
        var owner = Yard.player(h, 2, 2, "pacifist");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(Items.BOW), new ItemStack(launcher), new ItemStack(rocket, 4));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FARMER, owner, bed, null);
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider(h, raid.get(), EntityType.PILLAGER, 22, 8, true);
        }).thenWaitUntil(() -> h.assertTrue(Defenders.arms(worker).raid() == raid.get().getId(), "it looked"))
                .thenWaitUntil(() -> h.assertTrue(worker.getBrain().isActive(Activity.PRE_RAID) || worker.getBrain().isActive(Activity.RAID)
                        || worker.getBrain().isActive(Activity.HIDE), "it hides as vanilla's do"))
                .thenExecute(() -> {
                    h.assertFalse(defending(worker), "not defending");
                    h.assertTrue(Yard.count(h, chest, Items.BOW) == 1 && Yard.count(h, chest, launcher) == 1 && Yard.count(h, chest, rocket) == 4, "nothing taken");
                    h.assertTrue(hand(worker).isEmpty(), "nothing in its hand");
                    end(raid.get());
                }).thenSucceed();
    }

    // ---- who ----------------------------------------------------------------------------------

    /** A captive with a sword in its home chest arms; a free villager with a sword in a chest by its bed
     * doesn't. */
    @GameTest(template = "yard", timeoutTicks = 600, batch = "defence_who")
    public void aCaptiveArmsAndAFreeVillagerDoesNot(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 2, 2, "slaver");
        var capBed = Yard.bed(h, 8, 8);
        Yard.chest(h, 10, 8, new ItemStack(Items.IRON_SWORD));
        var freeBed = Yard.bed(h, 30, 8);
        var freeChest = Yard.chest(h, 32, 8, new ItemStack(Items.IRON_SWORD));
        var captive = Yard.villager(h, 12, 12, VillagerProfession.FARMER, 2);
        var free = Yard.villager(h, 30, 12, VillagerProfession.FARMER, 2);
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.capture(level, captive, owner, new ItemStack(Serfdom.CHAIN_LEAD.get()));
            captive.dropLeash(true, false);
            h.assertTrue(Workers.assignBed(level, captive, capBed) == Workers.Picked.OK, "the captive's bed");
            Workers.set(level, captive, Workers.of(captive).withCuffs(false));
            MarketGameTests.townsman(h, free, freeBed, 5);
            raid.set(raid(h, owner, capBed));
            raider(h, raid.get(), EntityType.PILLAGER, 20, 20, true);
        }).thenWaitUntil(() -> h.assertTrue(hand(captive).is(Items.IRON_SWORD), "the captive armed"))
                .thenIdle(100)
                .thenExecute(() -> {
                    h.assertTrue(hand(free).isEmpty() && Yard.count(h, freeChest, Items.IRON_SWORD) == 1, "the free villager took nothing");
                    h.assertFalse(Defenders.arms(free).carries(), "nor carries anything");
                    end(raid.get());
                }).thenSucceed();
    }

    /** At night a zombie stands by a worker with a sword at home: no raid, no arms. */
    @GameTest(template = "yard", timeoutTicks = 300, batch = "defence_zombie")
    public void aZombieAtNightArmsNobody(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "sleeper");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(Items.IRON_SWORD));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FARMER, owner, bed, null);
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        var at = Yard.at(h, 14, 1, 12);
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        zombie.setNoAi(true);
        h.getLevel().addFreshEntity(zombie);
        h.startSequence().thenIdle(200).thenExecute(() -> {
            h.assertTrue(Yard.count(h, chest, Items.IRON_SWORD) == 1 && hand(worker).isEmpty(), "nothing taken");
            h.assertFalse(defending(worker) || Defenders.arms(worker).raid() >= 0, "it never armed");
            zombie.discard();
        }).thenSucceed();
    }

    // ---- the line of fire ---------------------------------------------------------------------

    /** An archer by its chest, a raider down the line, and a villager standing between: no arrow flies.
     * The villager steps aside, and the raider is hit. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "defence_friend")
    public void aFriendInTheLineHoldsItsFire(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 40, "marksman");
        var bed = Yard.bed(h, 8, 6);
        var chest = Yard.chest(h, 8, 9, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16));
        var worker = Yard.worker(h, 7, 10, VillagerProfession.FLETCHER, owner, bed, null);
        var friend = Yard.villager(h, 15, 10, VillagerProfession.MASON, 2);
        friend.setNoAi(true);
        var raider = new AtomicReference<Raider>();
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider.set(raider(h, raid.get(), EntityType.PILLAGER, 21, 10, true));
            raider.get().setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }).thenWaitUntil(() -> h.assertTrue(hand(worker).is(Items.BOW), "the bow in its hand"))
                .thenExecute(() -> {
                    // Where it stands with its bow: the friend in the line, the raider within range.
                    worker.moveTo(Yard.at(h, 9, 1, 10).getX() + 0.5, worker.getY(), Yard.at(h, 9, 1, 10).getZ() + 0.5);
                })
                .thenIdle(120)
                .thenExecute(() -> {
                    h.assertTrue(raider.get().getHealth() == raider.get().getMaxHealth(), "no arrow while the friend stands between: " + raider.get().getHealth());
                    h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class, new AABB(Yard.at(h, 0, 0, 0)).inflate(48)).isEmpty(), "none flying");
                    var aside = Yard.at(h, 15, 1, 16);
                    friend.moveTo(aside.getX() + 0.5, aside.getY(), aside.getZ() + 0.5);
                })
                .thenWaitUntil(() -> h.assertTrue(raider.get().getHealth() < raider.get().getMaxHealth(), "the friend aside, the raider is hit"))
                .thenExecute(() -> end(raid.get())).thenSucceed();
    }

    // ---- leaving --------------------------------------------------------------------------------

    /** A defender with a sword is set free mid-raid: the sword drops where it stands, and it carries
     * nothing of ours after. */
    @GameTest(template = "yard", timeoutTicks = 600, batch = "defence_freed")
    public void aDefenderSetFreeMidRaidDropsWhatItTook(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 2, 2, "emancipator");
        var bed = Yard.bed(h, 8, 8);
        Yard.chest(h, 10, 8, new ItemStack(Items.IRON_SWORD));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FARMER, owner, bed, null);
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider(h, raid.get(), EntityType.PILLAGER, 40, 40, true);
        }).thenWaitUntil(() -> h.assertTrue(hand(worker).is(Items.IRON_SWORD), "armed"))
                .thenExecute(() -> {
                    var where = worker.blockPosition();
                    Workers.free(level, worker, com.chunkworks.serfdom.domain.Parting.Way.SET_FREE);
                    var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3));
                    h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_SWORD)), "the sword drops: " + drops.stream().map(ItemEntity::getItem).toList());
                    h.assertTrue(hand(worker).isEmpty() && !Defenders.arms(worker).carries(), "it carries nothing of ours");
                    end(raid.get());
                }).thenSucceed();
    }

    /** A bow and one arrow against a ravager: one shot, then nothing to fight with, so it hides,
     * carrying the bow. The raid stopped, it comes out and puts the bow back. */
    @GameTest(template = "yard", timeoutTicks = 1200, batch = "defence_hid")
    public void aDefenderThatHidPutsItsBowBackAfterTheRaid(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "survivor");
        var bed = Yard.bed(h, 8, 8);
        var chest = Yard.chest(h, 10, 8, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 1));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FLETCHER, owner, bed, null);
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider(h, raid.get(), EntityType.RAVAGER, 22, 8, true);
        }).thenWaitUntil(() -> h.assertTrue(Defenders.arms(worker).carries() && Defenders.fodderOf(worker) == 0 && !defending(worker),
                        "the arrow spent, it hides: " + Defenders.arms(worker)))
                .thenExecute(() -> {
                    h.assertTrue(Yard.count(h, chest, Items.BOW) == 0, "still carrying the bow");
                    raid.get().stop();
                })
                .thenWaitUntil(() -> h.assertTrue(Yard.count(h, chest, Items.BOW) == 1, "the bow put back"))
                .thenExecute(() -> {
                    h.assertTrue(!Defenders.arms(worker).carries() && hand(worker).isEmpty(), "nothing carried");
                    end(raid.get());
                }).thenSucceed();
    }

    // ---- death --------------------------------------------------------------------------------

    /** A defender with a bow, arrows and a sword, killed: all three drop. */
    @GameTest(template = "yard", timeoutTicks = 600, batch = "defence_death")
    public void aDefenderKilledDropsWhatItTook(GameTestHelper h) {
        Yard.floor(h);
        var owner = Yard.player(h, 2, 2, "undertaker");
        var bed = Yard.bed(h, 8, 8);
        Yard.chest(h, 10, 8, new ItemStack(Items.BOW), new ItemStack(Items.ARROW, 16), new ItemStack(Items.IRON_SWORD));
        var worker = Yard.worker(h, 12, 12, VillagerProfession.FLETCHER, owner, bed, null);
        var raid = new AtomicReference<Raid>();
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            raid.set(raid(h, owner, bed));
            raider(h, raid.get(), EntityType.PILLAGER, 40, 40, true);
        }).thenWaitUntil(() -> h.assertTrue(hand(worker).is(Items.BOW) && Defenders.arms(worker).kept().size() >= 2, "armed: " + Defenders.arms(worker)))
                .thenExecute(() -> {
                    var where = worker.blockPosition();
                    worker.kill();
                    var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3));
                    for (var item : new Item[]{Items.BOW, Items.IRON_SWORD, Items.ARROW})
                        h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(item)), "it drops its " + item + ": " + drops.stream().map(ItemEntity::getItem).toList());
                    end(raid.get());
                }).thenSucceed();
    }
}
