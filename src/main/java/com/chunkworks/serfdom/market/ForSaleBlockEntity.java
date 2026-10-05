/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.domain.Ledger;
import com.chunkworks.serfdom.domain.Stall;
import com.chunkworks.serfdom.domain.Verdict;
import com.chunkworks.serfdom.job.RecipeBook;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/** A For Sale block (D-0006): its owner; the item it sells (a copy, components and all; empty when
 * none is set), how many a sale and a sale's price; its stock ({@link #STOCK} slots, which take only
 * that item) and its proceeds ({@link #PROCEEDS} slots of emeralds, which only give); its ledger; and
 * the villager it is serving now, if any (one at a time; a customer that never let go is forgotten
 * after {@link #SERVE_FOR} ticks). Clients get the owner, the item, the quantity, the price and
 * whether it is open, to draw it and its label.
 *
 * <p>Rep invariant: 1 &le; quantity, price &le; {@link Stall#MOST}; the template's count is 1 when it
 * is set; the proceeds hold only emeralds. */
public final class ForSaleBlockEntity extends BlockEntity {
    public static final int STOCK = 18, PROCEEDS = 9;
    /** Ticks a customer may hold the stall before it is taken for one that never let go. */
    static final long SERVE_FOR = 400;
    private Optional<UUID> owner = Optional.empty();
    private String ownerName = "";
    private ItemStack template = ItemStack.EMPTY;
    private int quantity = 1, price = 1;
    private final NonNullList<ItemStack> stock = NonNullList.withSize(STOCK, ItemStack.EMPTY);
    private final NonNullList<ItemStack> proceeds = NonNullList.withSize(PROCEEDS, ItemStack.EMPTY);
    private Ledger ledger = Ledger.EMPTY;
    private UUID customer;
    private long customerSince;
    private boolean clientOpen;

    public ForSaleBlockEntity(BlockPos pos, BlockState state) { super(Serfdom.FOR_SALE_ENTITY.get(), pos, state); }

    // ---- the owner ----------------------------------------------------------------------------------

    public Optional<UUID> owner() { return owner; }
    public String ownerName() { return ownerName; }
    /** effects: true iff {@code player} may open and break it: its owner, or anyone while it has none. */
    public boolean ownedBy(UUID player) { return owner.isEmpty() || owner.get().equals(player); }
    public void claim(UUID player, String name) { owner = Optional.of(player); ownerName = name; offerChanged(); }

    // ---- the offer ----------------------------------------------------------------------------------

    public ItemStack template() { return template.copy(); }
    public int quantity() { return quantity; }
    public int price() { return price; }

    /** effects: sells {@code stack} (one of it, components and all), or nothing when it is empty; an
     * emerald is never sold for emeralds. */
    public void setTemplate(ItemStack stack) {
        template = stack.isEmpty() || stack.is(Items.EMERALD) ? ItemStack.EMPTY : stack.copyWithCount(1);
        offerChanged();
    }
    public void setQuantity(int n) { quantity = Math.clamp(n, 1, Stall.MOST); offerChanged(); }
    public void setPrice(int n) { price = Math.clamp(n, 1, Stall.MOST); offerChanged(); }

    /** effects: true iff {@code stack} is what the stall sells. */
    public boolean sells(ItemStack stack) { return !template.isEmpty() && ItemStack.isSameItemSameComponents(template, stack); }

    /** effects: the stall as the domain sees it now. */
    public Stall stall() {
        return new Stall(template.isEmpty() ? "" : RecipeBook.key(template.getItem()), quantity, price, stockCount(), room());
    }

    /** effects: how many of what it sells its stock holds. */
    public int stockCount() {
        int n = 0;
        for (var s : stock) if (sells(s)) n += s.getCount();
        return n;
    }

    /** effects: how many more emeralds its proceeds can take. */
    public int room() {
        int n = 0;
        for (var s : proceeds) n += s.isEmpty() ? Items.EMERALD.getDefaultMaxStackSize() : s.is(Items.EMERALD) ? s.getMaxStackSize() - s.getCount() : 0;
        return n;
    }

    /** effects: on a client, whether the server says it sells now. */
    public boolean clientOpen() { return clientOpen; }

    // ---- a sale -------------------------------------------------------------------------------------

