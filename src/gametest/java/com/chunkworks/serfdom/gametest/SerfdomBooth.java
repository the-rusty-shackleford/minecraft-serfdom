/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.client.PostScreen;
import com.chunkworks.serfdom.client.WorkerScreen;
import com.chunkworks.serfdom.domain.Need;
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

/** The booth (D-0001), silent, software-rendered, graded by eye from its photographs: a Work Post
 * facing the camera with its outline on and five workers in a row behind it, each showing one
 * need; the Worker Screen of the first; the post's screen, with a click on [+] that the server
 * takes; a worker on the chain lead; the post and the chain in the inventory. Every step also
 * checks in code what it can. This fixture never ships. */
@EventBusSubscriber(modid = "serfdom_gametest", value = Dist.CLIENT)
public final class SerfdomBooth {
    private static final Logger LOG = LoggerFactory.getLogger("Serfdom booth");
    private static int tick;
    private static final List<Integer> workers = new ArrayList<>();
    private static BlockPos post;

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
                        v.moveTo(base.getX() - 4 + 2 * i + 0.5, base.getY(), base.getZ() + 5.5, 180F, 0F);
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
                    check(shown.equals(List.of("NO_BED", "NO_TOOL", "NO_FUEL", "NO_MATERIALS", "CHEST_FULL")), "the client sees each worker's need: " + shown);
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
                        v.setLeashedTo(p, true);
                        p.teleportTo(p.serverLevel(), p.getX(), p.getY(), p.getZ(), -30F, 20F);
                    });
                }
                case 150 -> {
                    var v = mc.level.getEntity(workers.get(2));
                    check(v instanceof Villager villager && villager.isLeashed(), "the client sees the worker on the chain");
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
                case 200 -> {
                    LOG.info("serfdom booth: COMPLETE");
                    mc.stop();
                }
                default -> {}
            }
        } catch (Throwable failure) { LOG.error("serfdom booth: FAIL", failure); mc.stop(); }
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
