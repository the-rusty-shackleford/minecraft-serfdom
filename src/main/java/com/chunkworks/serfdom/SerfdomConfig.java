/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The server's settings, in {@code config/serfdom-server.toml}. Defaults are D-0001's and D-0002's. */
public final class SerfdomConfig {
    private SerfdomConfig() {}
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue HIRE_PER_LEVEL = B
            .comment("Emeralds to hire a villager, for each profession level (1 novice to 5 master).")
            .defineInRange("hire_per_level", 8, 0, 64);
    public static final ModConfigSpec.IntValue OFFER_SECONDS = B
            .comment("Seconds a [Hire] line stays clickable.")
            .defineInRange("offer_seconds", 30, 5, 600);
    public static final ModConfigSpec.IntValue PICK_SECONDS = B
            .comment("Seconds to right-click a bed or a Work Post after Assign bed or Assign job.")
            .defineInRange("pick_seconds", 30, 5, 600);
    public static final ModConfigSpec.IntValue ACTION_FLOOR_TICKS = B
            .comment("The fewest ticks any one action of a worker takes, so quick work stays visible.")
            .defineInRange("action_floor_ticks", 10, 1, 200);
    public static final ModConfigSpec.IntValue LEAVES_PER_TICK = B
            .comment("Leaves a woodcutter clears each tick once a tree's logs are down.")
            .defineInRange("leaves_per_tick", 4, 1, 64);
    public static final ModConfigSpec.IntValue CRAFT_TICKS = B
            .comment("Ticks one craft, cut or repair at a workshop station takes a cook or a blacksmith (D-0002); a matching profession is 25% faster.")
            .defineInRange("craft_ticks", 40, 1, 1200);
    public static final ModConfigSpec.IntValue CAPTURE_TICKS = B
            .comment("Ticks a player holds the chain on a free villager to take it (D-0003).")
            .defineInRange("capture_ticks", com.chunkworks.serfdom.domain.Capture.HOLD, 1, 1200);
    public static final ModConfigSpec.DoubleValue CAPTURE_REACH = B
            .comment("Blocks from the player to the villager within which the hold keeps going.")
            .defineInRange("capture_reach", com.chunkworks.serfdom.domain.Capture.REACH, 0.5, 6.0);
    public static final ModConfigSpec.DoubleValue ESCAPE_CHANCE = B
            .comment("The chance a captive asleep in its bed at midnight gets up and walks home, once a night.")
            .defineInRange("escape_chance", com.chunkworks.serfdom.domain.Escape.CHANCE, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue CAPTIVE_SLOWDOWN = B
            .comment("How much slower a captive walks and works than a hired worker, as a fraction.")
            .defineInRange("captive_slowdown", com.chunkworks.serfdom.domain.Pace.CAPTIVE_SLOWDOWN, 0.0, 0.9);
    public static final ModConfigSpec.BooleanValue HUMMING = B
            .comment("Captives at work now and then hum a phrase of their work song.")
            .define("humming", true);
    public static final ModConfigSpec.BooleanValue WORKERS = B
            .comment("The workers module: hiring, beds, posts and jobs. Off: owned villagers live as free ones and nobody can hire.")
            .define("workers", true);
    public static final ModConfigSpec SPEC = B.build();
}