    /** requires: the stall has {@code sales} sales in a row ({@link Stall#sales}). effects: takes
     * {@code sales} sales' worth of the item out of its stock and puts their price into its proceeds;
     * returns the goods. */
    public List<ItemStack> sell(int sales) {
        int want = sales * quantity, pay = sales * price;
        if (sales < 1 || want > stockCount() || pay > room()) throw new IllegalStateException("can't make " + sales + " sales");
        var out = new ArrayList<ItemStack>();
        for (int i = 0; i < stock.size() && want > 0; i++) {
            var s = stock.get(i);
            if (!sells(s)) continue;
            var taken = s.split(Math.min(want, s.getCount()));
            want -= taken.getCount();
            out.add(taken);
        }
        for (int i = 0; i < proceeds.size() && pay > 0; i++) {
            var s = proceeds.get(i);
            if (s.isEmpty()) {
                int put = Math.min(pay, Items.EMERALD.getDefaultMaxStackSize());
                proceeds.set(i, new ItemStack(Items.EMERALD, put));
                pay -= put;
            } else if (s.is(Items.EMERALD)) {
                int put = Math.min(pay, s.getMaxStackSize() - s.getCount());
                s.grow(put);
                pay -= put;
            }
        }
        changed();
        return out;
    }

    // ---- the ledger ---------------------------------------------------------------------------------

    public Ledger ledger() { return ledger; }

    /** effects: the visit written in the ledger, and shown to the owner when they have it open. */
    public void write(Ledger.Line line) {
        ledger = ledger.write(line);
        setChanged();
        if (level instanceof net.minecraft.server.level.ServerLevel server) Stalls.ledgerChanged(server, this);
    }

    // ---- one customer at a time ---------------------------------------------------------------------

    /** effects: true iff the stall serves {@code who} now: nobody else is being served (or the one
     * who was has held it longer than {@link #SERVE_FOR}); it is then {@code who}'s until let go. */
    public boolean serve(UUID who, long now) {
        if (customer != null && !customer.equals(who) && now - customerSince < SERVE_FOR) return false;
        if (!who.equals(customer)) customerSince = now;
        customer = who;
        return true;
    }

    /** effects: {@code who} is served no more. */
    public void letGo(UUID who) { if (who.equals(customer)) customer = null; }

    /** effects: true iff someone other than {@code who} is being served. */
    public boolean busy(UUID who, long now) { return customer != null && !customer.equals(who) && now - customerSince < SERVE_FOR; }

    // ---- the menu's containers and the hoppers' handler -------------------------------------------------

    /** The stock as a container: it takes only what the stall sells. */
    public final Container stockContainer = new Part(stock) {
        @Override public boolean canPlaceItem(int slot, ItemStack s) { return sells(s); }
    };
    /** The proceeds as a container: emeralds, which only come out. */
    public final Container proceedsContainer = new Part(proceeds) {
        @Override public boolean canPlaceItem(int slot, ItemStack s) { return false; }
    };

