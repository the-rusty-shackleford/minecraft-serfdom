/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.PlacedLogs;
import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.domain.Felling;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;

/** One tree coming down (D-0001): each log in turn, top first, at the time a player with the same
 * axe takes, the crack drawn as it goes; then its leaves, a few a tick; then saplings on the
 * trunk's base. Drops go into the worker's inventory, and a log whose drops would not fit waits
 * until the worker has emptied it. */
final class WoodTask implements Job.Task {
    /** The sapling each kind of log's leaves were last seen to drop, so a tree whose leaves dropped
     * none is still replanted from the worker's pocket. */
    private static final Map<String, Item> SAPLINGS = new ConcurrentHashMap<>();
    private final Felling.Tree tree;
    private final BlockPos base;
    private int log, leaf, left = -1, total;

    WoodTask(Felling.Tree tree) {
        this.tree = tree;
        this.base = Woodcutting.pos(tree.base());
    }

    @Override public BlockPos stand() { return base; }
    @Override public int reach() { return 3; }
    @Override public BlockPos key() { return base; }

    @Override public Job.Step tick(ServerLevel level, Villager worker, double speed) {
        var tool = worker.getItemBySlot(EquipmentSlot.MAINHAND);
        var logs = tree.logs();
        while (log < logs.size()) {
            var pos = Woodcutting.pos(logs.get(log));
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS) || !BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(tree.species())) { log++; left = -1; continue; }
            if (left < 0) {
                total = Tools.ticks(level, pos, state, tool, speed);
                left = total;
            }
            if (--left > 0) {
                level.destroyBlockProgress(worker.getId(), pos, Math.min(9, 10 * (total - left) / total));
                return Job.Step.WORKING;
            }
            List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, tool);
            if (!Storage.fits(worker.getInventory(), drops)) { left = 0; return Job.Step.FULL; }
            level.destroyBlockProgress(worker.getId(), pos, -1);
            level.destroyBlock(pos, false, worker);
            PlacedLogs.forget(level, pos);
            Storage.add(worker.getInventory(), drops);
            log++;
            left = -1;
            if (!tool.isEmpty()) {
                tool.hurtAndBreak(1, level, worker, item -> worker.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
                if (worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return Job.Step.PAUSE;
            }
            return Job.Step.WORKING;
        }
        var leaves = tree.leaves();
        int budget = SerfdomConfig.LEAVES_PER_TICK.get();
        while (leaf < leaves.size() && budget > 0) {
            var pos = Woodcutting.pos(leaves.get(leaf));
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES) || !state.hasProperty(LeavesBlock.PERSISTENT) || state.getValue(LeavesBlock.PERSISTENT)) { leaf++; continue; }
            var drops = Block.getDrops(state, level, pos, null, worker, ItemStack.EMPTY);
            if (!Storage.fits(worker.getInventory(), drops)) return Job.Step.FULL;
            for (var d : drops) if (d.is(ItemTags.SAPLINGS)) SAPLINGS.put(tree.species(), d.getItem());
            level.destroyBlock(pos, false, worker);
            Storage.add(worker.getInventory(), drops);
            leaf++;
            budget--;
        }
        if (leaf < leaves.size()) return Job.Step.WORKING;
        replant(level, worker);
        return Job.Step.DONE;
    }

    /** effects: plants a sapling of the tree's kind on each base cell that is empty above soil, as
     * long as the worker carries one. */
    private void replant(ServerLevel level, Villager worker) {
        var sapling = SAPLINGS.getOrDefault(tree.species(), named(tree.species()));
        if (sapling == null || !(sapling instanceof BlockItem item)) return;
        var inventory = worker.getInventory();
        for (var cell : tree.replant()) {
            var pos = Woodcutting.pos(cell);
            var state = item.getBlock().defaultBlockState();
            if (!level.getBlockState(pos).isAir() || !state.canSurvive(level, pos)) continue;
            int slot = -1;
            for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(sapling)) { slot = i; break; }
            if (slot < 0) return;
            level.setBlockAndUpdate(pos, state);
            inventory.getItem(slot).shrink(1);
        }
    }

    /** effects: the sapling named after a log the way vanilla and most mods name them
     * ({@code oak_log} to {@code oak_sapling}), if there is one; null otherwise. */
    static Item named(String species) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(species.replace("_log", "_sapling"));
        if (id == null || !species.endsWith("_log")) return null;
        var item = BuiltInRegistries.ITEM.get(id);
        return item.builtInRegistryHolder().is(ItemTags.SAPLINGS) ? item : null;
    }

    @Override public void abandon(ServerLevel level, Villager worker) {
        if (log < tree.logs().size()) level.destroyBlockProgress(worker.getId(), Woodcutting.pos(tree.logs().get(log)), -1);
        left = -1;
    }
}
