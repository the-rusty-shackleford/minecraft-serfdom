/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.Workers;
import com.chunkworks.serfdom.domain.Shopping;
import com.chunkworks.serfdom.job.Kitchen;
import com.chunkworks.serfdom.job.RecipeBook;
import com.chunkworks.serfdom.job.Storage;
import com.chunkworks.serfdom.post.Posts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** What a shopper carries home (D-0006), saved with it, so a trip cut short (a panic, the night, the
 * chunk unloading) loses nothing: it puts the goods away at its next chance. One of them is shown in
 * its hand on the way, a copy marked as only shown, which nothing takes for a tool, which never drops,
 * and which is taken away as the villager loads. A shopper that dies or turns drops what it carries. Goods go where the want said: a free villager's household, a worker's
 * home chest, its post's chests; a villager whose state has changed since puts them where its state
 * says now. */
public final class Baskets {
    private static final String SHOWN = "serfdom_shown";
    private Baskets() {}

    /** The goods and where they go. Immutable. */
    public record Basket(List<ItemStack> goods, Shopping.Dest dest) {
        public static final Codec<Basket> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.listOf().fieldOf("goods").forGetter(Basket::goods),
                Codec.STRING.xmap(s -> Shopping.Dest.valueOf(s.toUpperCase(Locale.ROOT)), d -> d.name().toLowerCase(Locale.ROOT)).fieldOf("dest").forGetter(Basket::dest)
        ).apply(i, Basket::new));
        public Basket {
            goods = goods.stream().filter(s -> !s.isEmpty()).map(ItemStack::copy).toList();
            Objects.requireNonNull(dest);
        }
        public boolean empty() { return goods.isEmpty(); }
    }

    /** effects: what the villager carries; nothing when it carries nothing. */
    public static Optional<Basket> of(Villager villager) { return villager.getExistingData(Serfdom.BASKET).filter(b -> !b.empty()); }

    /** effects: the villager carries {@code goods} on top of what it carried, to {@code dest}. */
    public static void carry(Villager villager, List<ItemStack> goods, Shopping.Dest dest) {
        var all = new ArrayList<ItemStack>();
        of(villager).ifPresent(b -> all.addAll(b.goods()));
        all.addAll(goods);
        villager.setData(Serfdom.BASKET, new Basket(all, dest));
        show(villager);
    }

    /** effects: true iff the stack is one only shown in a hand. */
    public static boolean shown(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(SHOWN);
    }

    /** effects: the first good shown in the villager's hand, when its hand is free. */
    public static void show(Villager villager) {
        var basket = of(villager);
        if (basket.isEmpty() || !villager.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) return;
        villager.setItemSlot(EquipmentSlot.MAINHAND, shownCopy(basket.get().goods().getFirst()));
    }

    /** effects: one of {@code stack}, marked as only shown in a hand. */
    public static ItemStack shownCopy(ItemStack stack) {
        var copy = stack.copyWithCount(1);
        var tag = new CompoundTag();
        tag.putBoolean(SHOWN, true);
        copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return copy;
    }

    /** effects: a stack only shown in the villager's hand is taken away. */
    public static void clearShown(Villager villager) {
        if (shown(villager.getItemBySlot(EquipmentSlot.MAINHAND))) villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** effects: where the villager puts its goods away now: its household and so its bed; its home
     * chest; its post. Empty when it has nowhere (no bed, no home chest, no loaded post). */
    public static Optional<BlockPos> destination(ServerLevel level, Villager villager, Shopping.Dest dest) {
        return switch (where(villager, dest)) {
            case HOUSEHOLD -> villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)
                    .filter(h -> h.dimension() == level.dimension()).map(h -> h.pos());
            case HOME -> Kitchen.home(level, villager);
            case POST -> Workers.of(villager).post().filter(p -> p.dimension() == level.dimension()).map(p -> p.pos());
        };
    }

    /** effects: where goods meant for {@code dest} go, by the villager's state now: a free villager's
     * household; a worker's home chest unless they were for its post. */
    static Shopping.Dest where(Villager villager, Shopping.Dest dest) {
        if (!Workers.of(villager).owned()) return Shopping.Dest.HOUSEHOLD;
        return dest == Shopping.Dest.POST ? Shopping.Dest.POST : Shopping.Dest.HOME;
    }

    static void listen() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) -> {
            if (!(e.getEntity() instanceof Villager v) || v.level().isClientSide()) return;
            e.getDrops().removeIf(d -> shown(d.getItem()));
            for (var s : of(v).map(Basket::goods).orElse(List.of()))
                e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(v.level(), v.getX(), v.getY(), v.getZ(), s.copy()));
            v.removeData(Serfdom.BASKET);
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Post e) -> {
            if (!(e.getEntity() instanceof Villager v) || v.level().isClientSide()) return;
            for (var s : of(v).map(Basket::goods).orElse(List.of())) v.spawnAtLocation(s.copy());
            v.removeData(Serfdom.BASKET);
        });
    }

    /** effects: puts the villager's goods away where they go: into its household; into its home chest
     * (what does not fit dropped at its feet); sorted into its post's chests as its job sorts (what
     * does not fit dropped by the post). Its hand is emptied of what it showed. */
    public static void putAway(ServerLevel level, Villager villager) {
        clearShown(villager);
        var basket = of(villager);
        villager.removeData(Serfdom.BASKET);
        if (basket.isEmpty()) return;
        var goods = basket.get().goods();
        switch (where(villager, basket.get().dest())) {
            case HOUSEHOLD -> {
                var h = Households.of(villager);
                for (var s : goods) h = h.add(RecipeBook.key(s.getItem()), s.getCount());
                Households.set(villager, h);
            }
            case HOME -> {
                var home = Kitchen.home(level, villager).flatMap(p -> Storage.handler(level, p)).orElse(null);
                for (var s : goods) {
                    var rest = home == null ? s : ItemHandlerHelper.insertItemStacked(home, s.copy(), false);
                    if (!rest.isEmpty()) villager.spawnAtLocation(rest);
                }
            }
            case POST -> {
                var post = Workers.of(villager).post().flatMap(p -> Posts.loaded(level.getServer(), p)).orElse(null);
                for (var s : goods) {
                    var rest = s.copy();
                    if (post != null) for (var pos : Storage.order(level, post, rest)) {
                        var h = Storage.handler(level, pos).orElse(null);
                        if (h != null) rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
                        if (rest.isEmpty()) break;
                    }
                    if (!rest.isEmpty()) {
                        if (post != null) net.minecraft.world.Containers.dropItemStack(level, post.getBlockPos().getX() + 0.5, post.getBlockPos().getY() + 1, post.getBlockPos().getZ() + 0.5, rest);
                        else villager.spawnAtLocation(rest);
                    }
                }
            }
        }
    }
}
