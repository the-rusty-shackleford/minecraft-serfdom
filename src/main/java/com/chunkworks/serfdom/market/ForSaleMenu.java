/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.chunkworks.serfdom.Serfdom;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The For Sale block's menu (D-0006): the slot showing what it sells (a click with an item sets it,
 * an empty click clears it, and nothing is taken), its stock below, its proceeds under that, the
 * player's inventory and hotbar at the bottom, and how many a sale and a sale's price as numbers the
 * client's buttons change ({@link #clickMenuButton}). Only the stall's owner, within reach, while the
 * stall stands, may use it; the server asks on every click and every tick. On a client its slots hide
 * while the ledger is shown. */
public final class ForSaleMenu extends AbstractContainerMenu {
    /** Slot numbers: the item sold, the stock's 18, the proceeds' 9, the inventory's 27, the hotbar's 9. */
    public static final int SELLS = 0, STOCK = 1, PROCEEDS = STOCK + ForSaleBlockEntity.STOCK, INVENTORY = PROCEEDS + ForSaleBlockEntity.PROCEEDS,
            HOTBAR = INVENTORY + 27, END = HOTBAR + 9;
    /** The buttons: how many a sale less and more by one, by eight; the price likewise. */
    public static final int QTY_DOWN = 0, QTY_UP = 1, QTY_DOWN_8 = 2, QTY_UP_8 = 3, PRICE_DOWN = 4, PRICE_UP = 5, PRICE_DOWN_8 = 6, PRICE_UP_8 = 7;
    /** Where things are in the panel, in GUI pixels: a slot's item corner. */
    public static final int WIDTH = 176, SELLS_X = 8, SELLS_Y = 22, STOCK_Y = 54, PROCEEDS_Y = 104, INVENTORY_Y = 138, HOTBAR_Y = 196, HEIGHT = 220;
    static final double REACH = 8.0;

    @Nullable private final ForSaleBlockEntity stall;
    private final Container sells;
    private final ContainerData numbers;
    /** On a client: true while the ledger is shown, which hides the slots. */
    public boolean ledgerShown;

    /** effects: the server's menu, on the stall. */
    public ForSaleMenu(int id, Inventory inventory, ForSaleBlockEntity stall) {
        this(id, inventory, stall, new Sells(stall), stall.stockContainer, stall.proceedsContainer, new ContainerData() {
            @Override public int get(int i) { return i == 0 ? stall.quantity() : stall.price(); }
            @Override public void set(int i, int v) { if (i == 0) stall.setQuantity(v); else stall.setPrice(v); }
            @Override public int getCount() { return 2; }
        });
    }

    /** effects: the client's menu, as the open packet makes it; the server fills it. */
    public ForSaleMenu(int id, Inventory inventory) {
        this(id, inventory, null, new SimpleContainer(1), new SimpleContainer(ForSaleBlockEntity.STOCK), new SimpleContainer(ForSaleBlockEntity.PROCEEDS), new SimpleContainerData(2));
    }

    private ForSaleMenu(int id, Inventory inventory, @Nullable ForSaleBlockEntity stall, Container sells, Container stock, Container proceeds, ContainerData numbers) {
        super(Serfdom.FOR_SALE_MENU.get(), id);
        this.stall = stall;
        this.sells = sells;
        this.numbers = numbers;
        addSlot(new Shown(sells, 0, SELLS_X, SELLS_Y) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
            @Override public boolean mayPickup(Player p) { return false; }
        });
        for (int row = 0; row < 2; row++) for (int col = 0; col < 9; col++)
            addSlot(new Shown(stock, row * 9 + col, 8 + col * 18, STOCK_Y + row * 18) {
                @Override public boolean mayPlace(ItemStack s) { return ForSaleMenu.this.stall != null ? container.canPlaceItem(getContainerSlot(), s) : sellsHere(s); }
            });
        for (int col = 0; col < 9; col++)
            addSlot(new Shown(proceeds, col, 8 + col * 18, PROCEEDS_Y) {
                @Override public boolean mayPlace(ItemStack s) { return false; }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Shown(inventory, 9 + row * 9 + col, 8 + col * 18, INVENTORY_Y + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Shown(inventory, col, 8 + col * 18, HOTBAR_Y));
        addDataSlots(numbers);
    }

    /** A slot that hides on a client while the ledger is shown. */
    private class Shown extends Slot {
        Shown(Container c, int i, int x, int y) { super(c, i, x, y); }
        @Override public boolean isActive() { return !ledgerShown; }
    }

    /** On a client, what the slot of the item sold shows decides what its stock takes. */
    private boolean sellsHere(ItemStack s) { return !sells.getItem(0).isEmpty() && ItemStack.isSameItemSameComponents(sells.getItem(0), s); }

    /** effects: how many a sale, as the server last said. */
    public int quantity() { return Math.max(1, numbers.get(0)); }
    /** effects: a sale's price, as the server last said. */
    public int price() { return Math.max(1, numbers.get(1)); }

    /** effects: a button: how many a sale or the price, by one or by eight, held to 1 to 64. */
    @Override public boolean clickMenuButton(Player player, int id) {
        if (stall == null) return false;
        switch (id) {
            case QTY_DOWN -> stall.setQuantity(stall.quantity() - 1);
            case QTY_UP -> stall.setQuantity(stall.quantity() + 1);
            case QTY_DOWN_8 -> stall.setQuantity(stall.quantity() - 8);
            case QTY_UP_8 -> stall.setQuantity(stall.quantity() + 8);
            case PRICE_DOWN -> stall.setPrice(stall.price() - 1);
            case PRICE_UP -> stall.setPrice(stall.price() + 1);
            case PRICE_DOWN_8 -> stall.setPrice(stall.price() - 8);
            case PRICE_UP_8 -> stall.setPrice(stall.price() + 8);
            default -> { return false; }
        }
        return true;
    }

    /** effects: a click on the slot of the item sold sets it from what the cursor holds, or clears it
     * when the cursor is empty, taking nothing; any other click as a menu's. */
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot == SELLS) {
            if (stall != null && (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE)) stall.setTemplate(getCarried());
            return;
        }
        super.clicked(slot, button, type, player);
    }

    @Override public boolean stillValid(Player player) {
        if (stall == null || player.level().isClientSide()) return true;
        return !stall.isRemoved() && stall.getLevel() == player.level() && stall.ownedBy(player.getUUID())
                && player.distanceToSqr(stall.getBlockPos().getCenter()) <= REACH * REACH;
    }

    /** effects: a shift-click: from the stock or the proceeds to the player; from the player into the
     * stock when it is what the stall sells. */
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (index == SELLS || !slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        boolean moved = index < INVENTORY ? moveItemStackTo(stack, INVENTORY, END, true) : moveItemStackTo(stack, STOCK, PROCEEDS, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, before); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }

    /** effects: the stall this menu opened on; nothing on a client. */
    @Nullable public ForSaleBlockEntity stall() { return stall; }

    /** The slot of the item sold, on the server: what the stall sells. */
    private static final class Sells implements Container {
        private final ForSaleBlockEntity stall;
        Sells(ForSaleBlockEntity stall) { this.stall = stall; }
        @Override public int getContainerSize() { return 1; }
        @Override public boolean isEmpty() { return stall.template().isEmpty(); }
        @Override public ItemStack getItem(int i) { return stall.template(); }
        @Override public ItemStack removeItem(int i, int n) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int i) { return ItemStack.EMPTY; }
        @Override public void setItem(int i, ItemStack s) {}
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() {}
    }
}
