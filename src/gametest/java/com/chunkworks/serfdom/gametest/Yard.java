/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.gametest;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** The tests' ground and people: a grass yard, chests, beds, a Work Post, a grown oak, mock players
 * that join the server as a real one does, villagers with their brains running, and the two
 * gestures a player makes, done the way the server's packet handler does them. */
final class Yard {
    static final int SIZE = 48;
    private Yard() {}

    /** effects: a grass floor across the yard at y 0, stone beneath. */
    static void floor(GameTestHelper h) {
        for (int x = 0; x < SIZE; x++) for (int z = 0; z < SIZE; z++) h.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
    }

    static BlockPos at(GameTestHelper h, int x, int y, int z) { return h.absolutePos(new BlockPos(x, y, z)); }

    /** effects: a mock survival player at the yard position, carrying {@code holding}. */
    static ServerPlayer player(GameTestHelper h, int x, int z, String name, ItemStack... holding) {
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
        var at = at(h, x, 1, z);
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        // Carried in the main inventory, never the hand: the hand stays empty for the gestures.
        for (int i = 0; i < holding.length; i++) player.getInventory().setItem(9 + i, holding[i]);
        return player;
    }

    /** effects: a survival fake player wearing an expedition bag that holds the cells. */
    static ServerPlayer bagged(GameTestHelper h, int x, int z, String name, ItemStack... cells) {
        var bag = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("backpacksplus:expedition_backpack")));
        h.assertTrue(!bag.is(Items.AIR), "Backpacks+'s expedition bag is registered");
        bag.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(cells)));
        var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.getInventory().clearContent();
        p.getInventory().setItem(38, bag);
        var at = at(h, x, 1, z);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    /** effects: an adult villager at the yard position with its brain running. */
    static Villager villager(GameTestHelper h, int x, int z, VillagerProfession profession, int level) {
        var villager = EntityType.VILLAGER.create(h.getLevel());
        var at = at(h, x, 1, z);
        villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        villager.setPersistenceRequired();
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(level));
        h.getLevel().addFreshEntity(villager);
        return villager;
    }

    /** effects: {@code player} uses {@code target} with the main hand as the server's packet
     * handler does; NeoForge fires the use event inside {@code interactOn}. */
    static InteractionResult use(ServerPlayer player, Entity target) {
        return player.interactOn(target, InteractionHand.MAIN_HAND);
    }

    /** effects: {@code player} right-clicks the block at {@code pos} as the packet handler does,
     * and says whether the click event was taken. */
    static boolean click(ServerPlayer player, BlockPos pos) {
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var event = CommonHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        if (!event.isCanceled()) player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        return event.isCanceled();
    }

    /** effects: runs a command as the player. */
    static void command(ServerPlayer player, String command) {
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    /** effects: a red bed whose head is at the yard position, its foot to the south; returns the head. */
    static BlockPos bed(GameTestHelper h, int x, int z) {
        var head = at(h, x, 1, z);
        var state = Blocks.RED_BED.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        h.getLevel().setBlock(head.south(), state.setValue(BedBlock.PART, BedPart.FOOT), 3);
        h.getLevel().setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return head;
    }

    /** effects: a chest at the yard position holding the stacks; returns its position. */
    static BlockPos chest(GameTestHelper h, int x, int z, ItemStack... stacks) {
        var pos = at(h, x, 1, z);
        h.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (Container) h.getLevel().getBlockEntity(pos);
        for (int i = 0; i < stacks.length; i++) chest.setItem(i, stacks[i]);
        return pos;
    }

    /** effects: how many of {@code item} the container at {@code pos} holds. */
    static int count(GameTestHelper h, BlockPos pos, Item item) {
        var c = (Container) h.getLevel().getBlockEntity(pos);
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    /** effects: a Work Post at the yard position owned by {@code owner}, with the job and radius. */
    static WorkPostBlockEntity post(GameTestHelper h, int x, int z, ServerPlayer owner, String job, int radius) {
        var pos = at(h, x, 1, z);
        h.getLevel().setBlock(pos, Serfdom.WORK_POST.get().defaultBlockState(), 3);
        var post = (WorkPostBlockEntity) h.getLevel().getBlockEntity(pos);
        post.claim(owner.getUUID(), owner.getGameProfile().getName());
        post.setJob(Serfdom.id(job), radius);
        return post;
    }

    /** effects: {@code owner}'s worker at the yard position, with the bed and the post given, as
     * the Worker Screen and two clicks would give them. */
    static Villager worker(GameTestHelper h, int x, int z, VillagerProfession profession, ServerPlayer owner, BlockPos bed, WorkPostBlockEntity post) {
        var v = villager(h, x, z, profession, 1);
        Workers.hire(h.getLevel(), v, owner, Optional.empty());
        h.assertTrue(Workers.assignBed(h.getLevel(), v, bed) == Workers.Picked.OK, "the bed is given");
        if (post != null) h.assertTrue(Workers.link(h.getLevel(), v, owner, post.getBlockPos()) == Workers.Picked.OK, "the post is linked");
        return v;
    }

    /** effects: grows a vanilla oak, the same one every time, rooted at the yard position. */
    static BlockPos oak(GameTestHelper h, int x, int z) {
        var root = at(h, x, 1, z);
        var level = h.getLevel();
        var feature = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolderOrThrow(TreeFeatures.OAK).value();
        h.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(42L), root), "the oak grew");
        return root;
    }

    /** effects: {@code block} placed at the yard position; returns its position. */
    static BlockPos block(GameTestHelper h, int x, int z, Block block) {
        var pos = at(h, x, 1, z);
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        return pos;
    }

    /** effects: how many of {@code item} the containers at {@code chests} hold together. */
    static int count(GameTestHelper h, Item item, BlockPos... chests) {
        int n = 0;
        for (var c : chests) n += count(h, c, item);
        return n;
    }

    /** effects: the hour set and held: daylight stops. A test in a batch can start well after the
     * batch began, and the GameTest server runs thousands of ticks a second, so with daylight
     * running a late test found the day nearly over (a smith with 1077 ticks of shift left). Hunger
     * is off for the batch (D-0005): these batches test other rules, and most hold an hour inside
     * the breakfast window, where a worker would sit down to eat whatever food lies near its bed,
     * the cook's counted stock among it. {@link #hourWithHunger} is the meal batches'. The economy is
     * off too (D-0006): GameTest leaves earlier tests' areas standing, and a worker with an emerald and
     * no food would walk off to a stall another batch left behind. {@link #economy} turns it on. */
    static void hour(net.minecraft.server.level.ServerLevel level, long time) {
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(time);
        com.chunkworks.serfdom.SerfdomConfig.HUNGER.set(false);
        com.chunkworks.serfdom.SerfdomConfig.ECONOMY.set(false);
    }

    /** effects: the economy on, for the market's batches (D-0006). */
    static void economy() { com.chunkworks.serfdom.SerfdomConfig.ECONOMY.set(true); }

    /** effects: the hour set and held, with hunger on. */
    static void hourWithHunger(net.minecraft.server.level.ServerLevel level, long time) {
        hour(level, time);
        com.chunkworks.serfdom.SerfdomConfig.HUNGER.set(true);
    }

    /** effects: true iff the block at {@code pos} is {@code block}. */
    static boolean is(GameTestHelper h, BlockPos pos, Block block) { return h.getLevel().getBlockState(pos).is(block); }
}
