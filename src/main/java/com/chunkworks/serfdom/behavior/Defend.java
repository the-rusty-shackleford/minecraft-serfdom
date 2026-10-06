/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.behavior;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.compat.GunsCompat;
import com.chunkworks.serfdom.defence.Armouries;
import com.chunkworks.serfdom.defence.Arms;
import com.chunkworks.serfdom.defence.Defenders;
import com.chunkworks.serfdom.domain.Armoury;
import com.chunkworks.serfdom.domain.Defence;
import com.chunkworks.serfdom.domain.LineOfFire;
import com.chunkworks.serfdom.job.Kitchen;
import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/** A worker in a raid (D-0007), the whole of its defence activity. Every {@link #THINK} ticks it asks
 * {@link Defence#next} what to do, and does it:
 * <ul>
 * <li><b>Arm:</b> it puts its work tool away, and walks to each chest its armoury plan names, nearest
 * first, taking the planned weapons (if nobody took them first) and what feeds its ranged weapon, up to
 * {@code ammo_carried}; it has then tried for this raid.</li>
 * <li><b>Fight:</b> its ranged weapon in hand while it can fire, else its melee weapon. With a bow it
 * stands within 15 blocks and draws a player's full draw; a crossbow charges; a gun fires at its
 * rate from its range and reloads loose rounds from what it carries. It never shoots while a friend
 * is within a block of the line. With a melee weapon it closes and strikes at the weapon's speed.</li>
 * <li><b>Stand by:</b> no raider in reach, it keeps within six blocks of its post or bed.</li>
 * <li><b>Hide:</b> nothing to fight with: it hands its brain to vanilla's raid.</li>
 * <li><b>Put back:</b> the raid over, it walks to each chest a thing came from and puts it back; what
 * will not go there goes to its home chest, else the ground.</li>
 * </ul> */
public final class Defend extends Behavior<Villager> {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean TRACE = Boolean.getBoolean("serfdom.trace");
    static final int THINK = 10, WALK_LIMIT = 600, STAND_BY = 6;
    static final double BOW_RANGE = 15, ARRIVE = 2.5, CLEARANCE = 1.0;
    /** An arrow's gravity, blocks a tick a tick: vanilla's for an arrow. */
    private static final double ARROW_GRAVITY = 0.05;
    private static final float SPEED = 0.6F;

    private Defence.Step step;
    private long nextThink;
    private Optional<Raider> target = Optional.empty();
    // A trip to chests: arming or putting back.
    private final List<BlockPos> stops = new ArrayList<>();
    private Armouries.Plan plan;
    private long walkSince;
    private int cooldown, draw, reloading;

