/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.SerfdomConfig;
import com.chunkworks.serfdom.behavior.WorkShift;
import com.chunkworks.serfdom.compat.FarmersDelightCompat;
import com.chunkworks.serfdom.domain.Pace;
import com.chunkworks.serfdom.domain.Recipes;
import com.chunkworks.serfdom.domain.Repair;
import com.chunkworks.serfdom.domain.Station;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** One task of a cook or a blacksmith (D-0002), as legs walked in turn: fetch from the chests, then
 * load a station, make something at one, collect a finished load, or mend at the anvil. Each leg is
 * done where it stands; a leg that finds the world changed under it (the chest emptied, the
 * station taken) ends the task, and what the worker carries goes back to the chests before it
 * plans again. The worker's claim on the post (its station, what it brings) lasts as long as the
 * task. */
final class WorkshopTask implements Job.Task {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    /** How near a chest or a station is near enough to use. */
    private static final int REACH = 2;

    /** One part of the task, done at one place. */
    interface Leg {
        BlockPos at();
        /** effects: one tick of the leg: WORKING, DONE, or PAUSE when it cannot be done. */
        Job.Step act(ServerLevel level, Villager worker, double speed);
    }

    private final WorkPostBlockEntity post;
    private final List<Leg> legs;
    private final String what;
    private int index;

    WorkshopTask(WorkPostBlockEntity post, List<Leg> legs, String what) {
        this.post = post;
        this.legs = List.copyOf(legs);
        this.what = what;
    }

    @Override public BlockPos stand() { return legs.get(Math.min(index, legs.size() - 1)).at(); }
    @Override public int reach() { return REACH; }
    @Override public BlockPos key() { return stand(); }

    @Override public Job.Step tick(ServerLevel level, Villager worker, double speed) {
        while (index < legs.size()) {
            var leg = legs.get(index);
            if (!WorkShift.arrived(worker, leg.at(), REACH)) return Job.Step.MOVE;
            worker.getLookControl().setLookAt(Vec3.atCenterOf(leg.at()));
            var step = leg.act(level, worker, speed);
            if (step == Job.Step.WORKING) return step;
            if (step != Job.Step.DONE) {
                if (TRACE) LOG.info("Serfdom trace: {} {} stopped at leg {} of {} ({})", worker.getId(), what, index + 1, legs.size(), leg.getClass().getSimpleName());
                break;
            }
            index++;
        }
        post.release(worker.getUUID());
        return Job.Step.DONE;
    }

    @Override public void abandon(ServerLevel level, Villager worker) { post.release(worker.getUUID()); }

    // ---- legs --------------------------------------------------------------------------------------

    /** Take {@code items} out of the container at {@code at}. */
    record Fetch(BlockPos at, Map<String, Integer> items) implements Leg {
        @Override public Job.Step act(ServerLevel level, Villager worker, double speed) {
            worker.swing(InteractionHand.MAIN_HAND);
            return Storage.take(level, at, items, worker.getInventory()) ? Job.Step.DONE : Job.Step.PAUSE;
        }
    }

    /** Take one stack {@code wanted} accepts out of the container at {@code at}: into the hand when
     * {@code hand} (a knife), else into the inventory (a worn tool to mend). */
    record FetchOne(BlockPos at, Predicate<ItemStack> wanted, boolean hand) implements Leg {
        @Override public Job.Step act(ServerLevel level, Villager worker, double speed) {
            if (hand && wanted.test(worker.getItemBySlot(EquipmentSlot.MAINHAND))) return Job.Step.DONE;
            var got = Storage.takeOne(level, at, wanted);
            if (got.isEmpty()) return Job.Step.PAUSE;
            worker.swing(InteractionHand.MAIN_HAND);
            if (hand) {
                var held = worker.getItemBySlot(EquipmentSlot.MAINHAND);
                if (!held.isEmpty()) worker.getInventory().addItem(held);
                worker.setItemSlot(EquipmentSlot.MAINHAND, got);
            } else worker.getInventory().addItem(got);
            return Job.Step.DONE;
        }
    }

