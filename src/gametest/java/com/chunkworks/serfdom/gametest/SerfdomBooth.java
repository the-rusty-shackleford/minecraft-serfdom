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
 * its side. Every step also checks in code what it can. This fixture never ships. */
@EventBusSubscriber(modid = "serfdom_gametest", value = Dist.CLIENT)
public final class SerfdomBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Serfdom booth");
    private static int tick;
    private static final List<Integer> workers = new ArrayList<>();
    private static BlockPos post, smithy, kitchen, capture;
    private static int captive, trailerId;

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
                        v.moveTo(base.getX() - 5 + 2 * i + 0.5, base.getY(), base.getZ() + 5.5, 180F, 0F);
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
                    check(shown.equals(List.of("NO_BED", "NO_TOOL", "NO_STATION", "NO_FUEL", "NO_MATERIALS", "CHEST_FULL")), "the client sees each worker's need: " + shown);
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
                case 620 -> {
                    LOG.info("serfdom booth: COMPLETE");
                    mc.stop();
                }
                default -> {}
            }
        } catch (Throwable failure) { LOG.error("serfdom booth: FAIL", failure); mc.stop(); }
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
