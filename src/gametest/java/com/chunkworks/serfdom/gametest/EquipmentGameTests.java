/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.WorkerMenu;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Parting;
import com.chunkworks.serfdom.domain.WorkDay;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-server partitions of phase 2b (D-0004), every click through the server's own handler for
 * the client's click packet, with Lucky's Wardrobe loaded:
 * <ul>
 * <li>dressing: a piece clicked on is worn, marked to drop whole, and protects as armour does
 * (against a bare twin's same hit); a captive and a child are dressed alike;</li>
 * <li>wear: a hit wears every piece as on a player and breaks a spent one; an Unbreakable piece and
 * Lucky's Wardrobe's clothes do not wear; a falling block wears a free villager's helmet;</li>
 * <li>who: a stranger opens nothing and a forged menu of theirs takes nothing; the owner past the
 * reach takes nothing; a piece goes only where it is worn; binding keeps a piece on but in
 * creative;</li>
 * <li>a screen left open on a worker that dies, or goes free, takes nothing more;</li>
 * <li>death: everything worn drops whole with the tool and the load, mob loot on or off, but a piece
 * with Curse of Vanishing;</li>
 * <li>shift-clicks: each piece into its slot (armour, a carved pumpkin, an elytra, Lucky's
 * Wardrobe's hat, boots and trousers), back out, and what has no free slot to the hotbar.</li>
 * </ul>
 * Turning zombie, set free, escaping and the law's freeing are in {@link CaptiveGameTests}, beside
 * the tests of those ways. */
@GameTestHolder("serfdom") @PrefixGameTestTemplate(false)
public final class EquipmentGameTests {
    private static final int SETTLE = 5;

    @BeforeBatch(batch = "equipment") public static void equipment(ServerLevel level) { Yard.hour(level, WorkDay.WORK_START - 1500); }

    /** effects: {@code player} clicks slot {@code slot} of their open menu, as the client's click
     * packet arrives at the server (which asks first whether the menu may still be used). */
    static void click(ServerPlayer player, int slot, int button, ClickType type) {
        var menu = player.containerMenu;
        player.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, button, type,
                menu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
    }

    /** effects: {@code owner} opens their worker's screen with a sneaking use, as a player does. */
    static WorkerMenu open(GameTestHelper h, ServerPlayer owner, Villager worker) {
        owner.setShiftKeyDown(true);
        Yard.use(owner, worker);
        owner.setShiftKeyDown(false);
        h.assertTrue(owner.containerMenu instanceof WorkerMenu m && m.villager() == worker, "the Worker Screen is open on the worker: " + owner.containerMenu);
        return (WorkerMenu) owner.containerMenu;
    }

    /** effects: the pieces put on {@code villager} as the Worker Screen's slots put them, head first. */
    static void dress(ServerPlayer owner, Villager villager, ItemStack... pieces) {
        var menu = new WorkerMenu(0, owner.getInventory(), villager);
        for (int i = 0; i < pieces.length; i++) if (!pieces[i].isEmpty()) menu.slots.get(WorkerMenu.WEARING + i).set(pieces[i]);
    }

    /** effects: the drop chance the villager keeps for {@code slot}, as it saves it. */
    static float dropChance(Villager villager, EquipmentSlot slot) {
        return villager.saveWithoutId(new CompoundTag()).getList("ArmorDropChances", Tag.TAG_FLOAT).getFloat(slot.getIndex());
    }

    static ItemStack wardrobe(GameTestHelper h, String path) {
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("luckyswardrobe", path));
        h.assertTrue(item != Items.AIR, "Lucky's Wardrobe's " + path + " is registered");
        return new ItemStack(item);
    }

    static ItemStack cursed(ServerLevel level, Item item, ResourceKey<Enchantment> curse) {
        var stack = new ItemStack(item);
        stack.enchant(level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(curse), 1);
        return stack;
    }

    static Zombie attacker(GameTestHelper h, int x, int z) {
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        var at = Yard.at(h, x, 1, z);
        zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        zombie.setNoAi(true);
        h.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    static List<ItemEntity> dropsNear(GameTestHelper h, Villager v) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(v.blockPosition()).inflate(3));
    }

    static int count(List<ItemEntity> drops, Item item) {
        return drops.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    // ---- dressing -------------------------------------------------------------------------

    /** The owner picks a diamond chestplate up from their inventory and clicks it onto the chest
     * slot: the worker wears it, it is marked to drop whole, the worker's armour reads 8, and the
     * same zombie's hit takes less from it than from its bare twin. A captive and a child take
     * a helmet the same way. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "equipment")
    public void aPieceClickedOntoAWorkerIsWornAndProtectsAsArmourDoes(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "dresser", new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_HELMET));
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var twin = Yard.villager(h, 20, 10, VillagerProfession.FARMER, 2);
        var captive = Yard.villager(h, 12, 10, VillagerProfession.MASON, 2);
        var child = Yard.villager(h, 8, 10, VillagerProfession.NONE, 1);
        child.setAge(-24000);
        for (var v : List.of(worker, twin, captive, child)) v.setNoAi(true);
        var zombie = attacker(h, 15, 4);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, worker, owner, Optional.empty());
            open(h, owner, worker);
            click(owner, WorkerMenu.INVENTORY, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().is(Items.DIAMOND_CHESTPLATE), "the chestplate is on the cursor");
            click(owner, WorkerMenu.WEARING + 1, 0, ClickType.PICKUP);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "worn: " + worker.getItemBySlot(EquipmentSlot.CHEST));
            h.assertTrue(owner.containerMenu.getCarried().isEmpty(), "the cursor is empty");
            h.assertTrue(dropChance(worker, EquipmentSlot.CHEST) > 1.0F, "marked to drop whole: " + dropChance(worker, EquipmentSlot.CHEST));
            owner.closeContainer();
            Workers.capture(level, captive, owner, new ItemStack(Serfdom.CHAIN_LEAD.get()));
            h.assertTrue(Workers.of(captive).cuffed(), "the captive is in chains");
            Workers.hire(level, child, owner, Optional.empty());
            for (var v : List.of(captive, child)) {
                open(h, owner, v);
                click(owner, WorkerMenu.INVENTORY + (v == captive ? 1 : 2), 0, ClickType.QUICK_MOVE);
                h.assertTrue(v.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), (v == captive ? "the captive" : "the child") + " wears the helmet");
                owner.closeContainer();
            }
        }).thenIdle(2).thenExecute(() -> {
            h.assertTrue(worker.getArmorValue() == 8, "the worker's armour reads 8: " + worker.getArmorValue());
            h.assertTrue(twin.getArmorValue() == 0, "the twin's 0");
            worker.hurt(level.damageSources().mobAttack(zombie), 6.0F);
            twin.hurt(level.damageSources().mobAttack(zombie), 6.0F);
            h.assertTrue(Math.abs(twin.getHealth() - 14.0F) < 1e-3, "the bare twin takes all 6: " + twin.getHealth());
            // Diamond: 8 points, toughness 2: 6 × (1 − (8 − 6 ÷ 2.5) ÷ 25) = 4.656.
            h.assertTrue(Math.abs(worker.getHealth() - (20.0F - 4.656F)) < 1e-3, "the worker takes 4.656: " + worker.getHealth());
        }).thenSucceed();
    }

    // ---- wear -----------------------------------------------------------------------------

    /** One zombie's hit of 8 wears every piece by 2, as on a player: the iron helmet loses 2, the
     * chestplate with 1 left breaks off, Unbreakable leggings and Lucky's Wardrobe's boots lose
     * nothing. A falling block wears a free villager's helmet: every villager's armour wears. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "equipment")
    public void armourOnAVillagerWearsAndBreaksAsOnAPlayer(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "wearer");
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var free = Yard.villager(h, 20, 10, VillagerProfession.FARMER, 2);
        for (var v : List.of(worker, free)) v.setNoAi(true);
        var zombie = attacker(h, 15, 4);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, worker, owner, Optional.empty());
            var chest = new ItemStack(Items.IRON_CHESTPLATE);
            chest.setDamageValue(chest.getMaxDamage() - 1);
            var legs = new ItemStack(Items.LEATHER_LEGGINGS);
            legs.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            var boots = wardrobe(h, "taiga_boots");
            dress(owner, worker, new ItemStack(Items.IRON_HELMET), chest, legs, boots);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.FEET).is(boots.getItem()), "Lucky's Wardrobe's boots are worn on the feet");
            double[] points = {0};
            boots.forEachModifier(EquipmentSlot.FEET, (attribute, modifier) -> { if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)) points[0] += modifier.amount(); });
            org.slf4j.LoggerFactory.getLogger("Serfdom test").info("Lucky's Wardrobe's taiga boots give {} armour points, max damage {}", points[0], boots.getMaxDamage());
            dress(owner, free, new ItemStack(Items.IRON_HELMET));
            worker.hurt(level.damageSources().mobAttack(zombie), 8.0F);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() == 2, "the helmet lost 2: " + worker.getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "the spent chestplate broke off");
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.LEGS).getDamageValue() == 0, "the Unbreakable leggings lost nothing");
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.FEET).is(boots.getItem()) && worker.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 0, "the clothes lost nothing");
            free.hurt(level.damageSources().fallingBlock(zombie), 4.0F);
            h.assertTrue(free.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() >= 1, "a falling block wore the free villager's helmet: " + free.getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
        }).thenSucceed();
    }

    // ---- who --------------------------------------------------------------------------------

    /** A stranger's sneaking use opens nothing, and a menu of theirs, forged, takes nothing. The
     * owner past eight blocks takes nothing; back within reach, the helmet comes off on the cursor,
     * and will not go on the empty legs (nor swap off the bound boots). Bound boots stay on in
     * survival and come off in creative. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "equipment")
    public void onlyTheOwnerWithinReachChangesWhatAWorkerWears(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "keeper");
        var stranger = Yard.player(h, 12, 12, "stranger");
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        worker.setNoAi(true);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, worker, owner, Optional.empty());
            dress(owner, worker, new ItemStack(Items.IRON_HELMET), ItemStack.EMPTY, ItemStack.EMPTY, cursed(level, Items.IRON_BOOTS, Enchantments.BINDING_CURSE));
            stranger.setShiftKeyDown(true);
            Yard.use(stranger, worker);
            h.assertTrue(stranger.containerMenu == stranger.inventoryMenu, "the stranger's use opens nothing");
            var forged = new WorkerMenu(stranger.containerMenu.containerId + 1, stranger.getInventory(), worker);
            stranger.containerMenu = forged;
            h.assertFalse(forged.stillValid(stranger), "a stranger's menu is not valid");
            click(stranger, WorkerMenu.WEARING, 0, ClickType.PICKUP);
            h.assertTrue(stranger.containerMenu.getCarried().isEmpty() && worker.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the forged click took nothing");
            stranger.containerMenu = stranger.inventoryMenu;

            open(h, owner, worker);
            var near = owner.position();
            owner.moveTo(worker.getX() + 9.0, owner.getY(), worker.getZ());
            click(owner, WorkerMenu.WEARING, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().isEmpty() && worker.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "nine blocks off, nothing taken");
            owner.moveTo(near.x, near.y, near.z);
            click(owner, WorkerMenu.WEARING, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().is(Items.IRON_HELMET) && worker.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "within reach the helmet comes off");
            click(owner, WorkerMenu.WEARING + 2, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().is(Items.IRON_HELMET) && worker.getItemBySlot(EquipmentSlot.LEGS).isEmpty(), "a helmet does not go on the empty legs");
            click(owner, WorkerMenu.WEARING + 3, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().is(Items.IRON_HELMET) && worker.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "nor swaps off the bound boots");
            click(owner, WorkerMenu.WEARING, 0, ClickType.PICKUP);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "back on the head");
            click(owner, WorkerMenu.WEARING + 3, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().isEmpty() && worker.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "bound boots stay on in survival");
            owner.setGameMode(GameType.CREATIVE);
            click(owner, WorkerMenu.WEARING + 3, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().is(Items.IRON_BOOTS) && worker.getItemBySlot(EquipmentSlot.FEET).isEmpty(), "and come off in creative");
        }).thenSucceed();
    }

    /** A screen left open on a worker that dies may no longer be used (the player's tick shuts a
     * menu that says so; a mock player has no tick of its own, and ticking one by hand sends
     * payloads it has no channel for), and a click on it takes nothing: the chestplate lies on the
     * ground once. A screen left open on a captive that goes free (here, on its way home) takes
     * nothing either, and it keeps wearing the helmet. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "equipment")
    public void aScreenLeftOpenOnAWorkerThatDiesOrGoesFreeTakesNothing(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "mourner");
        var doomed = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        var runner = Yard.villager(h, 20, 10, VillagerProfession.FARMER, 2);
        for (var v : List.of(doomed, runner)) v.setNoAi(true);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, doomed, owner, Optional.empty());
            dress(owner, doomed, ItemStack.EMPTY, new ItemStack(Items.IRON_CHESTPLATE));
            var menu = open(h, owner, doomed);
            // Killed past its armour: a hit of 1000 would wear every piece to nothing first.
            doomed.kill();
            h.assertFalse(doomed.isAlive(), "dead");
            h.assertFalse(menu.stillValid(owner), "the screen may not be used");
            click(owner, WorkerMenu.WEARING + 1, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().isEmpty(), "the click took nothing");
            h.assertTrue(count(dropsNear(h, doomed), Items.IRON_CHESTPLATE) == 1, "the chestplate lies there once: " + dropsNear(h, doomed));

            Workers.capture(level, runner, owner, new ItemStack(Serfdom.CHAIN_LEAD.get()));
            dress(owner, runner, new ItemStack(Items.IRON_HELMET));
            owner.moveTo(runner.getX(), runner.getY(), runner.getZ() + 2.0);
            var open = open(h, owner, runner);
            Workers.free(level, runner, Parting.Way.ESCAPES);
            h.assertFalse(open.stillValid(owner), "the screen may not be used on a free villager");
            click(owner, WorkerMenu.WEARING, 0, ClickType.PICKUP);
            h.assertTrue(owner.containerMenu.getCarried().isEmpty() && runner.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "nothing taken; it keeps the helmet");
        }).thenSucceed();
    }

    // ---- death --------------------------------------------------------------------------------

    /** Two dressed workers with an axe and five logs die, one with mob loot on, one with it off:
     * each drops its helmet, chestplate and boots whole with the axe and the logs, and its leggings
     * with Curse of Vanishing are gone. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "equipment")
    public void aDeadWorkerDropsWhatItWoreButTheVanishing(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "heir");
        var first = Yard.villager(h, 8, 10, VillagerProfession.FARMER, 2);
        var second = Yard.villager(h, 30, 10, VillagerProfession.FARMER, 2);
        for (var v : List.of(first, second)) v.setNoAi(true);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            var rule = level.getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
            boolean was = rule.get();
            try {
                for (var v : List.of(first, second)) {
                    Workers.hire(level, v, owner, Optional.empty());
                    Workers.set(level, v, Workers.of(v).withTool(new ItemStack(Items.IRON_AXE)));
                    v.getInventory().addItem(new ItemStack(Items.OAK_LOG, 5));
                    var helmet = new ItemStack(Items.IRON_HELMET);
                    helmet.setDamageValue(30);
                    dress(owner, v, helmet, new ItemStack(Items.IRON_CHESTPLATE), cursed(level, Items.IRON_LEGGINGS, Enchantments.VANISHING_CURSE), new ItemStack(Items.IRON_BOOTS));
                    rule.set(v == first, level.getServer());
                    // Killed past its armour: a hit of 1000 would wear every piece to nothing first.
                    v.kill();
                    var drops = dropsNear(h, v);
                    var loot = v == first ? "mob loot on" : "mob loot off";
                    for (var item : List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_BOOTS, Items.IRON_AXE))
                        h.assertTrue(count(drops, item) == 1, loot + ": one " + item + " in " + drops);
                    h.assertTrue(count(drops, Items.OAK_LOG) == 5, loot + ": the logs");
                    h.assertTrue(count(drops, Items.IRON_LEGGINGS) == 0, loot + ": the vanishing leggings are gone");
                    h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.IRON_HELMET) && e.getItem().getDamageValue() == 30), loot + ": the helmet drops whole, as worn");
                }
            } finally {
                rule.set(was, level.getServer());
            }
        }).thenSucceed();
    }

    // ---- shift-clicks -------------------------------------------------------------------------

    /** Shift-clicks from the inventory: an iron helmet onto the head; Lucky's Wardrobe's farmer hat,
     * with the head worn, to the hotbar; its trousers onto the legs and its boots onto the feet; an
     * elytra onto the chest; a stick to the hotbar. The helmet shift-clicked off goes back to the
     * inventory, and a carved pumpkin goes on in its place. */
    @GameTest(template = "yard", timeoutTicks = 100, batch = "equipment")
    public void aShiftClickSendsEachPieceToItsSlotAndBack(GameTestHelper h) {
        Yard.floor(h);
        var level = h.getLevel();
        var owner = Yard.player(h, 10, 12, "valet", new ItemStack(Items.IRON_HELMET), wardrobe(h, "farmer_hat"), wardrobe(h, "snowy_pants"),
                wardrobe(h, "taiga_boots"), new ItemStack(Items.ELYTRA), new ItemStack(Items.STICK), new ItemStack(Items.CARVED_PUMPKIN));
        var worker = Yard.villager(h, 10, 10, VillagerProfession.FARMER, 2);
        worker.setNoAi(true);
        h.startSequence().thenIdle(SETTLE).thenExecute(() -> {
            Workers.hire(level, worker, owner, Optional.empty());
            open(h, owner, worker);
            int inv = WorkerMenu.INVENTORY;
            click(owner, inv, 0, ClickType.QUICK_MOVE);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET), "the helmet onto the head");
            click(owner, inv + 1, 0, ClickType.QUICK_MOVE);
            h.assertTrue(owner.getInventory().items.subList(0, 9).stream().anyMatch(s -> s.getItem() == BuiltInRegistries.ITEM.get(ResourceLocation.parse("luckyswardrobe:farmer_hat"))),
                    "the farmer hat, the head worn, to the hotbar");
            click(owner, inv + 2, 0, ClickType.QUICK_MOVE);
            h.assertTrue(BuiltInRegistries.ITEM.getKey(worker.getItemBySlot(EquipmentSlot.LEGS).getItem()).toString().equals("luckyswardrobe:snowy_pants"), "the trousers onto the legs: " + worker.getItemBySlot(EquipmentSlot.LEGS));
            click(owner, inv + 3, 0, ClickType.QUICK_MOVE);
            h.assertTrue(BuiltInRegistries.ITEM.getKey(worker.getItemBySlot(EquipmentSlot.FEET).getItem()).toString().equals("luckyswardrobe:taiga_boots"), "the boots onto the feet: " + worker.getItemBySlot(EquipmentSlot.FEET));
            click(owner, inv + 4, 0, ClickType.QUICK_MOVE);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA), "the elytra onto the chest");
            click(owner, inv + 5, 0, ClickType.QUICK_MOVE);
            h.assertTrue(owner.getInventory().items.subList(0, 9).stream().anyMatch(s -> s.is(Items.STICK)), "the stick to the hotbar");
            click(owner, WorkerMenu.WEARING, 0, ClickType.QUICK_MOVE);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && owner.getInventory().items.subList(9, 36).stream().anyMatch(s -> s.is(Items.IRON_HELMET)),
                    "the helmet back to the inventory");
            click(owner, inv + 6, 0, ClickType.QUICK_MOVE);
            h.assertTrue(worker.getItemBySlot(EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN), "the carved pumpkin onto the head");
        }).thenSucceed();
    }
}
