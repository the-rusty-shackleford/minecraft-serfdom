/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.job;

import com.chunkworks.serfdom.domain.JobScript;
import com.chunkworks.serfdom.domain.Radius;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

/** The jobs data packs define, in {@code data/<namespace>/serfdom/job/<id>.json} (D-0001, D-0002):
 * <pre>{"tool": "minecraft:axes", "target": "tree", "radius": {"min": 4, "default": 16, "max": 32},
 *  "bonus": ["morevillagers:woodworker"], "bonus_fallback": ["minecraft:fletcher"]}
 *{"target": "workshop", "stations": ["table", "furnace"], "duties": ["charcoal"], ...}</pre>
 * A file that does not parse is left out and logged. Read on the server. */
public final class Jobs extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<ResourceLocation, JobScript> jobs = Map.of();

    private record RadiusData(int min, int standard, int max) {
        static final Codec<RadiusData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(RadiusData::min),
                Codec.INT.fieldOf("default").forGetter(RadiusData::standard),
                Codec.INT.fieldOf("max").forGetter(RadiusData::max)).apply(i, RadiusData::new));
    }
    private record Data(Optional<String> tool, String target, RadiusData radius, List<String> bonus, List<String> fallback, List<String> stations, List<String> duties) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("tool").forGetter(Data::tool),
                Codec.STRING.fieldOf("target").forGetter(Data::target),
                RadiusData.CODEC.fieldOf("radius").forGetter(Data::radius),
                Codec.STRING.listOf().optionalFieldOf("bonus", List.of()).forGetter(Data::bonus),
                Codec.STRING.listOf().optionalFieldOf("bonus_fallback", List.of()).forGetter(Data::fallback),
                Codec.STRING.listOf().optionalFieldOf("stations", List.of()).forGetter(Data::stations),
                Codec.STRING.listOf().optionalFieldOf("duties", List.of()).forGetter(Data::duties)).apply(i, Data::new));
    }

    public Jobs() { super(GSON, "serfdom/job"); }

    @Override protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        var out = new TreeMap<ResourceLocation, JobScript>();
        files.forEach((id, json) -> {
            try {
                var data = Data.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                var target = JobScript.Target.named(data.target()).orElseThrow(() -> new IllegalArgumentException("unknown target " + data.target()));
                var stations = new HashSet<com.chunkworks.serfdom.domain.Station>();
                for (var n : data.stations()) stations.add(com.chunkworks.serfdom.domain.Station.named(n).orElseThrow(() -> new IllegalArgumentException("unknown station " + n)));
                var duties = new HashSet<com.chunkworks.serfdom.domain.Workshop.Duty>();
                for (var n : data.duties()) duties.add(com.chunkworks.serfdom.domain.Workshop.Duty.named(n).orElseThrow(() -> new IllegalArgumentException("unknown duty " + n)));
                out.put(id, new JobScript(id.toString(), data.tool(), target,
                        new Radius(data.radius().min(), data.radius().standard(), data.radius().max()),
                        new HashSet<>(data.bonus()), new HashSet<>(data.fallback()), stations, duties));
            } catch (RuntimeException e) {
                LOG.error("Serfdom: job {} left out: {}", id, e.getMessage());
            }
        });
        jobs = Map.copyOf(out);
        LOG.info("Serfdom: {} job(s): {}", jobs.size(), jobs.keySet());
    }

    /** effects: the job with this id, if a data pack defines it. */
    public static Optional<JobScript> get(ResourceLocation id) { return Optional.ofNullable(jobs.get(id)); }
    /** effects: every job's id, in order. */
    public static List<ResourceLocation> ids() { return List.copyOf(new TreeMap<>(jobs).keySet()); }

    /** effects: the code that works the job's target kind. */
    public static Job code(JobScript job) {
        return switch (job.target()) {
            case TREE -> Woodcutting.INSTANCE;
            case CROP -> Farming.INSTANCE;
            case WORKSHOP -> WorkshopJob.INSTANCE;
        };
    }

    /** effects: the job's tool tag, if it takes a tool. */
    public static Optional<TagKey<Item>> tool(JobScript job) {
        return job.toolTag().map(t -> TagKey.create(Registries.ITEM, ResourceLocation.parse(t)));
    }

    /** effects: the worker's speed at the job ({@link com.chunkworks.serfdom.domain.Pace#speed}):
     * the bonus when its profession is one the job favours in this game, slowed by the configured
     * share when it is a captive (D-0003), and by its hunger (D-0005). */
    public static double speed(JobScript job, Villager worker) {
        var profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(worker.getVillagerData().getProfession());
        boolean matching = job.bonusIn(PROFESSIONS.get()).contains(String.valueOf(profession));
        return com.chunkworks.serfdom.domain.Pace.speed(matching, com.chunkworks.serfdom.Workers.of(worker).captive(),
                com.chunkworks.serfdom.SerfdomConfig.CAPTIVE_SLOWDOWN.get(), com.chunkworks.serfdom.Appetite.fed(worker));
    }

    /** The game's professions: the registry is frozen before any world loads. */
    private static final java.util.function.Supplier<Set<String>> PROFESSIONS = com.google.common.base.Suppliers.memoize(() ->
            BuiltInRegistries.VILLAGER_PROFESSION.keySet().stream().map(ResourceLocation::toString).collect(Collectors.toUnmodifiableSet()));
}
