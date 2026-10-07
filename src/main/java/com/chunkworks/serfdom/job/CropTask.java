/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.behavior.WorkShift;
import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.gameevent.GameEvent;

/** A round of farming in one plot (D-0001, D-0008): first the seeds the round sows and the farmer
 * lacks, from the farm's chests; then the work in walking order, each piece done when the worker
 * stands by it: a crop taken and replanted from its own harvest (each harvest wears the hoe by one),
 * a bare spot sown with a seed it carries. The plot is held while the round lasts, and each piece
 * done is struck off the farm's fields at once. */
final class CropTask implements Job.Task {
    /** Seeds to take on the way: up to {@code count} of {@code seed} from the chest at {@code chest}. */
    record Fetch(BlockPos chest, Item seed, int count) {}

    private final Job.Place plot;
    private final List<Fetch> fetches;
    private final List<Field.Work> targets;
    private final Consumer<BlockPos> done;
    private int fetch, next, left = -1;

    CropTask(Job.Place plot, List<Fetch> fetches, List<Field.Work> targets, Consumer<BlockPos> done) {
        this.plot = plot;
        this.fetches = List.copyOf(fetches);
        this.targets = List.copyOf(targets);
        this.done = done;
    }

    @Override public BlockPos stand() {
        if (fetch < fetches.size()) return fetches.get(fetch).chest();
        return targets.get(Math.min(next, targets.size() - 1)).pos();
    }
    @Override public int reach() { return 2; }
    @Override public BlockPos key() { return stand(); }
    @Override public Optional<Job.Place> hold() { return Optional.of(plot); }

    @Override public Job.Step tick(ServerLevel level, Villager worker, double speed) {
        while (fetch < fetches.size()) {
            var f = fetches.get(fetch);
            if (!WorkShift.arrived(worker, f.chest(), reach())) return Job.Step.MOVE;
            Storage.takeUpTo(level, f.chest(), f.seed(), f.count(), worker.getInventory());
            fetch++;
        }
        while (next < targets.size()) {
            var work = targets.get(next);
            var pos = work.pos();
            if (work.sow().isPresent()) {
                var seed = work.sow().get();
                if (!bare(level, pos)) { done.accept(pos); next++; left = -1; continue; }
                if (Storage.count(worker.getInventory(), seed) == 0) { next++; left = -1; continue; }
                if (!near(worker.blockPosition(), pos)) return Job.Step.MOVE;
                if (left < 0) left = Tools.quick(speed);
                if (--left > 0) return Job.Step.WORKING;
                sow(level, worker, pos, seed);
                next++;
                left = -1;
                return next < targets.size() ? Job.Step.WORKING : Job.Step.DONE;
            }
            var state = level.getBlockState(pos);
            boolean ripe = Farming.crop(state) ? Farming.target(level, pos, state).filter(pos::equals).isPresent() : Farming.fruit(level, pos);
            if (!ripe) { done.accept(pos); next++; left = -1; continue; }
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
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            done.accept(pos);
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

    /** effects: true iff {@code pos} is a bare spot: air above farmland. */
    static boolean bare(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).getBlock() instanceof FarmBlock;
    }

    /** requires: {@link #bare}(pos), the worker carries {@code seed}. effects: plants one of
     * {@code seed} at {@code pos} as a player would, when what it plants can stand there; the spot is
     * struck off either way. */
    private void sow(ServerLevel level, Villager worker, BlockPos pos, Item seed) {
        done.accept(pos);
        if (!(seed instanceof BlockItem item)) return;
        var state = item.getBlock().defaultBlockState();
        if (!state.canSurvive(level, pos)) return;
        var carried = worker.getInventory();
        for (int i = 0; i < carried.getContainerSize(); i++) {
            if (!carried.getItem(i).is(seed)) continue;
            carried.getItem(i).shrink(1);
            level.setBlockAndUpdate(pos, state);
            worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            var sound = state.getSoundType(level, pos, worker);
            level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(worker, state));
            return;
        }
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
