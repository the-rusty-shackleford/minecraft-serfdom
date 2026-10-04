/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.job.Jobs;
import com.chunkworks.serfdom.post.WorkPostBlockEntity;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The Worker Screen and the Work Post's screen (D-0001): what the server sends to show them, and
 * the buttons the client sends back. The screens are client code, handed each view through
 * {@link Client}. Every action is checked again on the server: the player must own the worker or
 * the post and stand within reach. */
public final class Screens {
    static final double REACH = 8.0;
    private Screens() {}

    /** What the Worker Screen shows. */
    public record WorkerView(int entity, Component name, Component profession, int level, Component bed, Component job, byte need, boolean hasBed, boolean hasPost) implements CustomPacketPayload {
        public static final Type<WorkerView> TYPE = new Type<>(Serfdom.id("worker_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkerView> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.entity);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.name);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.profession);
            buf.writeVarInt(v.level);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.bed);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.job);
            buf.writeByte(v.need);
            buf.writeBoolean(v.hasBed);
            buf.writeBoolean(v.hasPost);
        }, buf -> new WorkerView(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readByte(), buf.readBoolean(), buf.readBoolean()));
        @Override public Type<WorkerView> type() { return TYPE; }
    }

    public enum WorkerButton { ASSIGN_BED, ASSIGN_JOB, CLEAR_JOB }

    /** A Worker Screen button. */
    public record WorkerAction(int entity, WorkerButton button) implements CustomPacketPayload {
        public static final Type<WorkerAction> TYPE = new Type<>(Serfdom.id("worker_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkerAction> CODEC = StreamCodec.of(
                (buf, a) -> { buf.writeVarInt(a.entity); buf.writeEnum(a.button); },
                buf -> new WorkerAction(buf.readVarInt(), buf.readEnum(WorkerButton.class)));
        @Override public Type<WorkerAction> type() { return TYPE; }
    }

    /** One job a post can take, with its radius bounds. */
    public record JobChoice(ResourceLocation id, int min, int standard, int max) {
        static final StreamCodec<RegistryFriendlyByteBuf, JobChoice> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, JobChoice::id, ByteBufCodecs.VAR_INT, JobChoice::min,
                ByteBufCodecs.VAR_INT, JobChoice::standard, ByteBufCodecs.VAR_INT, JobChoice::max, JobChoice::new);
    }

    /** What the Work Post's screen shows. */
    public record PostView(BlockPos pos, ResourceLocation job, List<JobChoice> jobs, int radius, boolean outline, List<String> workers) implements CustomPacketPayload {
        public static final Type<PostView> TYPE = new Type<>(Serfdom.id("post_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PostView> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PostView::pos, ResourceLocation.STREAM_CODEC, PostView::job,
                JobChoice.CODEC.apply(ByteBufCodecs.list()), PostView::jobs, ByteBufCodecs.VAR_INT, PostView::radius,
                ByteBufCodecs.BOOL, PostView::outline, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), PostView::workers, PostView::new);
        @Override public Type<PostView> type() { return TYPE; }
    }

    /** The post's settings as the screen leaves them. */
    public record PostEdit(BlockPos pos, ResourceLocation job, int radius, boolean outline) implements CustomPacketPayload {
        public static final Type<PostEdit> TYPE = new Type<>(Serfdom.id("post_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PostEdit> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PostEdit::pos, ResourceLocation.STREAM_CODEC, PostEdit::job,
                ByteBufCodecs.VAR_INT, PostEdit::radius, ByteBufCodecs.BOOL, PostEdit::outline, PostEdit::new);
        @Override public Type<PostEdit> type() { return TYPE; }
    }

    /** The client's screens, set by the client at setup. */
    public static final class Client {
        private static Consumer<WorkerView> workers = v -> {};
        private static Consumer<PostView> posts = v -> {};
        private Client() {}
        public static void receivers(Consumer<WorkerView> w, Consumer<PostView> p) { workers = Objects.requireNonNull(w); posts = Objects.requireNonNull(p); }
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(WorkerView.TYPE, WorkerView.CODEC, (v, ctx) -> Client.workers.accept(v));
        registrar.playToClient(PostView.TYPE, PostView.CODEC, (v, ctx) -> Client.posts.accept(v));
        registrar.playToServer(WorkerAction.TYPE, WorkerAction.CODEC, (a, ctx) -> { if (ctx.player() instanceof ServerPlayer p) pressed(p, a); });
        registrar.playToServer(PostEdit.TYPE, PostEdit.CODEC, (e, ctx) -> { if (ctx.player() instanceof ServerPlayer p) edited(p, e); });
    }

    // ---- the worker -------------------------------------------------------------------------

    /** effects: shows {@code player} their worker's screen. */
    public static void openWorker(ServerPlayer player, Villager worker) { send(player, view(worker)); }

    /** effects: sends the payload when the player's client can take it; a fake player's cannot. */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    static WorkerView view(Villager worker) {
        var w = Workers.of(worker);
        var profession = Workers.profession(worker);
        var none = Component.translatable("screen.serfdom.none");
        var bed = w.bed().<Component>map(b -> Component.translatable("screen.serfdom.at", b.pos().getX(), b.pos().getY(), b.pos().getZ())).orElse(none);
        var job = w.post().<Component>map(p -> {
            var post = com.chunkworks.serfdom.post.Posts.loaded(worker.getServer(), p);
            var name = post.map(x -> jobName(x.job())).orElse(Component.translatable("screen.serfdom.unloaded"));
            return Component.translatable("screen.serfdom.job_at", name, p.pos().getX(), p.pos().getY(), p.pos().getZ());
        }).orElse(none);
        return new WorkerView(worker.getId(), Workers.name(worker), profession, worker.getVillagerData().getLevel(), bed, job,
                worker.getData(Serfdom.NEED), w.bed().isPresent(), w.post().isPresent());
    }

    /** effects: a job's name: its translation, {@code job.<namespace>.<path>}. */
    public static Component jobName(ResourceLocation job) { return Component.translatable("job." + job.getNamespace() + "." + job.getPath()); }

    static void pressed(ServerPlayer player, WorkerAction action) {
        if (!(player.level().getEntity(action.entity()) instanceof Villager worker) || worker.distanceTo(player) > REACH) return;
        if (!Workers.of(worker).ownedBy(player.getUUID())) return;
        var level = player.serverLevel();
        switch (action.button()) {
            case ASSIGN_BED -> Picks.start(player, worker, Picks.Kind.BED);
            case ASSIGN_JOB -> {
                if (Workers.of(worker).bed().isEmpty()) player.displayClientMessage(Component.translatable("message.serfdom.picked.post.no_bed", Workers.name(worker)).withStyle(ChatFormatting.RED), true);
                else Picks.start(player, worker, Picks.Kind.POST);
            }
            case CLEAR_JOB -> {
                Workers.clearJob(level, worker);
                openWorker(player, worker);
            }
        }
    }

    // ---- the post ---------------------------------------------------------------------------

    /** effects: shows {@code player} their post's screen. */
    public static void openPost(ServerPlayer player, WorkPostBlockEntity post) {
        var jobs = Jobs.ids().stream().map(id -> Jobs.get(id).orElseThrow()).map(j -> new JobChoice(ResourceLocation.parse(j.id()), j.radius().min(), j.radius().standard(), j.radius().max())).toList();
        send(player, new PostView(post.getBlockPos(), post.job(), jobs, post.radius(), post.outline(), post.workerNames()));
    }

    static void edited(ServerPlayer player, PostEdit edit) {
        if (player.blockPosition().distSqr(edit.pos()) > REACH * REACH) return;
        if (!(player.level().getBlockEntity(edit.pos()) instanceof WorkPostBlockEntity post) || !post.ownedBy(player.getUUID())) return;
        if (Jobs.get(edit.job()).isEmpty()) return;
        post.setJob(edit.job(), edit.radius());
        post.setOutline(edit.outline());
    }
}
