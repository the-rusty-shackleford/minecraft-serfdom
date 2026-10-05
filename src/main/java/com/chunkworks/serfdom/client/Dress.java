/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.domain.Fit;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** The villager's own model dressed for what it wears (D-0004), around each villager's draw:
 * <ul>
 * <li>anything on its legs takes its robe off ({@link Fit#robeShown}), so leggings and trousers
 * show;</li>
 * <li>anything on its head hides its hat (a farmer's brim, a type's hood), which would poke through
 * a helmet. The profession layer sets the hat's visibility itself every draw, so the model's mixin
 * asks {@link #headCovered} after it.</li>
 * </ul>
 * Every villager shares one model, so each draw sets it before and puts it back after. */
public final class Dress {
    private static boolean headCovered;
    private Dress() {}

    /** effects: whether the villager being drawn wears something on its head. */
    public static boolean headCovered() { return headCovered; }

    static void before(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Villager villager) || !(event.getRenderer().getModel() instanceof VillagerModel<?> model)) return;
        jacket(model).visible = Fit.robeShown(!villager.getItemBySlot(EquipmentSlot.LEGS).isEmpty());
        headCovered = !villager.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        if (headCovered) model.getHead().getChild("hat").visible = false;
    }

    static void after(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Villager) || !(event.getRenderer().getModel() instanceof VillagerModel<?> model)) return;
        jacket(model).visible = true;
        headCovered = false;
    }

    private static ModelPart jacket(VillagerModel<?> model) { return model.root().getChild("body").getChild("jacket"); }
}
