/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import net.neoforged.neoforge.common.ModConfigSpec;

/** The server's settings, in {@code config/serfdom-server.toml}. Defaults are the decisions'. */
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
    public static final ModConfigSpec.BooleanValue HUNGER = B
            .comment("Workers grow hungry and eat breakfast and dinner (D-0005). Off: nobody hungers, and work never slows for it.")
            .define("hunger", true);
    public static final ModConfigSpec.DoubleValue HUNGER_PER_HOUR = B
            .comment("Hunger points (of 20) a worker loses each waking hour (1200 ticks); half that asleep.")
            .defineInRange("hunger_per_hour", 1.0, 0.0, 20.0);
    public static final ModConfigSpec.DoubleValue HUNGRY_FLOOR = B
            .comment("How fast a worker just short of starving works, as a fraction of its fed pace. Below half hunger its pace falls straight to this; at 0 it works no more.")
            .defineInRange("hungry_floor", 0.5, 0.05, 1.0);
    public static final ModConfigSpec.BooleanValue ECONOMY = B
            .comment("Villagers' purses, the For Sale block and shopping (D-0006). Off: villagers trade as vanilla's, nobody shops, and no deposit is paid.")
            .define("economy", true);
    public static final ModConfigSpec.IntValue PURSE_CAP = B
            .comment("The most emeralds a villager's purse holds; what a trade brings in past it is lost.")
            .defineInRange("purse_cap", com.chunkworks.serfdom.domain.Purse.CAP, 1, 4096);
    public static final ModConfigSpec.IntValue DEPOSIT = B
            .comment("Emeralds a free or hired villager under the line gets the first time it is seen in a morning.")
            .defineInRange("deposit", com.chunkworks.serfdom.domain.Purse.DEPOSIT, 0, 64);
    public static final ModConfigSpec.IntValue DEPOSIT_BELOW = B
            .comment("A purse under this many emeralds gets the morning deposit; a villager born or cured starts with this many.")
            .defineInRange("deposit_below", com.chunkworks.serfdom.domain.Purse.BELOW, 0, 4096);
    public static final ModConfigSpec.IntValue SHOP_REACH = B
            .comment("Blocks from its bed within which a villager shops at For Sale blocks.")
            .defineInRange("shop_reach", 64, 8, 128);
    public static final ModConfigSpec.IntValue SALES_PER_DAY = B
            .comment("The most sales a villager buys in a day, its meals' among them.")
            .defineInRange("sales_per_day", 3, 1, 64);
    public static final ModConfigSpec.DoubleValue NEED_BONUS = B
            .comment("What a villager will pay for something it needs, as a multiple of its base value.")
            .defineInRange("need_bonus", 1.5, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue CLIMATE_BONUS = B
            .comment("What a villager will pay for an item from another climate (hot or cold, by its village's biome), as a multiple (D-0006, 4b).")
            .defineInRange("climate_bonus", 1.5, 0.1, 10.0);
    public static final ModConfigSpec.DoubleValue TASTE_SPREAD = B
            .comment("How far a villager's taste for food, tools, decor and luxury runs either side of 1: 0.5 is 0.5 to 1.5.")
            .defineInRange("taste_spread", 0.5, 0.0, 0.95);
    public static final ModConfigSpec.IntValue WANTS_EACH = B
            .comment("The most of any one item a villager wants for its taste, at home.")
            .defineInRange("wants_each", 3, 0, 64);
    public static final ModConfigSpec.BooleanValue DEFENCE = B
            .comment("Owned villagers arm themselves from their chests in a raid, fight, and put it all back after (D-0007). Off: they hide as vanilla's do.")
            .define("defence", true);
    public static final ModConfigSpec.IntValue DEFENCE_REACH = B
            .comment("Blocks from its post or bed past which a defender never chases a raider.")
            .defineInRange("defence_reach", 48, 8, 96);
    public static final ModConfigSpec.IntValue AMMO_CARRIED = B
            .comment("The most arrows or rounds a defender carries out of its chests.")
            .defineInRange("ammo_carried", 64, 1, 640);
    public static final ModConfigSpec.BooleanValue WORKERS = B
            .comment("The workers module: hiring, beds, posts and jobs. Off: owned villagers live as free ones and nobody can hire.")
            .define("workers", true);
    public static final ModConfigSpec SPEC = B.build();
}
