/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

/** A round of harvesting (D-0001): the targets in walking order, each taken when the worker stands
 * by it, a crop replanted from its own harvest; each harvest wears the hoe by one. */
final class CropTask implements Job.Task {
    private final List<BlockPos> targets;
    private int next, left = -1;

    CropTask(List<BlockPos> targets) { this.targets = List.copyOf(targets); }

    @Override public BlockPos stand() { return targets.get(Math.min(next, targets.size() - 1)); }
    @Override public int reach() { return 2; }
    @Override public BlockPos key() { return stand(); }

    @Override public Job.Step tick(ServerLevel level, Villager worker, double speed) {
        while (next < targets.size()) {
            var pos = targets.get(next);
            var state = level.getBlockState(pos);
            boolean ripe = Farming.crop(state) ? Farming.target(level, pos, state).filter(pos::equals).isPresent() : Farming.fruit(level, pos);
            if (!ripe) { next++; left = -1; continue; }
            if (!near(worker.blockPosition(), pos)) return Job.Step.MOVE;
            if (left < 0) left = Tools.quick(speed);
            if (--left > 0) return Job.Step.WORKING;
            var tool = worker.getItemBySlot(EquipmentSlot.MAINHAND);
            var drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), worker, tool);
            if (FarmersDelightCompat.crop(state)) {
                var picked = FarmersDelightCompat.pick(level, pos, state, drops);
                if (!Storage.fits(worker.getInventory(), picked.drops())) { left = 0; return Job.Step.FULL; }
                picked.apply().run();
                Storage.add(worker.getInventory(), picked.drops());
            } else if (state.getBlock() instanceof CropBlock crop) {
                var seed = state.getBlock().getCloneItemStack(level, pos, state).getItem();
                var split = Farming.split(drops, seed);
                var rest = split.rest().stream().map(s -> new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(s.item())), s.count())).toList();
                if (!Storage.fits(worker.getInventory(), rest)) { left = 0; return Job.Step.FULL; }
                level.destroyBlock(pos, false, worker);
                if (split.replant()) level.setBlockAndUpdate(pos, crop.getStateForAge(0));
                Storage.add(worker.getInventory(), rest);
            } else {
                if (!Storage.fits(worker.getInventory(), drops)) { left = 0; return Job.Step.FULL; }
                level.destroyBlock(pos, false, worker);
                Storage.add(worker.getInventory(), drops);
            }
            next++;
            left = -1;
            if (!tool.isEmpty()) {
                tool.hurtAndBreak(1, level, worker, item -> worker.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
                if (worker.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return Job.Step.PAUSE;
            }
            return next < targets.size() ? Job.Step.WORKING : Job.Step.DONE;
        }
        return Job.Step.DONE;
    }

    @Override public void abandon(ServerLevel level, Villager worker) { left = -1; }

    /** effects: true iff a worker standing at {@code feet} can reach {@code target}: within
     * {@link #reach} + 1 blocks across, from a block below its feet to three above (a tomato vine
     * up its rope). Every place the shift counts as arrived (within reach + 1 along the three
     * axes) is near, unless the target is two or more below. */
    static boolean near(BlockPos feet, BlockPos target) {
        int dy = target.getY() - feet.getY();
        return Math.abs(target.getX() - feet.getX()) + Math.abs(target.getZ() - feet.getZ()) <= 3 && dy >= -1 && dy <= 3;
    }
}
