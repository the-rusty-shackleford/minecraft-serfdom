/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.domain.Household;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** A free villager's household (D-0006), saved with it: what it bought and has at home, used up
 * morning by morning as its profession's needs list says ({@link Household}). */
public final class Households {
    private Households() {}

    public static final Codec<Household> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.intRange(1, Integer.MAX_VALUE)).optionalFieldOf("goods", java.util.Map.of()).forGetter(Household::goods),
            Codec.unboundedMap(Codec.STRING, Codec.doubleRange(0, 0.999999999)).optionalFieldOf("wear", java.util.Map.of()).forGetter(Household::wear)
    ).apply(i, Household::new));

    /** effects: the villager's household; empty when it has none. */
    public static Household of(Villager villager) { return villager.getExistingData(Serfdom.HOUSEHOLD).orElse(Household.EMPTY); }

    public static void set(Villager villager, Household household) {
        if (household.goods().isEmpty() && household.wear().isEmpty()) villager.removeData(Serfdom.HOUSEHOLD);
        else villager.setData(Serfdom.HOUSEHOLD, household);
    }

    /** effects: the household after a morning's use of its profession's needs. */
    static void morning(ServerLevel level, Villager villager) {
        var h = of(villager);
        if (h.goods().isEmpty()) return;
        set(villager, h.morning(Needs.of(level, villager.getVillagerData().getProfession())));
    }
}
