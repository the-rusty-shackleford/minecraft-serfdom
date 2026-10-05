/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.Hunger;
import com.chunkworks.serfdom.domain.Meals;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** A worker's hunger and meal times (D-0005), saved with it apart from its {@link Worker} record,
 * since it changes every few seconds; only owned adults hunger, and a villager set free loses it.
 * The rules are the domain's ({@link Hunger}, {@link Meals}); this reads and writes them on the
 * villager, with the server's settings. */
public final class Appetite {
    /** How often a worker's hunger is brought up to date, in ticks. */
    public static final int EVERY = 100;
    private Appetite() {}

    /** A worker's belly: how fed it is and its meal times. Immutable. */
    public record Belly(Hunger hunger, Meals.Times times) {
        public static final Belly FULL = new Belly(Hunger.FULL, Meals.Times.NONE);
        public Belly { Objects.requireNonNull(hunger); Objects.requireNonNull(times); }
        public static final Codec<Belly> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.doubleRange(0, Hunger.MAX).optionalFieldOf("hunger", Hunger.MAX).forGetter(b -> b.hunger().points()),
                Codec.LONG.optionalFieldOf("ate_window", Meals.NEVER).forGetter(b -> b.times().ateWindow()),
                Codec.LONG.optionalFieldOf("retry_at", Meals.NEVER).forGetter(b -> b.times().retryAt())
        ).apply(i, (h, w, r) -> new Belly(new Hunger(h), new Meals.Times(w, r))));
        public Belly with(Hunger h) { return new Belly(h, times); }
        public Belly with(Meals.Times t) { return new Belly(hunger, t); }
    }

    /** effects: true iff hunger is on and the villager hungers: an owned adult. */
    public static boolean hungers(Villager villager) {
        return on() && !villager.isBaby() && Workers.owned(villager);
    }

    private static boolean on() { return !SerfdomConfig.SPEC.isLoaded() || SerfdomConfig.HUNGER.get(); }

    /** effects: the villager's belly; full for one that does not hunger. */
    public static Belly of(Villager villager) {
        return hungers(villager) ? villager.getExistingData(Serfdom.BELLY).orElse(Belly.FULL) : Belly.FULL;
    }

    /** effects: saves the belly, showing the hungry icon when it crossed half. */
    public static void set(Villager villager, Belly belly) {
        boolean was = of(villager).hunger().hungry();
        villager.setData(Serfdom.BELLY, belly);
        if (was != belly.hunger().hungry()) Workers.showNeed(villager);
    }

    /** effects: {@code ticks} more of hunger, asleep or awake. */
    public static void drain(Villager villager, int ticks) {
        if (!hungers(villager)) return;
        var belly = of(villager);
        double rate = SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.HUNGER_PER_HOUR.get() : 1.0;
        set(villager, belly.with(belly.hunger().drain(ticks, villager.isSleeping(), rate)));
    }

    /** effects: whether a meal is due for the villager now. */
    public static boolean due(ServerLevel level, Villager villager) {
        if (!hungers(villager)) return false;
        var worker = Workers.of(villager);
        var belly = of(villager);
        return Meals.due(new Meals.Facts(level.getDayTime(), level.getGameTime(), belly.hunger(), belly.times(), !villager.isSleeping(),
                worker.cuffed(), worker.escaping(), worker.bed().isPresent()));
    }

    /** effects: true iff the villager is starved: it works no more. */
    public static boolean starved(Villager villager) { return of(villager).hunger().starved(); }

    /** effects: how fast the villager's actions go for its hunger, never 0 (a starved worker does not
     * work at all, which its shift sees first). */
    public static double fed(Villager villager) {
        double floor = SerfdomConfig.SPEC.isLoaded() ? SerfdomConfig.HUNGRY_FLOOR.get() : 0.5;
        var h = of(villager).hunger();
        return h.starved() ? floor : h.workSpeed(floor);
    }
}
