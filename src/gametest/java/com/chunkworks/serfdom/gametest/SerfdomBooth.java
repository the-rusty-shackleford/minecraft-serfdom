/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.client.PickerScreen;
import com.chunkworks.serfdom.client.PostScreen;
import com.chunkworks.serfdom.client.StockScreen;
import com.chunkworks.serfdom.client.WorkerScreen;
import com.chunkworks.serfdom.domain.Need;
import com.chunkworks.serfdom.domain.Stock;
import com.chunkworks.serfdom.domain.Workshop;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The booth (D-0001, D-0002, D-0003), silent, software-rendered, graded by eye from its
 * photographs: a Work Post facing the camera with its outline on and six workers in a row behind
 * it, each showing one need; the Worker Screen of the first; the post's screen, with a click on [+]
 * that the server takes; a worker in chains on the chain lead; the post and the chain in the
 * inventory. Then a smithy and a kitchen, each a post among its stations: the blacksmith's post
 * screen and stock list (a row stocked, one short, one wanting a smithing table), the cook's (one row
 * being made, one without fuel, one without a knife), and the picker searched and clicked, the row
 * arriving on the server. Then the capture: the chain held on a free farmer, the farmer taken and
 * cuffed, its Worker Screen; and four captives in the trailer, seen through its open doors and from
 * its side. Then (D-0004) armour, clothes, the robe rule, a child, a captive, an elytra, walking,
 * the trailer and the dressed Worker Screen; and (D-0005) a worker eating its breakfast, one
 * cooking at a smoker, and the Worker Screen's drumsticks. Every step also checks in code what it
 * can. This fixture never ships. */
