/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Armour on a villager wears as on a player (D-0004, Rusty's call). Vanilla wears only a player's:
 * {@code LivingEntity.hurtArmor} and {@code hurtHelmet} do nothing, and {@code Player} fills them in
 * exactly so. A hit takes max(1, damage ÷ 4) from each piece, a falling block wears the helmet, a
 * piece that cannot be hurt (Unbreakable, or Lucky's Wardrobe's clothes) is not, and a spent piece
 * breaks off; all through NeoForge's {@code ArmorHurtEvent}, as a player's does. */
@Mixin(Villager.class)
abstract class VillagerWearMixin extends AbstractVillager {
    private VillagerWearMixin(EntityType<? extends AbstractVillager> type, Level level) { super(type, level); }

    @Override protected void hurtArmor(DamageSource source, float damage) {
        doHurtEquipment(source, damage, EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
    }

    @Override protected void hurtHelmet(DamageSource source, float damage) { doHurtEquipment(source, damage, EquipmentSlot.HEAD); }
}