    /** Put a load into the station at {@code at}: {@code times} of each pick, and {@code fuel}. */
    record Load(BlockPos at, Station kind, Recipes.Rule rule, List<String> picks, int times, ItemStack fuel, int cookTicks, boolean lastIsContainer, WorkPostBlockEntity post) implements Leg {
        @Override public Job.Step act(ServerLevel level, Villager worker, double speed) {
            if (Stations.site(level, post, new Stations.Found(at, kind), false).state() != com.chunkworks.serfdom.domain.Workshop.State.FREE) return Job.Step.PAUSE;
            var inv = worker.getInventory();
            var stacks = new ArrayList<ItemStack>();
            for (var pick : picks) {
                var s = removeFrom(inv, pick, times);
                if (s.getCount() < times) { inv.addItem(s); for (var t : stacks) inv.addItem(t); return Job.Step.PAUSE; }
                stacks.add(s);
            }
            var burn = fuel.isEmpty() ? ItemStack.EMPTY : removeFrom(inv, RecipeBook.key(fuel.getItem()), fuel.getCount());
            if (burn.getCount() < fuel.getCount() || !Stations.load(level, worker, at, kind, stacks, burn, cookTicks, lastIsContainer)) {
                for (var t : stacks) inv.addItem(t);
                if (!burn.isEmpty()) inv.addItem(burn);
                return Job.Step.PAUSE;
            }
            worker.swing(InteractionHand.MAIN_HAND);
            post.loadedAt(at, rule.result(), times * rule.yield());
            if (TRACE) LOG.info("Serfdom trace: {} loaded {} x{} ({}) into {} {}", worker.getId(), rule.id(), times, burn.isEmpty() ? "no fuel" : burn.getCount() + " " + RecipeBook.key(burn.getItem()), kind, at.toShortString());
            return Job.Step.DONE;
        }
    }