@EventBusSubscriber(modid = "serfdom_gametest", value = Dist.CLIENT)
public final class SerfdomBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Serfdom booth");
    private static int tick;
    private static final List<Integer> workers = new ArrayList<>();
    private static BlockPos post, smithy, kitchen, capture;
    private static int captive, trailerId, stage, walker, eater, griller;
    private static BlockPos kitchenLot, smokerAt;
    private static boolean ateShot, cookShot;
    private static int litSeen;
    private static final List<Integer> aboardIds = new ArrayList<>();
    private static BlockPos lot;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("serfdom.booth")) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        mc.getToasts().clear();
        try {
            switch (++tick) {
                case 20 -> server(mc, p -> {
                    var l = p.serverLevel();
                    l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, l.getServer());
                    l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, l.getServer());
                    l.setDayTime(6000);
                    l.setWeatherParameters(6000, 0, false, false);
                    p.setGameMode(GameType.SURVIVAL);
                    p.getInventory().clearContent();
                    var base = p.blockPosition();
                    for (int x = -8; x <= 8; x++) for (int z = -2; z <= 12; z++) {
                        l.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                        for (int y = 0; y < 4; y++) l.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    post = base.offset(0, 0, 3);
                    l.setBlockAndUpdate(post, Serfdom.WORK_POST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    var be = (WorkPostBlockEntity) l.getBlockEntity(post);
                    be.claim(p.getUUID(), p.getGameProfile().getName());
                    be.setJob(Serfdom.id("woodcutting"), 4);
                    be.setOutline(true);
                    var needs = Need.values();
                    for (int i = 0; i < needs.length; i++) {
                        var v = EntityType.VILLAGER.create(l);
                        // Seven, 1.7 apart: all in view, every one within the icons' eight blocks.
                        v.moveTo(base.getX() + 0.5 + 1.7 * (i - 3), base.getY(), base.getZ() + 5.5, 180F, 0F);
                        v.setYHeadRot(180F);
                        v.setVillagerData(v.getVillagerData().setProfession(i % 2 == 0 ? VillagerProfession.FARMER : VillagerProfession.FLETCHER).setLevel(2));
                        v.setNoAi(true);
                        l.addFreshEntity(v);
                        Workers.hire(l, v, p, Optional.empty());
                        Workers.shiftNeed(v, Optional.of(needs[i]));
                        v.setData(Serfdom.NEED, Need.code(Optional.of(needs[i])));
                        v.syncData(Serfdom.NEED);
                        workers.add(v.getId());
                    }
                    // Within the icons' eight blocks of every worker.
                    p.teleportTo(l, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0F, 10F);
                });
                case 60 -> {
                    var shown = workers.stream().map(id -> mc.level.getEntity(id)).filter(e -> e instanceof Villager)
                            .map(e -> Workers.shownNeed((Villager) e).map(Enum::name).orElse("none")).toList();
                    check(shown.equals(List.of("NO_BED", "HUNGRY", "NO_TOOL", "NO_STATION", "NO_FUEL", "NO_MATERIALS", "CHEST_FULL")), "the client sees each worker's need: " + shown);
                    check(mc.level.getBlockEntity(post) instanceof WorkPostBlockEntity be && be.outline() && be.radius() == 4, "the client has the post's outline and radius");
                    photo(mc, "01-post-and-needs");
                    server(mc, p -> Screens.openWorker(p, (Villager) p.serverLevel().getEntity(workers.get(1))));
                }
                case 80 -> {
                    var screen = screen(mc, WorkerScreen.class, "the Worker Screen opens");
                    check(screen.view().hasBed() == false && !screen.jobButton().active && !screen.clearButton().active && screen.bedButton().active,
                            "no bed: Assign bed live, Assign job and Clear job greyed");
                    photo(mc, "02-worker-screen");
                    mc.setScreen(null);
                    server(mc, p -> Screens.openPost(p, (WorkPostBlockEntity) p.serverLevel().getBlockEntity(post)));
                }
                case 100 -> {
                    var screen = screen(mc, PostScreen.class, "the post's screen opens");
                    check(screen.view().jobs().size() >= 2 && screen.radius() == 4, "two jobs at least, radius 4: " + screen.view().jobs());
                    photo(mc, "03-post-screen");
                    var plus = screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals("+")).map(c -> (Button) c).findFirst().orElseThrow();
                    check(screen.mouseClicked(plus.getX() + plus.getWidth() / 2.0, plus.getY() + plus.getHeight() / 2.0, 0), "the click lands on +");
                }
                case 120 -> {
                    server(mc, p -> check(((WorkPostBlockEntity) p.serverLevel().getBlockEntity(post)).radius() == 5, "the server took the new radius"));
                    photo(mc, "04-post-screen-radius-5");
                    mc.setScreen(null);
                    server(mc, p -> {
                        var v = (Villager) p.serverLevel().getEntity(workers.get(2));
                        v.moveTo(p.getX() + 2.5, p.getY(), p.getZ() + 4.0, 200F, 0F);
                        Workers.set(p.serverLevel(), v, Workers.of(v).withCuffs(true));
                        v.setLeashedTo(p, true);
                        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -30F, 20F);
                    });
                }
                case 150 -> {
                    var v = mc.level.getEntity(workers.get(2));
                    check(v instanceof Villager villager && villager.isLeashed() && Workers.cuffed(villager), "the client sees the worker in chains, on the chain");
                    photo(mc, "05-chain");
                    server(mc, p -> {
                        p.getInventory().setItem(9, new ItemStack(Serfdom.WORK_POST_ITEM.get(), 3));
                        p.getInventory().setItem(10, new ItemStack(Serfdom.CHAIN_LEAD.get(), 2));
                        p.getInventory().setItem(0, new ItemStack(Serfdom.CHAIN_LEAD.get()));
                    });
                }
                case 170 -> mc.setScreen(new InventoryScreen(mc.player));
                case 190 -> {
                    check(mc.screen instanceof InventoryScreen, "the inventory is open");
                    photo(mc, "06-items");
                    mc.setScreen(null);
                }
                case 200 -> server(mc, p -> {
                    var l = p.serverLevel();
                    for (var id : workers) { var e = l.getEntity(id); if (e != null) e.discard(); }
                    p.getInventory().clearContent();
                    var base = BlockPos.containing(p.getX(), p.getY(), p.getZ());
                    // The smithy, left: a blast furnace, a table, an anvil, its chest; no smithing table.
                    smithy = base.offset(-5, 0, 8);
                    l.setBlockAndUpdate(smithy, Serfdom.WORK_POST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    l.setBlockAndUpdate(base.offset(-7, 0, 10), Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    l.setBlockAndUpdate(base.offset(-6, 0, 10), Blocks.CRAFTING_TABLE.defaultBlockState());
                    l.setBlockAndUpdate(base.offset(-4, 0, 10), Blocks.ANVIL.defaultBlockState());
                    l.setBlockAndUpdate(base.offset(-5, 0, 11), Blocks.CHEST.defaultBlockState());
                    var smithChest = (net.minecraft.world.Container) l.getBlockEntity(base.offset(-5, 0, 11));
                    smithChest.setItem(0, new ItemStack(Items.IRON_PICKAXE)); smithChest.setItem(1, new ItemStack(Items.IRON_PICKAXE));
                    smithChest.setItem(2, new ItemStack(Items.ANCIENT_DEBRIS, 4)); smithChest.setItem(3, new ItemStack(Items.GOLD_INGOT, 4));
                    smithChest.setItem(4, new ItemStack(Items.DIAMOND_SWORD)); smithChest.setItem(5, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
                    smithChest.setItem(6, new ItemStack(Items.COAL, 4));
                    var smith = (WorkPostBlockEntity) l.getBlockEntity(smithy);
                    smith.claim(p.getUUID(), p.getGameProfile().getName());
                    smith.setJob(Serfdom.id("blacksmith"), 4);
                    smith.setStock(new Stock(List.of(new Stock.Row("minecraft:iron_pickaxe", 2), new Stock.Row("minecraft:iron_sword", 1), new Stock.Row("minecraft:netherite_sword", 1))));
                    // The kitchen, right: a smoker, a table, a pot on a lit campfire, a cutting board.
                    kitchen = base.offset(5, 0, 8);
                    l.setBlockAndUpdate(kitchen, Serfdom.WORK_POST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    l.setBlockAndUpdate(base.offset(3, 0, 10), Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    l.setBlockAndUpdate(base.offset(4, 0, 10), Blocks.CRAFTING_TABLE.defaultBlockState());
                    l.setBlockAndUpdate(base.offset(6, 0, 10), Blocks.CAMPFIRE.defaultBlockState());
                    l.setBlockAndUpdate(base.offset(6, 1, 10), net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("farmersdelight:cooking_pot")).defaultBlockState());
                    l.setBlockAndUpdate(base.offset(7, 0, 10), net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.parse("farmersdelight:cutting_board")).defaultBlockState());
                    l.setBlockAndUpdate(base.offset(5, 0, 11), Blocks.CHEST.defaultBlockState());
                    var kitchenChest = (net.minecraft.world.Container) l.getBlockEntity(base.offset(5, 0, 11));
                    kitchenChest.setItem(0, new ItemStack(Items.WHEAT, 6)); kitchenChest.setItem(1, new ItemStack(Items.BEEF, 4));
                    var cook = (WorkPostBlockEntity) l.getBlockEntity(kitchen);
                    cook.claim(p.getUUID(), p.getGameProfile().getName());
                    cook.setJob(Serfdom.id("cooking"), 4);
                    cook.setStock(new Stock(List.of(new Stock.Row("minecraft:bread", 2), new Stock.Row("minecraft:cooked_beef", 4), new Stock.Row("farmersdelight:minced_beef", 2))));
                    // Within the screens' reach of both posts (eight blocks), the stations in view.
                    p.teleportTo(l, base.getX() + 0.5, base.getY(), base.getZ() + 3.5, 0F, 12F);
                });
                case 240 -> {
                    photo(mc, "07-smithy-and-kitchen");
                    server(mc, p -> Screens.openPost(p, (WorkPostBlockEntity) p.serverLevel().getBlockEntity(smithy)));
                }
                case 260 -> {
                    var screen = screen(mc, PostScreen.class, "the smithy's post screen opens");
                    check(Screens.jobName(screen.view().job()).getString().equals("Blacksmith") && screen.view().rows().size() == 3, "a blacksmith's post with three rows: " + screen.view().rows());
                    photo(mc, "08-smithy-post");
                    click(screen, "Stock list (3)...");
                }
                case 280 -> {
                    var screen = screen(mc, StockScreen.class, "the smithy's stock list opens");
                    var rows = screen.view().rows();
                    check(rows.get(0).status() == Workshop.Status.MET.ordinal() && rows.get(0).have() == 2, "two pickaxes stocked: " + rows.get(0));
                    check(rows.get(1).status() == Workshop.Status.SHORT.ordinal() && rows.get(1).detail().getString().startsWith("Short of 2 × Iron Ingot"), "the sword is short of iron: " + rows.get(1).detail().getString());
                    check(rows.get(2).status() == Workshop.Status.NO_STATION.ordinal() && rows.get(2).detail().getString().equals("Needs a smithing table"), "the netherite sword wants a smithing table: " + rows.get(2).detail().getString());
                    photo(mc, "09-smithy-stock");
                    mc.setScreen(null);
                    server(mc, p -> Screens.openPost(p, (WorkPostBlockEntity) p.serverLevel().getBlockEntity(kitchen)));
                }
                case 300 -> click(screen(mc, PostScreen.class, "the kitchen's post screen opens"), "Stock list (3)...");
                case 320 -> {
                    var screen = screen(mc, StockScreen.class, "the kitchen's stock list opens");
                    var rows = screen.view().rows();
                    check(rows.get(0).status() == Workshop.Status.MAKING.ordinal(), "bread can be made now: " + rows.get(0).detail().getString());
                    check(rows.get(1).status() == Workshop.Status.NO_FUEL.ordinal(), "steaks wait on fuel: " + rows.get(1).detail().getString());
                    check(rows.get(2).status() == Workshop.Status.NO_TOOL.ordinal(), "minced beef waits on a knife: " + rows.get(2).detail().getString());
                    photo(mc, "10-kitchen-stock");
                    click(screen, "Add...");
                }
                case 340 -> {
                    var picker = screen(mc, PickerScreen.class, "the picker opens");
                    for (char c : "stew".toCharArray()) picker.charTyped(c, 0);
                }
                case 360 -> {
                    var picker = screen(mc, PickerScreen.class, "the picker is still open");
                    photo(mc, "11-picker-stew");
                    check(picker.shownIds().contains("farmersdelight:beef_stew") && picker.shownIds().stream().allMatch(id -> id.contains("stew")), "the search finds the stews: " + picker.shownIds());
                    check(picker.clickFirst(), "the first stew is clicked");
                }
                case 380 -> {
                    server(mc, p -> {
                        var rows = ((WorkPostBlockEntity) p.serverLevel().getBlockEntity(kitchen)).stock().rows();
                        check(rows.size() == 4 && rows.get(3).item().contains("stew"), "the server added the stew: " + rows);
                    });
                    check(mc.screen instanceof StockScreen, "back on the list: " + mc.screen);
                }
                case 400 -> {
                    var screen = screen(mc, StockScreen.class, "the list shows the new row");
                    check(screen.view().rows().size() == 4, "four rows: " + screen.view().rows());
                    photo(mc, "12-kitchen-stock-added");
                    mc.setScreen(null);
                }
                case 420 -> server(mc, p -> {
                    var l = p.serverLevel();
                    var base = BlockPos.containing(p.getX(), p.getY(), p.getZ()).offset(40, 0, 0);
                    for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                        l.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                        for (int y = 0; y < 5; y++) l.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    var v = EntityType.VILLAGER.create(l);
                    v.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 2.0, 180F, 0F);
                    v.setYHeadRot(180F);
                    v.setYBodyRot(180F);
                    v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
                    v.setNoAi(true);
                    l.addFreshEntity(v);
                    captive = v.getId();
                    p.getInventory().clearContent();
                    p.getInventory().setItem(0, new ItemStack(Serfdom.CHAIN_LEAD.get(), 2));
                    p.getInventory().selected = 0;
                    // Level and as far back as the hold's reach allows, so the whole villager is in view.
                    p.teleportTo(l, base.getX() + 0.5, base.getY(), base.getZ() + 0.3, 0F, 4F);
                    capture = base;
                });
                case 440 -> {
                    // The use key held down, as a player holds it through the capture.
                    mc.options.keyUse.setDown(true);
                    server(mc, p -> p.interactOn(p.serverLevel().getEntity(captive), net.minecraft.world.InteractionHand.MAIN_HAND));
                }
                case 460 -> {
                    server(mc, p -> check(com.chunkworks.serfdom.Captures.holding(p, (Villager) p.serverLevel().getEntity(captive)), "the chain is held on the farmer"));
                    check(mc.player.isUsingItem(), "the client draws the chain");
                    photo(mc, "13-capture-hold");
                }
                case 500 -> {
                    mc.options.keyUse.setDown(false);
                    server(mc, p -> {
                        var w = Workers.of((Villager) p.serverLevel().getEntity(captive));
                        check(w.captive() && w.cuffed() && w.ownedBy(p.getUUID()), "the farmer is the player's captive, in chains: " + w);
                    });
                    var v = mc.level.getEntity(captive);
                    check(v instanceof Villager villager && Workers.cuffed(villager) && villager.isLeashed(), "the client sees the cuffs and the chain");
                    photo(mc, "14-captive-cuffed");
                    server(mc, p -> Screens.openWorker(p, (Villager) p.serverLevel().getEntity(captive)));
                }
                case 520 -> {
                    var screen = screen(mc, WorkerScreen.class, "the captive's Worker Screen opens");
                    check(screen.view().status() == Screens.Status.CUFFED && screen.freeButton().active, "in chains, Set free live: " + screen.view().status());
                    photo(mc, "15-captive-screen");
                    mc.setScreen(null);
                }
                case 540 -> server(mc, p -> {
                    var l = p.serverLevel();
                    var trailer = com.chunkworks.vanillawheels.Vehicle.create(l, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("trailer", "trailer"),
                            net.minecraft.world.phys.Vec3.atBottomCenterOf(capture.offset(0, 0, 8)), 180F);
                    check(trailer != null, "the trailer's profile is loaded");
                    l.addFreshEntity(trailer);
                    trailer.toggleDoors();
                    trailerId = trailer.getId();
                    var first = (Villager) l.getEntity(captive);
                    first.moveTo(capture.getX() + 0.5, capture.getY(), capture.getZ() + 4.5);
                    var aboard = new ArrayList<Villager>(List.of(first));
                    VillagerProfession[] trades = {VillagerProfession.FLETCHER, VillagerProfession.MASON, VillagerProfession.CLERIC};
                    for (int i = 0; i < 3; i++) {
                        var v = EntityType.VILLAGER.create(l);
                        v.moveTo(capture.getX() - 1.5 + 1.5 * i, capture.getY(), capture.getZ() + 3.0, 0F, 0F);
                        v.setVillagerData(v.getVillagerData().setProfession(trades[i]).setLevel(2));
                        v.setNoAi(true);
                        l.addFreshEntity(v);
                        Workers.capture(l, v, p, new ItemStack(Serfdom.CHAIN_LEAD.get()));
                        aboard.add(v);
                    }
                    aboardIds.clear();
                    for (var v : aboard) aboardIds.add(v.getId());
                    p.teleportTo(l, capture.getX() + 0.5, capture.getY(), capture.getZ() + 1.5, 0F, 10F);
                    p.getInventory().setItem(0, ItemStack.EMPTY);
                    check(trailer.interact(p, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(), "an empty-handed click loads");
                    check(trailer.cargoAboard().size() == 4, "four captives aboard: " + trailer.cargoAboard().size());
                    // Behind the trailer, which faces north, looking in through its open doors.
                    p.teleportTo(l, capture.getX() + 0.5, capture.getY() + 0.6, capture.getZ() + 13.5, 180F, 12F);
                });
                case 580 -> {
                    var t = mc.level.getEntity(trailerId);
                    check(t != null && t.getPassengers().size() == 4, "the client sees four aboard");
                    photo(mc, "16-trailer-rear");
                    server(mc, p -> p.teleportTo(p.serverLevel(), capture.getX() + 6.5, capture.getY() + 1.0, capture.getZ() + 8.5, 90F, 10F));
                }
                case 600 -> photo(mc, "17-trailer-side");
                // ---- 2b: armour on villagers (D-0004) ----
                case 620 -> server(mc, p -> {
                    var l = p.serverLevel();
                    lot = capture.offset(0, 0, 40);
                    var at = net.minecraft.world.phys.Vec3.atBottomCenterOf(lot);
                    for (int x = -10; x <= 30; x++) for (int z = -4; z <= 34; z++) {
                        l.setBlockAndUpdate(lot.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                        for (int y = 0; y < 5; y++) l.setBlockAndUpdate(lot.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    // The seven materials in a row, facing the camera.
                    VillagerProfession[] trades = {VillagerProfession.FARMER, VillagerProfession.FLETCHER, VillagerProfession.ARMORER, VillagerProfession.CLERIC,
                            VillagerProfession.LIBRARIAN, VillagerProfession.MASON, VillagerProfession.WEAPONSMITH};
                    for (int i = 0; i < 7; i++) stand(l, at.add(-5 + i * 1.5, 0, 6), 180F, trades[i], set(l, i));
                    // The close-up stage: one villager in iron, alone.
                    stage = stand(l, at.add(14, 0, 6), 180F, VillagerProfession.TOOLSMITH, set(l, 2)).getId();
                    // The robe and the hats: a chestplate alone, leggings alone, boots alone, a helmet alone, bare.
                    var iron = new ItemStack[]{new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS)};
                    stand(l, at.add(-5, 0, 14), 180F, VillagerProfession.FARMER, ItemStack.EMPTY, iron[1].copy());
                    stand(l, at.add(-2.5, 0, 14), 180F, VillagerProfession.FARMER, ItemStack.EMPTY, ItemStack.EMPTY, iron[2].copy());
                    stand(l, at.add(0, 0, 14), 180F, VillagerProfession.SHEPHERD, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, iron[3].copy());
                    stand(l, at.add(2.5, 0, 14), 180F, VillagerProfession.FARMER, iron[0].copy());
                    stand(l, at.add(5, 0, 14), 180F, VillagerProfession.FARMER);
                    // Heads on each trade: helmets over hats and hoods, a carved pumpkin, a zombie's head.
                    VillagerProfession[] hatted = {VillagerProfession.FISHERMAN, VillagerProfession.SHEPHERD, VillagerProfession.LIBRARIAN, VillagerProfession.BUTCHER,
                            VillagerProfession.CARTOGRAPHER, VillagerProfession.LEATHERWORKER, VillagerProfession.NITWIT};
                    ItemStack[] heads = {new ItemStack(Items.GOLDEN_HELMET), new ItemStack(Items.CHAINMAIL_HELMET), new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.CARVED_PUMPKIN),
                            new ItemStack(Items.ZOMBIE_HEAD), new ItemStack(Items.TURTLE_HELMET), dyed(Items.LEATHER_HELMET, 0x3B5BA5)};
                    for (int i = 0; i < 7; i++) stand(l, at.add(-5 + i * 1.5, 0, 20), 180F, hatted[i], heads[i]);
                    // Lucky's Wardrobe's clothes, worn where each says.
                    String[][] outfits = {{"farmer_hat", "leather_apron"}, {"top_hat", "snowy_coat", "snowy_pants", "snowy_boots"},
                            {"snowy_hood", "taiga_coat", "taiga_pants", "taiga_boots"}, {"desert_hat", "desert_robe", "desert_pants", "desert_sandals"}};
                    for (int i = 0; i < 4; i++) {
                        var v = stand(l, at.add(-4.5 + i * 3, 0, 26), 180F, i == 0 ? VillagerProfession.FARMER : VillagerProfession.NONE);
                        for (var path : outfits[i]) {
                            var stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("luckyswardrobe", path)));
                            check(!stack.isEmpty(), "Lucky's Wardrobe's " + path + " is registered");
                            v.setItemSlot(v.getEquipmentSlotForItem(stack), stack);
                        }
                    }
                    // A child in iron, a captive in chains in a chestplate and helmet, an elytra.
                    var child = stand(l, at.add(18, 0, 14), 180F, VillagerProfession.NONE, set(l, 2));
                    child.setAge(-24000);
                    var chained = stand(l, at.add(20.5, 0, 14), 180F, VillagerProfession.MASON);
                    Workers.capture(l, chained, p, new ItemStack(Serfdom.CHAIN_LEAD.get()));
                    chained.dropLeash(true, false);
                    chained.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    chained.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    stand(l, at.add(23, 0, 14), 180F, VillagerProfession.CLERIC, ItemStack.EMPTY, new ItemStack(Items.ELYTRA));
                    // Beside vanilla's own: a zombie villager in the same iron and the same diamond.
                    stand(l, at.add(17, 0, 22), 180F, VillagerProfession.FARMER, set(l, 2));
                    zombie(l, at.add(18.5, 0, 22), set(l, 2));
                    stand(l, at.add(21, 0, 22), 180F, VillagerProfession.FARMER, set(l, 4));
                    zombie(l, at.add(22.5, 0, 22), set(l, 4));
                    // The walker, side on.
                    walker = stand(l, at.add(16, 0, 30), -90F, VillagerProfession.FARMER, set(l, 4)).getId();
                    p.teleportTo(l, lot.getX() + 0.5, lot.getY(), lot.getZ() - 1.5, 0F, 5F);
                    mc.execute(() -> mc.options.hideGui = true);
                });
                case 660 -> {
                    var row = mc.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(lot.offset(-6, 0, 5)).expandTowards(12, 3, 2));
                    check(row.size() == 7 && row.stream().allMatch(v -> !v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()), "the client sees seven villagers in chestplates: " + row.size());
                    photo(mc, "18-armour-materials-front");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 0.5, lot.getY(), lot.getZ() + 12.5, 180F, 5F));
                }
                case 680 -> {
                    photo(mc, "19-armour-materials-back");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 14.5, lot.getY(), lot.getZ() + 4.2, 0F, 18F));
                }
                case 700 -> {
                    photo(mc, "20-armour-closeup-front");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 16.8, lot.getY(), lot.getZ() + 6.5, 90F, 18F));
                }
                case 720 -> {
                    photo(mc, "21-armour-closeup-side");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 14.5, lot.getY(), lot.getZ() + 8.8, 180F, 18F));
                }
                case 740 -> {
                    photo(mc, "22-armour-closeup-back");
                    server(mc, p -> {
                        var v = (Villager) p.serverLevel().getEntity(stage);
                        var worn = set(p.serverLevel(), 6);
                        for (int i = 0; i < 4; i++) v.setItemSlot(com.chunkworks.serfdom.WorkerMenu.WORN[i], worn[i]);
                        p.teleportTo(p.serverLevel(), lot.getX() + 13.3, lot.getY(), lot.getZ() + 4.4, -35F, 16F);
                    });
                }
                case 760 -> {
                    photo(mc, "23-armour-closeup-trimmed");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 0.5, lot.getY(), lot.getZ() + 9.8, 0F, 8F));
                }
                case 780 -> {
                    photo(mc, "24-robe-rule");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 0.5, lot.getY(), lot.getZ() + 15.8, 0F, 8F));
                }
                case 800 -> {
                    photo(mc, "25-heads-on-trades");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 0.5, lot.getY(), lot.getZ() + 21.8, 0F, 8F));
                }
                case 820 -> {
                    photo(mc, "26-wardrobe-front");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 0.5, lot.getY(), lot.getZ() + 30.2, 180F, 8F));
                }
                case 840 -> {
                    photo(mc, "27-wardrobe-back");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 20.5, lot.getY(), lot.getZ() + 9.8, 0F, 8F));
                }
                case 860 -> {
                    var cuffed = mc.level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(lot.offset(20, 0, 14)).inflate(0.6)).stream().filter(Workers::cuffed).count();
                    check(cuffed == 1, "the client sees the captive in chains");
                    photo(mc, "28-child-captive-elytra");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 20.5, lot.getY(), lot.getZ() + 18.2, 180F, 8F));
                }
                case 880 -> {
                    photo(mc, "29-elytra-back");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 20.0, lot.getY(), lot.getZ() + 18.0, 0F, 8F));
                }
                case 900 -> {
                    photo(mc, "30-beside-zombie-villagers");
                    server(mc, p -> p.teleportTo(p.serverLevel(), lot.getX() + 19.5, lot.getY(), lot.getZ() + 26.5, 0F, 8F));
                }
                case 950 -> photo(mc, "31-walking");
                case 970 -> server(mc, p -> {
                    var l = p.serverLevel();
                    for (int i = 0; i < aboardIds.size(); i++) {
                        var v = (Villager) l.getEntity(aboardIds.get(i));
                        var worn = set(l, i + 1);
                        for (int j = 0; j < 4; j++) v.setItemSlot(com.chunkworks.serfdom.WorkerMenu.WORN[j], worn[j]);
                    }
                    p.teleportTo(l, capture.getX() + 0.5, capture.getY() + 0.6, capture.getZ() + 13.5, 180F, 12F);
                });
                case 1000 -> {
                    photo(mc, "32-trailer-armoured");
                    mc.options.hideGui = false;
                    server(mc, p -> {
                        var l = p.serverLevel();
                        var v = (Villager) l.getEntity(stage);
                        Workers.hire(l, v, p, Optional.empty());
                        p.teleportTo(l, lot.getX() + 14.5, lot.getY(), lot.getZ() + 3.5, 0F, 10F);
                        Screens.openWorker(p, v);
                    });
                }
                case 1030 -> {
                    var screen = screen(mc, WorkerScreen.class, "the dressed worker's screen opens");
                    check(screen.view() != null && screen.getMenu().villager() != null && screen.getMenu().villager().getId() == stage, "the view came and bound the menu");
                    check(screen.getMenu().slots.get(com.chunkworks.serfdom.WorkerMenu.WEARING + 1).getItem().is(Items.IRON_CHESTPLATE), "the client's chest slot holds the trimmed chestplate");
                    photo(mc, "33-worker-screen-dressed");
                    mc.setScreen(null);
                }
                // ---- 3: meals (D-0005) ----
                case 1050 -> server(mc, p -> {
                    var l = p.serverLevel();
                    com.chunkworks.serfdom.SerfdomConfig.HUNGER.set(true);
                    kitchenLot = lot.offset(44, 0, 0);
                    for (int x = -8; x <= 10; x++) for (int z = -3; z <= 12; z++) {
                        l.setBlockAndUpdate(kitchenLot.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                        for (int y = 0; y < 5; y++) l.setBlockAndUpdate(kitchenLot.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    var foot = net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
                    // Beds to the north, chests and the smoker to the south, the camera beyond them looking
                    // back north: the workers face it as they eat at their chests and wait by the smoker.
                    // The eater: a bed, a chest of bread.
                    var bedA = kitchenLot.offset(-1, 0, -1);
                    l.setBlockAndUpdate(bedA.south(), foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                    l.setBlockAndUpdate(bedA, foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    l.setBlockAndUpdate(kitchenLot.offset(-1, 0, 5), Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    ((net.minecraft.world.Container) l.getBlockEntity(kitchenLot.offset(-1, 0, 5))).setItem(0, new ItemStack(Items.BREAD, 8));
                    // The griller: a bed, a chest of raw beef and coal, a smoker.
                    var bedB = kitchenLot.offset(5, 0, -1);
                    l.setBlockAndUpdate(bedB.south(), foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
                    l.setBlockAndUpdate(bedB, foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    l.setBlockAndUpdate(kitchenLot.offset(6, 0, 5), Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                    var larder = (net.minecraft.world.Container) l.getBlockEntity(kitchenLot.offset(6, 0, 5));
                    larder.setItem(0, new ItemStack(Items.BEEF, 4));
                    larder.setItem(1, new ItemStack(Items.COAL, 2));
                    smokerAt = kitchenLot.offset(3, 0, 5);
                    // Its fire toward the camera.
                    l.setBlockAndUpdate(smokerAt, Blocks.SMOKER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
                });
                // A few ticks on: vanilla registers a bed as a home in a task of its own after placing it.
                case 1056 -> server(mc, p -> {
                    var l = p.serverLevel();
                    var bedA = kitchenLot.offset(-1, 0, -1);
                    var bedB = kitchenLot.offset(5, 0, -1);
                    for (int i = 0; i < 2; i++) {
                        var v = EntityType.VILLAGER.create(l);
                        v.moveTo(kitchenLot.getX() + (i == 0 ? -0.5 : 4.5), kitchenLot.getY(), kitchenLot.getZ() + 1.5, 0F, 0F);
                        v.setVillagerData(v.getVillagerData().setProfession(i == 0 ? VillagerProfession.FARMER : VillagerProfession.BUTCHER).setLevel(2));
                        v.setPersistenceRequired();
                        l.addFreshEntity(v);
                        Workers.hire(l, v, p, Optional.empty());
                        check(Workers.assignBed(l, v, i == 0 ? bedA : bedB) == Workers.Picked.OK, "a bed for the " + (i == 0 ? "eater" : "griller"));
                        com.chunkworks.serfdom.Appetite.set(v, new com.chunkworks.serfdom.Appetite.Belly(new com.chunkworks.serfdom.domain.Hunger(i == 0 ? 5 : 4),
                                com.chunkworks.serfdom.domain.Meals.Times.NONE));
                        if (i == 0) eater = v.getId(); else griller = v.getId();
                    }
                    p.teleportTo(l, kitchenLot.getX() + 2.5, kitchenLot.getY() + 0.4, kitchenLot.getZ() + 9.0, 180F, 14F);
                    mc.execute(() -> mc.options.hideGui = true);
                });
                case 1500 -> {
                    check(ateShot, "a worker was photographed eating");
                    check(cookShot, "a worker was photographed by its lit smoker");
                    mc.options.hideGui = false;
                    server(mc, p -> {
                        var l = p.serverLevel();
                        var v = (Villager) l.getEntity(eater);
                        com.chunkworks.serfdom.Appetite.set(v, new com.chunkworks.serfdom.Appetite.Belly(new com.chunkworks.serfdom.domain.Hunger(13.5),
                                com.chunkworks.serfdom.Appetite.of(v).times()));
                        // Fed, it wanders; the screen holds only within eight blocks of it.
                        v.setNoAi(true);
                        p.teleportTo(l, v.getX(), v.getY(), v.getZ() + 3.0, 180F, 10F);
                        Screens.openWorker(p, v);
                    });
                }
                case 1530 -> {
                    var screen = screen(mc, WorkerScreen.class, "the eater's screen opens");
                    check(screen.view() != null && screen.view().hunger() == 14, "fourteen half drumsticks: " + (screen.view() == null ? "no view" : screen.view().hunger()));
                    photo(mc, "36-worker-screen-hunger");
                    mc.setScreen(null);
                }
                case 1550 -> {
                    LOG.info("serfdom booth: COMPLETE");
                    mc.stop();
                }
                default -> {
                    if (tick > 1060 && tick < 1500) meals(mc);
                    if (tick > 905 && tick < 950) server(mc, p -> {
                        var v = p.serverLevel().getEntity(walker);
                        if (v != null) v.setPos(v.getX() + 0.12, v.getY(), v.getZ());
                    });
                }
            }
        } catch (Throwable failure) { LOG.error("serfdom booth: FAIL", failure); mc.stop(); }
    }

    /** effects: a villager standing still at {@code at}, facing {@code yaw}, at the trade, wearing
     * the pieces head first. */
    private static Villager stand(net.minecraft.server.level.ServerLevel l, net.minecraft.world.phys.Vec3 at, float yaw, VillagerProfession trade, ItemStack... worn) {
        var v = EntityType.VILLAGER.create(l);
        v.moveTo(at.x, at.y, at.z, yaw, 0F);
        v.setYHeadRot(yaw);
        v.setYBodyRot(yaw);
        v.setVillagerData(v.getVillagerData().setProfession(trade).setLevel(2));
        v.setNoAi(true);
        v.setPersistenceRequired();
        for (int i = 0; i < worn.length; i++) if (!worn[i].isEmpty()) v.setItemSlot(com.chunkworks.serfdom.WorkerMenu.WORN[i], worn[i]);
        l.addFreshEntity(v);
        return v;
    }

    /** effects: a zombie villager standing still at {@code at}, facing the camera, wearing the pieces. */
    private static void zombie(net.minecraft.server.level.ServerLevel l, net.minecraft.world.phys.Vec3 at, ItemStack... worn) {
        var z = EntityType.ZOMBIE_VILLAGER.create(l);
        z.moveTo(at.x, at.y, at.z, 180F, 0F);
        z.setYHeadRot(180F);
        z.setYBodyRot(180F);
        z.setNoAi(true);
        z.setPersistenceRequired();
        for (int i = 0; i < worn.length; i++) if (!worn[i].isEmpty()) z.setItemSlot(com.chunkworks.serfdom.WorkerMenu.WORN[i], worn[i]);
        l.addFreshEntity(z);
    }

    /** effects: the booth's armour set {@code n}, head first: 0 leather dyed brown, 1 chainmail,
     * 2 iron, 3 gold, 4 diamond, 5 netherite, 6 a turtle shell, an iron chestplate trimmed in gold,
     * enchanted diamond leggings and dyed leather boots. */
    private static ItemStack[] set(net.minecraft.server.level.ServerLevel l, int n) {
        return switch (n) {
            case 0 -> new ItemStack[]{dyed(Items.LEATHER_HELMET, 0x7A4A26), dyed(Items.LEATHER_CHESTPLATE, 0x7A4A26), dyed(Items.LEATHER_LEGGINGS, 0x7A4A26), dyed(Items.LEATHER_BOOTS, 0x7A4A26)};
            case 1 -> pieces(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
            case 2 -> pieces(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case 3 -> pieces(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS);
            case 4 -> pieces(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            case 5 -> pieces(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
            default -> {
                var chest = new ItemStack(Items.IRON_CHESTPLATE);
                var registries = l.registryAccess();
                chest.set(net.minecraft.core.component.DataComponents.TRIM, new net.minecraft.world.item.armortrim.ArmorTrim(
                        registries.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_MATERIAL).getHolderOrThrow(net.minecraft.world.item.armortrim.TrimMaterials.GOLD),
                        registries.registryOrThrow(net.minecraft.core.registries.Registries.TRIM_PATTERN).getHolderOrThrow(net.minecraft.world.item.armortrim.TrimPatterns.COAST)));
                var legs = new ItemStack(Items.DIAMOND_LEGGINGS);
                legs.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                yield new ItemStack[]{new ItemStack(Items.TURTLE_HELMET), chest, legs, dyed(Items.LEATHER_BOOTS, 0x2E6B3A)};
            }
        };
    }
    private static ItemStack[] pieces(net.minecraft.world.item.Item... items) {
        var out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) out[i] = new ItemStack(items[i]);
        return out;
    }
    private static ItemStack dyed(net.minecraft.world.item.Item item, int rgb) {
        var stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(rgb, true));
        return stack;
    }

    /** effects: the meal photographs, each taken the first tick the client sees it: the eater with
     * bread in its hand at its chest, the griller standing by its lit smoker. */
    private static void meals(Minecraft mc) {
        if (!ateShot && mc.level.getEntity(eater) instanceof Villager v && v.getMainHandItem().is(Items.BREAD)) {
            photo(mc, "34-eating");
            ateShot = true;
        }
        var smoker = mc.level.getBlockState(smokerAt);
        boolean lit = smoker.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) && smoker.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT);
        if (!lit) litSeen = 0;
        else if (litSeen == 0) litSeen = tick;
        // Ten ticks after the fire is first seen: the world's mesh is redrawn a few frames after a block changes.
        if (!cookShot && lit && tick >= litSeen + 10 && mc.level.getEntity(griller) instanceof Villager g && g.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(smokerAt)) < 9) {
            photo(mc, "35-cooking");
            cookShot = true;
        }
    }

    /** effects: clicks the screen's button labelled {@code label}. */
    private static void click(Screen screen, String label) {
        var button = screen.children().stream().filter(c -> c instanceof Button b && b.getMessage().getString().equals(label)).map(c -> (Button) c).findFirst()
                .orElseThrow(() -> new IllegalStateException("no button " + label + " on " + screen));
        check(screen.mouseClicked(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0, 0), "the click lands on " + label);
    }
    private static <S extends Screen> S screen(Minecraft mc, Class<S> type, String what) {
        check(type.isInstance(mc.screen), what + ": " + mc.screen);
        return type.cast(mc.screen);
    }
    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> { try { action.accept(server.getPlayerList().getPlayer(id)); } catch (Throwable failure) { LOG.error("serfdom booth: FAIL", failure); mc.execute(mc::stop); } });
    }
    private static void photo(Minecraft mc, String name) {
        mc.getToasts().clear();
        Screenshot.grab(mc.gameDirectory, "serfdom-" + name + ".png", mc.getMainRenderTarget(), m -> LOG.info("serfdom booth: {}", m.getString()));
    }
    private static void check(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); LOG.info("serfdom booth: PASS {}", message); }
}