    public Defend() { super(ImmutableMap.of(), 24000); }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, Villager v) { return v.getBrain().isActive(Serfdom.DEFEND.get()); }

    @Override protected boolean canStillUse(ServerLevel level, Villager v, long now) {
        return v.getBrain().isActive(Serfdom.DEFEND.get()) && step != Defence.Step.NONE && step != Defence.Step.HIDE && !Workers.of(v).cuffed();
    }

    @Override protected void start(ServerLevel level, Villager v, long now) {
        step = null;
        nextThink = now;
        stops.clear();
        target = Optional.empty();
        cooldown = draw = reloading = 0;
    }

    @Override protected void tick(ServerLevel level, Villager v, long now) {
        if (now >= nextThink) {
            nextThink = now + THINK;
            think(level, v, now);
        }
        if (step == null) return;
        switch (step) {
            case ARM -> trip(level, v, now, true);
            case PUT_BACK -> trip(level, v, now, false);
            case FIGHT -> fight(level, v, now);
            case STAND -> standBy(level, v);
            case HIDE, NONE -> {}
        }
    }

    @Override protected void stop(ServerLevel level, Villager v, long now) {
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        var brain = v.getBrain();
        if (!brain.isActive(Serfdom.DEFEND.get())) return;
        if (step == Defence.Step.HIDE) {
            // Vanilla's raid: it hides, as it would have without arms.
            var raid = Defenders.raid(level, v);
            brain.setActiveActivityIfPossible(raid.isPresent() && raid.get().hasFirstWaveSpawned() && !raid.get().isBetweenWaves() ? Activity.RAID : Activity.PRE_RAID);
        } else brain.setActiveActivityIfPossible(brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L)));
    }

    // ---- deciding ---------------------------------------------------------------------------

    private void think(ServerLevel level, Villager v, long now) {
        var raid = Defenders.raid(level, v);
        target = raid.flatMap(r -> Defenders.target(level, v, r));
        var facts = Defenders.facts(level, v, false);
        var next = Defence.next(new Defence.Facts(facts.raid(), facts.musters(), facts.carries(), facts.tried(), facts.rangedReady(), facts.melee(), target.isPresent()));
        // A trip under way finishes before it fights: a defender half armed arms on.
        if (step == Defence.Step.ARM && !stops.isEmpty() && raid.isPresent()) return;
        if (next == step) {
            if (next == Defence.Step.FIGHT || next == Defence.Step.STAND) equip(v);
            return;
        }
        if (TRACE) LOG.info("Serfdom trace: {} defends: {} -> {} (raid {}, {})", v.getId(), step, next, raid.map(r -> r.getId()).orElse(-1), facts);
        step = next;
        switch (next) {
            case ARM -> beginArming(level, v, raid.orElseThrow().getId());
            case PUT_BACK -> beginPuttingBack(level, v);
            case FIGHT, STAND -> equip(v);
            case HIDE, NONE -> {}
        }
    }

    // ---- arming -----------------------------------------------------------------------------

    private void beginArming(ServerLevel level, Villager v, int raidId) {
        var arms = Defenders.arms(v);
        if (!arms.holds()) Workers.stash(v);
        Defenders.set(v, arms.tried(raidId));
        plan = Armouries.plan(level, v);
        var chests = new LinkedHashSet<BlockPos>();
        plan.ranged().ifPresent(s -> chests.add(s.chest()));
        plan.melee().ifPresent(s -> chests.add(s.chest()));
        // What feeds the ranged weapon, from every chest that holds some.
        plan.ranged().ifPresent(s -> {
            for (var pos : Armouries.chests(level, v)) if (feedsAt(level, pos, s.stack())) chests.add(pos);
        });
        stops.clear();
        stops.addAll(chests);
        stops.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(v.position())));
        if (TRACE) LOG.info("Serfdom trace: {} arms: {} {} from {}", v.getId(), plan.ranged().map(s -> s.stack().getItem().toString()).orElse("no ranged"),
                plan.melee().map(s -> s.stack().getItem().toString()).orElse("no melee"), stops);
        nextStop(v, level.getGameTime());
    }

    private static boolean feedsAt(ServerLevel level, BlockPos pos, ItemStack weapon) {
        var h = com.chunkworks.serfdom.job.Storage.handler(level, pos).orElse(null);
        if (h == null) return false;
        for (int i = 0; i < h.getSlots(); i++) if (Armouries.feeds(weapon, h.getStackInSlot(i))) return true;
        return false;
    }

    /** effects: at a chest of the trip: arming, it takes what the plan finds there; putting back, it
     * leaves what came from there. */
    private void atStop(ServerLevel level, Villager v, BlockPos pos, boolean arming) {
        v.swing(InteractionHand.MAIN_HAND);
        var arms = Defenders.arms(v);
        if (arming) {
            var kept = new ArrayList<>(arms.kept());
            for (var spot : List.of(plan.ranged(), plan.melee())) {
                if (spot.isEmpty() || !spot.get().chest().equals(pos)) continue;
                var got = Armouries.take(level, spot.get());
                if (!got.isEmpty()) kept.add(new Arms.Kept(got, Optional.of(pos)));
            }
            if (plan.ranged().isPresent()) {
                var weapon = plan.ranged().get().stack();
                int have = kept.stream().map(Arms.Kept::stack).filter(s -> Armouries.feeds(weapon, s)).mapToInt(ItemStack::getCount).sum();
                int want = Math.max(0, ammoCarried() - have);
                for (var s : Armouries.takeAll(level, pos, x -> Armouries.feeds(weapon, x), want)) kept.add(new Arms.Kept(s, Optional.of(pos)));
            }
            Defenders.set(v, arms.with(kept));
        } else {
            var kept = new ArrayList<Arms.Kept>();
            for (var k : arms.kept()) {
                if (k.from().filter(pos::equals).isEmpty()) { kept.add(k); continue; }
                var rest = Armouries.put(level, pos, k.stack().copy());
                if (!rest.isEmpty()) kept.add(new Arms.Kept(rest, Optional.empty()));
            }
            boolean holds = arms.holds();
            if (holds && arms.handFrom().filter(pos::equals).isPresent()) {
                if (TRACE) LOG.info("Serfdom trace: {} puts back {} (hand) and {} at {}", v.getId(), v.getMainHandItem(), arms.kept().stream().map(Arms.Kept::stack).toList(), pos.toShortString());
                var rest = Armouries.put(level, pos, v.getMainHandItem().copy());
                v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                Defenders.handDrops(v, false);
                if (!rest.isEmpty()) kept.add(new Arms.Kept(rest, Optional.empty()));
                holds = false;
            }
            Defenders.set(v, arms.with(kept, holds, arms.handFrom()));
        }
    }

    private static int ammoCarried() { return com.chunkworks.serfdom.SerfdomConfig.SPEC.isLoaded() ? com.chunkworks.serfdom.SerfdomConfig.AMMO_CARRIED.get() : 64; }

    // ---- putting back -----------------------------------------------------------------------

    private void beginPuttingBack(ServerLevel level, Villager v) {
        var arms = Defenders.arms(v);
        var chests = new LinkedHashSet<BlockPos>();
        arms.handFrom().ifPresent(chests::add);
        for (var k : arms.kept()) k.from().ifPresent(chests::add);
        stops.clear();
        stops.addAll(chests);
        stops.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(v.position())));
        nextStop(v, level.getGameTime());
    }

    /** effects: what is left once every chest of the trip is done: anything with no chest to go to,
     * into its home chest, else at its feet; it carries nothing of ours after. */
    private void leftovers(ServerLevel level, Villager v) {
        var arms = Defenders.arms(v);
        if (TRACE && arms.carries()) LOG.info("Serfdom trace: {} leftovers: {} (hand: {}) {}", v.getId(), arms.kept().stream().map(Arms.Kept::stack).toList(), arms.holds(), v.getMainHandItem());
        var home = Kitchen.home(level, v);
        for (var k : arms.kept()) {
            var rest = home.map(h -> Armouries.put(level, h, k.stack().copy())).orElse(k.stack().copy());
            if (!rest.isEmpty()) v.spawnAtLocation(rest);
        }
        if (arms.holds()) {
            var hand = v.getMainHandItem().copy();
            v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            var rest = home.map(h -> Armouries.put(level, h, hand)).orElse(hand);
            if (!rest.isEmpty()) v.spawnAtLocation(rest);
        }
        Defenders.handDrops(v, false);
        Defenders.set(v, new Arms(List.of(), false, Optional.empty(), arms.raid()));
    }

    // ---- a trip ---------------------------------------------------------------------------------

    private void nextStop(Villager v, long now) {
        walkSince = now;
        if (stops.isEmpty()) return;
        v.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stops.getFirst(), SPEED, 1));
    }

    private void trip(ServerLevel level, Villager v, long now, boolean arming) {
        if (stops.isEmpty()) {
            if (!arming) leftovers(level, v);
            return;
        }
        var at = stops.getFirst();
        if (v.position().distanceToSqr(Vec3.atBottomCenterOf(at)) <= ARRIVE * ARRIVE || now - walkSince > WALK_LIMIT) {
            if (now - walkSince <= WALK_LIMIT) atStop(level, v, at, arming);
            else if (TRACE) LOG.info("Serfdom trace: {} could not reach the chest at {}", v.getId(), at.toShortString());
            stops.removeFirst();
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            if (stops.isEmpty()) {
                if (arming) { equip(v); nextThink = now; }
                else leftovers(level, v);
            } else nextStop(v, now);
            return;
        }
        if (!v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(at, SPEED, 1));
    }

    // ---- the hand ---------------------------------------------------------------------------

    /** effects: the weapon the defence says in its hand: its ranged weapon while it can fire, else its
     * melee one; the other carried. */
    private static void equip(Villager v) {
        var arms = Defenders.arms(v);
        var facts = Defence.hand(Defenders.rangedReady(v, arms), Defenders.melee(v, arms));
        if (facts == Defence.Hand.NONE) return;
        var wantKind = facts == Defence.Hand.RANGED;
        var hand = v.getMainHandItem();
        boolean ours = arms.holds();
        if (ours && Armouries.kind(hand).map(k -> k.ranged() == wantKind).orElse(false)) return;
        var kept = new ArrayList<>(arms.kept());
        int pick = -1;
        for (int i = 0; i < kept.size(); i++) {
            var k = Armouries.kind(kept.get(i).stack());
            if (k.isPresent() && (wantKind ? k.get().ranged() && k.get() != Armoury.Kind.LAUNCHER : k.get() == Armoury.Kind.MELEE)) { pick = i; break; }
        }
        if (pick < 0) return;
        var taken = kept.remove(pick);
        if (ours && !hand.isEmpty()) kept.add(new Arms.Kept(hand.copy(), arms.handFrom()));
        else if (!hand.isEmpty()) Workers.stash(v);
        v.setItemSlot(EquipmentSlot.MAINHAND, taken.stack());
        Defenders.handDrops(v, true);
        Defenders.set(v, arms.with(kept, true, taken.from()));
    }

    // ---- fighting ---------------------------------------------------------------------------

    private void standBy(ServerLevel level, Villager v) {
        var home = Workers.of(v).post().or(() -> Workers.of(v).bed()).filter(p -> p.dimension() == level.dimension()).map(p -> p.pos());
        if (home.isEmpty()) return;
        if (v.position().distanceToSqr(Vec3.atBottomCenterOf(home.get())) > STAND_BY * STAND_BY) {
            if (!v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(home.get(), SPEED, 2));
        }
    }

    private void fight(ServerLevel level, Villager v, long now) {
        var t = target.filter(LivingEntity::isAlive).orElse(null);
        if (t == null) return;
        v.getLookControl().setLookAt(t, 30F, 30F);
        if (cooldown > 0) cooldown--;
        var hand = v.getMainHandItem();
        var kind = Armouries.kind(hand).orElse(null);
        if (kind == null || !Defenders.arms(v).holds()) return;
        switch (kind) {
            case MELEE -> melee(v, t, hand);
            case BOW, CROSSBOW -> bow(level, v, t, hand, kind == Armoury.Kind.CROSSBOW);
            case GUN -> gun(level, v, t, hand);
            case LAUNCHER -> {}
        }
    }

    private void melee(Villager v, LivingEntity t, ItemStack hand) {
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(t, SPEED, 0));
        if (cooldown > 0 || !v.isWithinMeleeAttackRange(t) || !v.hasLineOfSight(t)) return;
        v.swing(InteractionHand.MAIN_HAND);
        v.doHurtTarget(t);
        cooldown = Defence.cooldown(Armouries.blow(hand)[1]);
    }

    /** effects: true iff it stands within {@code range} of the target and sees it; otherwise it walks
     * toward it. */
    private static boolean inRange(Villager v, LivingEntity t, double range) {
        if (v.distanceTo(t) <= range && v.hasLineOfSight(t)) {
            v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            v.getNavigation().stop();
            return true;
        }
        v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(t, SPEED, 2));
        return false;
    }

    private void bow(ServerLevel level, Villager v, LivingEntity t, ItemStack hand, boolean crossbow) {
        if (!inRange(v, t, BOW_RANGE)) { draw = 0; return; }
        if (++draw < (crossbow ? Armoury.CROSSBOW_CHARGE : Armoury.BOW_DRAW)) return;
        if (!clear(level, v, t)) return;
        var arrow = takeOne(v, hand);
        if (arrow.isEmpty()) return;
        var shot = ProjectileUtil.getMobArrow(v, arrow, 1.0F, hand);
        double speed = crossbow ? Armoury.CROSSBOW_SPEED : Armoury.BOW_SPEED;
        double dx = t.getX() - v.getX(), dy = t.getY(1 / 3.0) - shot.getY(), dz = t.getZ() - v.getZ();
        // Lifted by what gravity takes over the flight. A skeleton lifts a fifth of the distance, for
        // its slow arrows; at a full draw's speed that sends every arrow over the head.
        double flight = Math.sqrt(dx * dx + dz * dz) / speed;
        shot.shoot(dx, dy + 0.5 * ARROW_GRAVITY * flight * flight, dz, (float) speed, 1.0F);
        shot.pickup = AbstractArrow.Pickup.ALLOWED;
        level.addFreshEntity(shot);
        level.playSound(null, v, crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 1.0F, 1.0F / (v.getRandom().nextFloat() * 0.4F + 0.8F));
        hand.hurtAndBreak(1, v, EquipmentSlot.MAINHAND);
        draw = 0;
    }

    private void gun(ServerLevel level, Villager v, LivingEntity t, ItemStack hand) {
        var gun = GunsCompat.gun(hand).orElse(null);
        if (gun == null) return;
        if (reloading > 0) {
            if (--reloading == 0) {
                var arms = Defenders.arms(v);
                var r = Defence.reload(gun.capacity(), GunsCompat.rounds(hand), Defenders.fodder(arms, hand), gun.reloadPerRound());
                var rounds = takeN(v, hand, r.rounds());
                if (!rounds.isEmpty()) GunsCompat.load(hand, rounds.getCount(), rounds);
            }
            return;
        }
        if (GunsCompat.rounds(hand) == 0) {
            var r = Defence.reload(gun.capacity(), 0, Defenders.fodder(Defenders.arms(v), hand), gun.reloadPerRound());
            if (r.rounds() > 0) reloading = Math.max(1, r.ticks());
            return;
        }
        if (!inRange(v, t, gun.range()) || cooldown > 0 || !clear(level, v, t)) return;
        GunsCompat.fire(level, v, hand, t);
        cooldown = Math.max(1, gun.fireTicks());
    }

    /** effects: true iff no friend stands within a block of the line from its eye to the target. */
    private static boolean clear(ServerLevel level, Villager v, LivingEntity t) {
        var from = v.getEyePosition();
        var to = GunsCompat.middle(t);
        var friends = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(CLEARANCE + 0.5),
                e -> e != v && e != t && e.isAlive() && !(e instanceof Enemy) && !e.isSpectator());
        var boxes = friends.stream().map(e -> { var b = e.getBoundingBox(); return new LineOfFire.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ); }).toList();
        return LineOfFire.clear(new LineOfFire.Point(from.x, from.y, from.z), new LineOfFire.Point(to.x, to.y, to.z), boxes, CLEARANCE);
    }

    // ---- ammunition -------------------------------------------------------------------------

    private static ItemStack takeOne(Villager v, ItemStack weapon) { return takeN(v, weapon, 1); }

    /** effects: up to {@code n} of what feeds {@code weapon} out of what it carries, as one stack of
     * one kind. */
    private static ItemStack takeN(Villager v, ItemStack weapon, int n) {
        var arms = Defenders.arms(v);
        var kept = new ArrayList<>(arms.kept());
        ItemStack out = ItemStack.EMPTY;
        for (int i = 0; i < kept.size() && n > 0; i++) {
            var k = kept.get(i);
            if (!Armouries.feeds(weapon, k.stack()) || (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, k.stack()))) continue;
            var s = k.stack().copy();
            var part = s.split(Math.min(n, s.getCount()));
            n -= part.getCount();
            if (out.isEmpty()) out = part; else out.grow(part.getCount());
            if (s.isEmpty()) { kept.remove(i); i--; } else kept.set(i, new Arms.Kept(s, k.from()));
        }
        Defenders.set(v, arms.with(kept));
        return out;
    }
}
