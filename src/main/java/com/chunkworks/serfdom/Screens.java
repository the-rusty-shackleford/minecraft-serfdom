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

/** The Worker Screen and the Work Post's screen (D-0001, D-0002, D-0004): what the server sends to
 * show them, and the buttons the client sends back. The Worker Screen is a menu of the worker's
 * armour slots ({@link WorkerMenu}) with its view sent beside it. A workshop post's view carries its stock list,
 * each row with what is stocked and why it is stuck. The screens are client code, handed each view through
 * {@link Client}. Every action is checked again on the server: the player must own the worker or
 * the post and stand within reach. */
public final class Screens {
    static final double REACH = 8.0;
    private Screens() {}

    /** What a worker is, as the Worker Screen says it (D-0003): hired or captive, a captive in chains or
     * on its way home, or a child, which cannot take a post. */
    public enum Status { HIRED, CAPTIVE, CUFFED, ESCAPING, CHILD }

    /** What the Worker Screen shows; {@code hunger} in half drumsticks (0 to 20), or &minus;1 for a
     * worker that does not hunger (D-0005); {@code purse} its emeralds, or &minus;1 with the economy
     * off (D-0006). */
    public record WorkerView(int entity, Component name, Component profession, int level, Component bed, Component job, byte need, byte hunger, int purse, boolean hasBed, boolean hasPost, Status status) implements CustomPacketPayload {
        public static final Type<WorkerView> TYPE = new Type<>(Serfdom.id("worker_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkerView> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeVarInt(v.entity);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.name);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.profession);
            buf.writeVarInt(v.level);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.bed);
            ComponentSerialization.STREAM_CODEC.encode(buf, v.job);
            buf.writeByte(v.need);
            buf.writeByte(v.hunger);
            buf.writeVarInt(v.purse + 1);
            buf.writeBoolean(v.hasBed);
            buf.writeBoolean(v.hasPost);
            buf.writeEnum(v.status);
        }, buf -> new WorkerView(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readByte(), buf.readByte(), buf.readVarInt() - 1, buf.readBoolean(), buf.readBoolean(), buf.readEnum(Status.class)));
        @Override public Type<WorkerView> type() { return TYPE; }
    }

    public enum WorkerButton { ASSIGN_BED, ASSIGN_JOB, CLEAR_JOB, SET_FREE }

    /** A Worker Screen button. */
    public record WorkerAction(int entity, WorkerButton button) implements CustomPacketPayload {
        public static final Type<WorkerAction> TYPE = new Type<>(Serfdom.id("worker_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkerAction> CODEC = StreamCodec.of(
                (buf, a) -> { buf.writeVarInt(a.entity); buf.writeEnum(a.button); },
                buf -> new WorkerAction(buf.readVarInt(), buf.readEnum(WorkerButton.class)));
        @Override public Type<WorkerAction> type() { return TYPE; }
    }

    /** One job a post can take, with its radius bounds and, for a workshop, its stations' names. */
    public record JobChoice(ResourceLocation id, int min, int standard, int max, List<String> stations) {
        static final StreamCodec<RegistryFriendlyByteBuf, JobChoice> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, JobChoice::id, ByteBufCodecs.VAR_INT, JobChoice::min,
                ByteBufCodecs.VAR_INT, JobChoice::standard, ByteBufCodecs.VAR_INT, JobChoice::max,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), JobChoice::stations, JobChoice::new);
        public boolean workshop() { return !stations.isEmpty(); }
    }

    /** One row of a post's stock list: what it keeps, how many are stocked or on their way, its
     * standing ({@link com.chunkworks.serfdom.domain.Workshop.Status}'s ordinal) and why. */
    public record RowView(ResourceLocation item, int keep, int have, int status, Component detail) {
        static final StreamCodec<RegistryFriendlyByteBuf, RowView> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, RowView::item, ByteBufCodecs.VAR_INT, RowView::keep, ByteBufCodecs.VAR_INT, RowView::have,
                ByteBufCodecs.VAR_INT, RowView::status, ComponentSerialization.STREAM_CODEC, RowView::detail, RowView::new);
    }

    /** What the Work Post's screen shows. */
    public record PostView(BlockPos pos, ResourceLocation job, List<JobChoice> jobs, int radius, boolean outline, List<String> workers, List<RowView> rows) implements CustomPacketPayload {
        public static final Type<PostView> TYPE = new Type<>(Serfdom.id("post_view"));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<JobChoice>> JOBS = JobChoice.CODEC.apply(ByteBufCodecs.list());
        private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> NAMES = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());
        private static final StreamCodec<RegistryFriendlyByteBuf, List<RowView>> ROWS = RowView.CODEC.apply(ByteBufCodecs.list());
        public static final StreamCodec<RegistryFriendlyByteBuf, PostView> CODEC = StreamCodec.of((buf, v) -> {
            BlockPos.STREAM_CODEC.encode(buf, v.pos);
            ResourceLocation.STREAM_CODEC.encode(buf, v.job);
            JOBS.encode(buf, v.jobs);
            buf.writeVarInt(v.radius);
            buf.writeBoolean(v.outline);
            NAMES.encode(buf, v.workers);
            ROWS.encode(buf, v.rows);
        }, buf -> new PostView(BlockPos.STREAM_CODEC.decode(buf), ResourceLocation.STREAM_CODEC.decode(buf), JOBS.decode(buf),
                buf.readVarInt(), buf.readBoolean(), NAMES.decode(buf), ROWS.decode(buf)));
        @Override public Type<PostView> type() { return TYPE; }
    }

    /** A change to the stock list: row {@code index} set to keep {@code keep} of {@code item}, added
     * at the end when index is the list's size, removed when keep is 0. */
    public record StockEdit(BlockPos pos, int index, ResourceLocation item, int keep) implements CustomPacketPayload {
        public static final Type<StockEdit> TYPE = new Type<>(Serfdom.id("stock_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StockEdit> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, StockEdit::pos, ByteBufCodecs.VAR_INT, StockEdit::index, ResourceLocation.STREAM_CODEC, StockEdit::item,
                ByteBufCodecs.VAR_INT, StockEdit::keep, StockEdit::new);
        @Override public Type<StockEdit> type() { return TYPE; }
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
        registrar.playToServer(StockEdit.TYPE, StockEdit.CODEC, (e, ctx) -> { if (ctx.player() instanceof ServerPlayer p) stocked(p, e); });
    }

    // ---- the worker -------------------------------------------------------------------------

    /** effects: opens {@code player}'s worker's screen: the menu of its four armour slots (opened as
     * the game opens any, by its own packet), then the view of the rest, which the client binds to
     * the menu as it comes. */
    public static void openWorker(ServerPlayer player, Villager worker) {
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) -> new WorkerMenu(id, inventory, worker), Workers.name(worker)));
        send(player, view(worker));
    }

    /** effects: shows the open Worker Screen the worker as it is now. */
    static void refreshWorker(ServerPlayer player, Villager worker) { send(player, view(worker)); }

    /** effects: sends the payload when the player's client can take it; a fake player's cannot. */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) PacketDistributor.sendToPlayer(player, payload);
    }

    /** effects: what the Worker Screen shows of {@code worker} now. Public for the GameTests. */
    public static WorkerView view(Villager worker) {
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
                worker.getData(Serfdom.NEED), (byte) (Appetite.hungers(worker) ? Appetite.of(worker).hunger().halves() : -1),
                com.chunkworks.serfdom.market.Purses.on() ? com.chunkworks.serfdom.market.Purses.emeralds(worker) : -1,
                w.bed().isPresent(), w.post().isPresent(), status(worker, w));
    }

    /** effects: what the worker is, the first that applies: a child, in chains, on its way home, a
     * captive, hired. */
    static Status status(Villager villager, Worker w) {
        if (villager.isBaby()) return Status.CHILD;
        if (w.cuffed()) return Status.CUFFED;
        if (w.escaping()) return Status.ESCAPING;
        return w.captive() ? Status.CAPTIVE : Status.HIRED;
    }

    /** effects: a job's name: its translation, {@code job.<namespace>.<path>}. */
    public static Component jobName(ResourceLocation job) { return Component.translatable("job." + job.getNamespace() + "." + job.getPath()); }

    /** effects: a Worker Screen button the player pressed, checked again here. Public for the GameTests. */
    public static void pressed(ServerPlayer player, WorkerAction action) {
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
                refreshWorker(player, worker);
            }
            case SET_FREE -> {
                var name = Workers.name(worker);
                // The owner's own chain comes back to them; only the law confiscates one.
                if (Workers.of(worker).cuffed() && !player.getAbilities().instabuild)
                    com.chunkworks.carried.api.Carried.giveOrDrop(player, new net.minecraft.world.item.ItemStack(Serfdom.CHAIN_LEAD.get()));
                Workers.free(level, worker, com.chunkworks.serfdom.domain.Parting.Way.SET_FREE);
                player.displayClientMessage(Component.translatable("message.serfdom.set_free", name).withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    // ---- the post ---------------------------------------------------------------------------

    /** effects: shows {@code player} their post's screen. */
    public static void openPost(ServerPlayer player, WorkPostBlockEntity post) { send(player, postView(player.serverLevel(), post)); }

    /** effects: what the post's screen shows now, its rows' standing worked out as a worker would. */
    public static PostView postView(net.minecraft.server.level.ServerLevel level, WorkPostBlockEntity post) {
        var jobs = Jobs.ids().stream().map(id -> Jobs.get(id).orElseThrow()).map(j -> new JobChoice(ResourceLocation.parse(j.id()), j.radius().min(), j.radius().standard(), j.radius().max(),
                j.stations().stream().map(com.chunkworks.serfdom.domain.Station::named).sorted().toList())).toList();
        var rows = new java.util.ArrayList<RowView>();
        var job = Jobs.get(post.job());
        if (job.isPresent() && job.get().target() == com.chunkworks.serfdom.domain.JobScript.Target.WORKSHOP && !post.stock().rows().isEmpty()) {
            var read = com.chunkworks.serfdom.job.WorkshopJob.read(level, post, job.get(), java.util.Optional.empty(), p -> false);
            var states = com.chunkworks.serfdom.domain.Workshop.rows(read.facts());
            for (int i = 0; i < states.size(); i++) {
                var row = post.stock().rows().get(i);
                var st = states.get(i);
                rows.add(new RowView(ResourceLocation.parse(row.item()), row.keep(), st.have(), st.status().ordinal(), detail(st)));
            }
        }
        return new PostView(post.getBlockPos(), post.job(), jobs, post.radius(), post.outline(), post.workerNames(), rows);
    }

    /** effects: a row's standing in words: stocked, being made, what it is short of, the stations it
     * needs, no fuel, a knife. */
    static Component detail(com.chunkworks.serfdom.domain.Workshop.RowState st) {
        return switch (st.status()) {
            case MET -> Component.translatable("screen.serfdom.stock.met").withStyle(ChatFormatting.GREEN);
            case MAKING -> Component.translatable("screen.serfdom.stock.making").withStyle(ChatFormatting.YELLOW);
            case SHORT -> {
                var list = Component.empty();
                int shown = 0;
                for (var s : st.shortages()) {
                    if (shown == 3) { list.append(", …"); break; }
                    if (shown++ > 0) list.append(", ");
                    list.append(Component.translatable("screen.serfdom.stock.short_item", s.missing(),
                            Component.translatable(com.chunkworks.serfdom.job.RecipeBook.item(s.options().get(0)).getDescriptionId())));
                }
                yield Component.translatable("screen.serfdom.stock.short", list).withStyle(ChatFormatting.RED);
            }
            case NO_STATION -> {
                var list = Component.empty();
                int i = 0;
                for (var k : st.missing().stream().sorted().toList()) {
                    if (i++ > 0) list.append(Component.translatable("screen.serfdom.stock.or"));
                    list.append(Component.translatable("station.serfdom." + k.named()));
                }
                yield Component.translatable("screen.serfdom.stock.no_station", list).withStyle(ChatFormatting.RED);
            }
            case NO_FUEL -> Component.translatable("screen.serfdom.stock.no_fuel").withStyle(ChatFormatting.RED);
            case NO_TOOL -> Component.translatable("screen.serfdom.stock.no_tool").withStyle(ChatFormatting.RED);
        };
    }

    static void edited(ServerPlayer player, PostEdit edit) {
        if (player.blockPosition().distSqr(edit.pos()) > REACH * REACH) return;
        if (!(player.level().getBlockEntity(edit.pos()) instanceof WorkPostBlockEntity post) || !post.ownedBy(player.getUUID())) return;
        if (Jobs.get(edit.job()).isEmpty()) return;
        boolean jobChanged = !edit.job().equals(post.job());
        post.setJob(edit.job(), edit.radius());
        post.setOutline(edit.outline());
        if (jobChanged) send(player, postView(player.serverLevel(), post));
    }

    /** effects: changes the post's stock list as the screen asks, when the item is one its job's
     * stations make, and shows the post again. */
    static void stocked(ServerPlayer player, StockEdit edit) {
        if (player.blockPosition().distSqr(edit.pos()) > REACH * REACH) return;
        if (!(player.level().getBlockEntity(edit.pos()) instanceof WorkPostBlockEntity post) || !post.ownedBy(player.getUUID())) return;
        var job = Jobs.get(post.job());
        if (job.isEmpty() || job.get().target() != com.chunkworks.serfdom.domain.JobScript.Target.WORKSHOP) return;
        var stock = post.stock();
        int i = edit.index();
        if (i < 0 || i > stock.rows().size() || edit.keep() < 0 || edit.keep() > com.chunkworks.serfdom.domain.Stock.MAX_KEEP) return;
        if (edit.keep() == 0) {
            if (i < stock.rows().size()) post.setStock(stock.without(i));
        } else {
            var item = edit.item().toString();
            if (!com.chunkworks.serfdom.job.RecipeBook.rules(player.level(), job.get().stations()).results().contains(item)) return;
            post.setStock(stock.with(i, new com.chunkworks.serfdom.domain.Stock.Row(item, edit.keep())));
        }
        send(player, postView(player.serverLevel(), post));
    }
}