    private class Part implements Container {
        private final NonNullList<ItemStack> items;
        Part(NonNullList<ItemStack> items) { this.items = items; }
        @Override public int getContainerSize() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int i) { return items.get(i); }
        @Override public ItemStack removeItem(int i, int n) { var s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) changed(); return s; }
        @Override public ItemStack removeItemNoUpdate(int i) { var s = ContainerHelper.takeItem(items, i); changed(); return s; }
        @Override public void setItem(int i, ItemStack s) { items.set(i, s); changed(); }
        @Override public void setChanged() { changed(); }
        @Override public boolean stillValid(Player p) { return !isRemoved(); }
        @Override public void clearContent() { items.clear(); changed(); }
    }

    /** What hoppers, pipes and Create see: the stock's slots, which take what the stall sells, then the
     * proceeds' slots, which give their emeralds and take nothing. */
    public final IItemHandler handler = new IItemHandler() {
        @Override public int getSlots() { return STOCK + PROCEEDS; }
        @Override public ItemStack getStackInSlot(int slot) { return slot < STOCK ? stock.get(slot) : proceeds.get(slot - STOCK); }
        @Override public ItemStack insertItem(int slot, ItemStack s, boolean simulate) {
            if (slot >= STOCK || s.isEmpty() || !sells(s)) return s;
            var here = stock.get(slot);
            if (!here.isEmpty() && !ItemStack.isSameItemSameComponents(here, s)) return s;
            int room = Math.min(s.getMaxStackSize(), getSlotLimit(slot)) - here.getCount();
            if (room <= 0) return s;
            int put = Math.min(room, s.getCount());
            if (!simulate) {
                if (here.isEmpty()) stock.set(slot, s.copyWithCount(put)); else here.grow(put);
                changed();
            }
            return s.copyWithCount(s.getCount() - put);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < STOCK || amount <= 0) return ItemStack.EMPTY;
            var here = proceeds.get(slot - STOCK);
            if (here.isEmpty()) return ItemStack.EMPTY;
            int take = Math.min(amount, here.getCount());
            var out = here.copyWithCount(take);
            if (!simulate) { here.shrink(take); changed(); }
            return out;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack s) { return slot < STOCK && sells(s); }
    };

    /** effects: what is left in it, to drop when it is broken: its stock and its proceeds. */
    public List<ItemStack> contents() {
        var out = new ArrayList<ItemStack>();
        for (var s : stock) if (!s.isEmpty()) out.add(s.copy());
        for (var s : proceeds) if (!s.isEmpty()) out.add(s.copy());
        return out;
    }

    // ---- saving and syncing -------------------------------------------------------------------------

    private boolean wasOpen;

    /** effects: saved, and clients told when whether it sells has changed: a hopper's every item does
     * not reach them. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide && stall().open() != wasOpen) sync();
    }

    /** effects: saved, and clients told: the owner or the offer changed. */
    private void offerChanged() {
        setChanged();
        if (level != null && !level.isClientSide) sync();
    }

    private void sync() {
        wasOpen = stall().open();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    static final Codec<Ledger> LEDGER_CODEC = RecordCodecBuilder.create(i -> i.group(
            RecordCodecBuilder.<Ledger.Line>create(l -> l.group(
                    Codec.LONG.fieldOf("time").forGetter(Ledger.Line::dayTime),
                    Codec.STRING.fieldOf("profession").forGetter(Ledger.Line::profession),
                    Codec.STRING.xmap(s -> Verdict.Reaction.valueOf(s.toUpperCase(Locale.ROOT)), r -> r.name().toLowerCase(Locale.ROOT)).fieldOf("reaction").forGetter(Ledger.Line::reaction),
                    Codec.INT.optionalFieldOf("sales", 0).forGetter(Ledger.Line::sales),
                    Codec.INT.optionalFieldOf("items", 0).forGetter(Ledger.Line::items),
                    Codec.INT.optionalFieldOf("paid", 0).forGetter(Ledger.Line::paid)
            ).apply(l, Ledger.Line::new)).listOf().fieldOf("lines").forGetter(Ledger::lines),
            RecordCodecBuilder.<Ledger.Totals>create(t -> t.group(
                    Codec.LONG.fieldOf("day").forGetter(Ledger.Totals::day),
                    Codec.INT.fieldOf("sold").forGetter(Ledger.Totals::sold),
                    Codec.INT.fieldOf("earned").forGetter(Ledger.Totals::earned),
                    Codec.INT.fieldOf("too_pricey").forGetter(Ledger.Totals::tooPricey),
                    Codec.INT.fieldOf("cant_afford").forGetter(Ledger.Totals::cantAfford),
                    Codec.INT.fieldOf("not_interested").forGetter(Ledger.Totals::notInterested)
            ).apply(t, Ledger.Totals::new)).listOf().fieldOf("days").forGetter(Ledger::days)
    ).apply(i, Ledger::new));

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        owner.ifPresent(u -> tag.putUUID("Owner", u));
        tag.putString("OwnerName", ownerName);
        writeOffer(tag, registries);
        var s = new CompoundTag();
        ContainerHelper.saveAllItems(s, stock, registries);
        tag.put("Stock", s);
        var p = new CompoundTag();
        ContainerHelper.saveAllItems(p, proceeds, registries);
        tag.put("Proceeds", p);
        LEDGER_CODEC.encodeStart(NbtOps.INSTANCE, ledger).result().ifPresent(t -> tag.put("Ledger", t));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty();
        ownerName = tag.getString("OwnerName");
        readOffer(tag, registries);
        stock.clear();
        ContainerHelper.loadAllItems(tag.getCompound("Stock"), stock, registries);
        proceeds.clear();
        ContainerHelper.loadAllItems(tag.getCompound("Proceeds"), proceeds, registries);
        ledger = tag.contains("Ledger") ? LEDGER_CODEC.parse(NbtOps.INSTANCE, tag.get("Ledger")).result().orElse(Ledger.EMPTY) : Ledger.EMPTY;
    }

    private void writeOffer(CompoundTag tag, HolderLookup.Provider registries) {
        if (!template.isEmpty()) tag.put("Sells", template.save(registries));
        tag.putInt("Quantity", quantity);
        tag.putInt("Price", price);
    }

    private void readOffer(CompoundTag tag, HolderLookup.Provider registries) {
        template = tag.contains("Sells") ? ItemStack.parse(registries, tag.getCompound("Sells")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        quantity = Math.clamp(tag.contains("Quantity") ? tag.getInt("Quantity") : 1, 1, Stall.MOST);
        price = Math.clamp(tag.contains("Price") ? tag.getInt("Price") : 1, 1, Stall.MOST);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        owner.ifPresent(u -> tag.putUUID("Owner", u));
        tag.putString("OwnerName", ownerName);
        writeOffer(tag, registries);
        tag.putBoolean("Open", stall().open());
        return tag;
    }

    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) { readClient(tag, registries); }

    @Override public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        readClient(packet.getTag(), registries);
    }

    private void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        owner = tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty();
        ownerName = tag.getString("OwnerName");
        readOffer(tag, registries);
        clientOpen = tag.getBoolean("Open");
    }
}