    /** Make {@code times} crafts of the rule at the station at {@code at}, a craft at a time. */
    static final class Make implements Leg {
        private final BlockPos at; private final Station kind; private final Recipes.Rule rule; private final List<String> picks; private final int times;
        private int made, left = -1;
        Make(BlockPos at, Station kind, Recipes.Rule rule, List<String> picks, int times) { this.at = at; this.kind = kind; this.rule = rule; this.picks = picks; this.times = times; }
        @Override public BlockPos at() { return at; }
        @Override public Job.Step act(ServerLevel level, Villager worker, double speed) {
            if (made >= times) return Job.Step.DONE;
            if (left < 0) {
                left = Pace.ticks(0.0F, 1.0F, true, SerfdomConfig.CRAFT_TICKS.get(), speed);
                worker.swing(InteractionHand.MAIN_HAND);
                var sound = kind == Station.SMITHING ? SoundEvents.SMITHING_TABLE_USE : worker.getVillagerData().getProfession().workSound();
                if (sound != null && kind != Station.BOARD) level.playSound(null, at, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            if (--left > 0) return Job.Step.WORKING;
            left = -1;
            if (!craft(level, worker)) return made > 0 ? Job.Step.DONE : Job.Step.PAUSE;
            made++;
            return made >= times ? Job.Step.DONE : Job.Step.WORKING;
        }
        /** effects: one craft: the picks taken out of the inventory, the result and leftovers put in;
         * false with nothing changed when they are not there, the recipe refuses them or the result
         * would not fit. */
        private boolean craft(ServerLevel level, Villager worker) {
            var inv = worker.getInventory();
            var holder = RecipeBook.recipe(level, rule);
            if (holder.isEmpty()) return false;
            var taken = new ArrayList<ItemStack>();
            for (var pick : picks) {
                var one = removeFrom(inv, pick, 1);
                if (one.isEmpty()) { for (var t : taken) inv.addItem(t); return false; }
                taken.add(one);
            }
            List<ItemStack> out;
            if (kind == Station.BOARD) {
                var knife = worker.getItemBySlot(EquipmentSlot.MAINHAND);
                out = FarmersDelightCompat.knife(knife) ? FarmersDelightCompat.cut(level, at, net.minecraft.resources.ResourceLocation.parse(rule.id()), taken.get(0), knife, worker) : List.of();
            } else {
                var made = kind == Station.SMITHING ? RecipeBook.smith(level, holder.get().value(), taken) : RecipeBook.grid(level, holder.get().value(), taken);
                out = made.map(m -> { var l = new ArrayList<ItemStack>(); l.add(m.result()); l.addAll(m.leftovers()); return (List<ItemStack>) l; }).orElse(List.of());
            }
            if (out.isEmpty() || !Storage.fits(inv, out)) { for (var t : taken) inv.addItem(t); return false; }
            Storage.add(inv, out);
            return true;
        }
    }

    /** Take what the post's load gave out of the station at {@code at}. */
    record Collect(BlockPos at, Station kind, String item, WorkPostBlockEntity post) implements Leg {
        @Override public Job.Step act(ServerLevel level, Villager worker, double speed) {
            int got = Stations.collect(level, worker, at, kind, item, worker.getInventory());
            worker.swing(InteractionHand.MAIN_HAND);
            post.collected(at, got);
            if (kind == Station.CAMPFIRE) post.forget(at);
            if (TRACE) LOG.info("Serfdom trace: {} collected {} {} from {} {}", worker.getId(), got, item, kind, at.toShortString());
            return Job.Step.DONE;
        }
    }

    /** Mend the most worn {@code item} carried with {@code units} of its material at the anvil at
     * {@code at}, as a player's material repair does without the levels; the anvil wears as it
     * does for a player. */
    static final class Mend implements Leg {
        private final BlockPos at; private final String item; private final int units;
        private int left = -1;
        Mend(BlockPos at, String item, int units) { this.at = at; this.item = item; this.units = units; }
        @Override public BlockPos at() { return at; }
        @Override public Job.Step act(ServerLevel level, Villager worker, double speed) {
            if (left < 0) { left = Pace.ticks(0.0F, 1.0F, true, SerfdomConfig.CRAFT_TICKS.get(), speed); worker.swing(InteractionHand.MAIN_HAND); }
            if (--left > 0) return Job.Step.WORKING;
            var inv = worker.getInventory();
            int slot = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var s = inv.getItem(i);
                if (s.isDamaged() && RecipeBook.key(s.getItem()).equals(item) && (slot < 0 || s.getDamageValue() > inv.getItem(slot).getDamageValue())) slot = i;
            }
            if (slot < 0) return Job.Step.PAUSE;
            var tool = inv.getItem(slot);
            int spend = Math.min(units, Repair.units(tool.getMaxDamage(), tool.getDamageValue()));
            int spent = 0;
            for (int i = 0; i < inv.getContainerSize() && spent < spend; i++) {
                var s = inv.getItem(i);
                if (i == slot || s.isEmpty() || !tool.getItem().isValidRepairItem(tool, s)) continue;
                int n = Math.min(spend - spent, s.getCount());
                s.shrink(n);
                spent += n;
            }
            if (spent < 1) return Job.Step.PAUSE;
            tool.setDamageValue(Repair.after(tool.getMaxDamage(), tool.getDamageValue(), spent));
            var state = level.getBlockState(at);
            if (level.random.nextFloat() < 0.12F) {
                var worn = AnvilBlock.damage(state);
                if (worn == null) { level.removeBlock(at, false); level.levelEvent(LevelEvent.SOUND_ANVIL_BROKEN, at, 0); }
                else { level.setBlock(at, worn, 2); level.levelEvent(LevelEvent.SOUND_ANVIL_USED, at, 0); }
            } else level.levelEvent(LevelEvent.SOUND_ANVIL_USED, at, 0);
            if (TRACE) LOG.info("Serfdom trace: {} mended {} with {} at {}", worker.getId(), item, spent, at.toShortString());
            return Job.Step.DONE;
        }
    }

    /** effects: takes up to {@code n} of {@code item} out of the inventory as one stack (the first
     * kind found sets its components); EMPTY when there is none. */
    static ItemStack removeFrom(SimpleContainer inv, String item, int n) {
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize() && out.getCount() < n; i++) {
            var s = inv.getItem(i);
            if (s.isEmpty() || !RecipeBook.key(s.getItem()).equals(item)) continue;
            if (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, s)) continue;
            int take = Math.min(n - out.getCount(), s.getCount());
            if (out.isEmpty()) out = s.copyWithCount(take); else out.grow(take);
            s.shrink(take);
        }
        return out;
    }
}
