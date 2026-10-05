/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom;

import com.chunkworks.serfdom.domain.Parting;
import com.chunkworks.serfdom.domain.Wardrobe;
import com.chunkworks.serfdom.domain.WorkerLayout;
import com.mojang.datafixers.util.Pair;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** The Worker Screen's menu (D-0004): the worker's four armour slots, read and written straight on
 * the villager, above the player's inventory and hotbar. On the server it is bound to the villager
 * from the start; on the client it is bound when the screen's view arrives, just after the open.
 * Only the owner of a living villager within reach may use it ({@link Wardrobe#access}); the server
 * asks on every click and every tick, so a screen left open when the villager dies, goes free or is
 * led off is shut and nothing can be taken through it. A piece put on is marked to drop whole
 * whenever the villager dies ({@link Parting#GUARANTEED}). */
public final class WorkerMenu extends AbstractContainerMenu {
    /** The villager's armour slots, top to bottom, as {@link Wardrobe.Slot} orders them. */
    public static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] EMPTY = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
    /** The menu's slot numbers: the four worn, then the inventory's 27, then the hotbar's 9. */
    public static final int WEARING = 0, INVENTORY = 4, HOTBAR = 31, END = 40;

    @Nullable private Villager villager;

    /** effects: the server's menu, on {@code villager}'s own armour slots. */
    public WorkerMenu(int id, Inventory inventory, Villager villager) {
        this(id, inventory, new Wearing(villager));
        this.villager = villager;
    }

    /** effects: the client's menu, as the open packet makes it; its slots are filled by the server. */
    public WorkerMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(4)); }

    private WorkerMenu(int id, Inventory inventory, Container wearing) {
        super(Serfdom.WORKER_MENU.get(), id);
        for (int i = 0; i < 4; i++) addSlot(new WearSlot(wearing, i));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, WorkerLayout.INVENTORY_X + col * WorkerLayout.SLOT, WorkerLayout.INVENTORY_Y + row * WorkerLayout.SLOT));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, WorkerLayout.INVENTORY_X + col * WorkerLayout.SLOT, WorkerLayout.HOTBAR_Y));
    }

    /** requires: the client's menu. effects: binds it to the villager its view names. */
    public void bind(Villager v) { if (villager == null) villager = v; }

    /** effects: the villager the menu dresses, or nothing on a client whose view has not come. */
    @Nullable public Villager villager() { return villager; }

    /** effects: whether {@code player} may still use the slots: on the server, the owner of a
     * living villager in their level within reach; the client trusts the server. */
    @Override public boolean stillValid(Player player) {
        if (villager == null || player.level().isClientSide()) return true;
        return access(player, villager) == Wardrobe.Access.OK;
    }

    /** effects: whether {@code player} may use {@code villager}'s slots now. */
    public static Wardrobe.Access access(Player player, Villager villager) {
        boolean here = villager.isAlive() && !villager.isRemoved() && villager.level() == player.level();
        return Wardrobe.access(here, Workers.of(villager).ownedBy(player.getUUID()), villager.distanceTo(player));
    }

    /** effects: a shift-click on slot {@code index}, moved as {@link Wardrobe#route} says. */
    @Override public ItemStack quickMoveStack(Player player, int index) {
        var slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var before = stack.copy();
        var from = index < INVENTORY ? Wardrobe.Region.WEARING : index < HOTBAR ? Wardrobe.Region.INVENTORY : Wardrobe.Region.HOTBAR;
        var worn = EnumSet.noneOf(Wardrobe.Slot.class);
        for (int i = 0; i < 4; i++) if (slots.get(WEARING + i).hasItem()) worn.add(Wardrobe.Slot.values()[i]);
        var route = Wardrobe.route(from, piece(stack, villager), worn, player.isCreative());
        boolean moved = false;
        if (route.wear().isPresent()) {
            int to = WEARING + route.wear().get().ordinal();
            moved = moveItemStackTo(stack, to, to + 1, false);
        } else for (var region : route.regions()) {
            moved = switch (region) {
                case WEARING -> moveItemStackTo(stack, WEARING, INVENTORY, false);
                case INVENTORY -> moveItemStackTo(stack, INVENTORY, HOTBAR, false);
                case HOTBAR -> moveItemStackTo(stack, HOTBAR, END, false);
            };
            if (moved) break;
        }
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY, before);
        else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return before;
    }

    /** effects: the stack as the wardrobe sees it: where it may be worn by {@code wearer} (nowhere
     * when the wearer is not known yet) and its curses. */
    public static Wardrobe.Piece piece(ItemStack stack, @Nullable LivingEntity wearer) {
        if (stack.isEmpty()) return Wardrobe.Piece.NOTHING;
        var fits = EnumSet.noneOf(Wardrobe.Slot.class);
        if (wearer != null) for (int i = 0; i < 4; i++) if (stack.canEquip(WORN[i], wearer)) fits.add(Wardrobe.Slot.values()[i]);
        return new Wardrobe.Piece(false, fits, EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE),
                EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP));
    }

    /** One of the four: one piece, one that may be worn there; binding keeps it on outside creative. */
    private final class WearSlot extends Slot {
        private final Wardrobe.Slot which;
        WearSlot(Container wearing, int index) {
            super(wearing, index, WorkerLayout.WEAR_X, WorkerLayout.WEAR_Y + index * WorkerLayout.SLOT);
            which = Wardrobe.Slot.values()[index];
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean mayPlace(ItemStack stack) {
            // A client not yet bound lets the server decide.
            return villager == null ? !stack.isEmpty() : Wardrobe.takes(which, piece(stack, villager));
        }
        @Override public boolean mayPickup(Player player) {
            return Wardrobe.mayTakeOff(piece(getItem(), villager), player.isCreative()) && super.mayPickup(player);
        }
        @Override public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() { return Pair.of(InventoryMenu.BLOCK_ATLAS, EMPTY[which.ordinal()]); }
    }

    /** The villager's armour slots as a container: read and written on the villager, so what it
     * wears is vanilla's armour (it protects, renders and is saved as the villager's own). A piece
     * put in is marked to drop whole whenever the villager dies. */
    static final class Wearing implements Container {
        private final Villager villager;
        Wearing(Villager villager) { this.villager = villager; }
        @Override public int getContainerSize() { return 4; }
        @Override public boolean isEmpty() {
            for (var slot : WORN) if (!villager.getItemBySlot(slot).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int index) { return villager.getItemBySlot(WORN[index]); }
        @Override public ItemStack removeItem(int index, int count) {
            var stack = getItem(index);
            if (stack.isEmpty() || count <= 0) return ItemStack.EMPTY;
            var taken = stack.copyWithCount(Math.min(count, stack.getCount()));
            setItem(index, stack.copyWithCount(stack.getCount() - taken.getCount()));
            return taken;
        }
        @Override public ItemStack removeItemNoUpdate(int index) {
            var stack = getItem(index);
            villager.setItemSlot(WORN[index], ItemStack.EMPTY);
            return stack;
        }
        @Override public void setItem(int index, ItemStack stack) {
            villager.setItemSlot(WORN[index], stack);
            if (!stack.isEmpty()) villager.setDropChance(WORN[index], Parting.GUARANTEED);
        }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { for (int i = 0; i < 4; i++) setItem(i, ItemStack.EMPTY); }
    }
}
