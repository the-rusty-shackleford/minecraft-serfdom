/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.WorkerMenu;
import com.chunkworks.serfdom.domain.Fit;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/** Armour on a villager (D-0004): every villager, owned or not, so a piece a dispenser put on shows
 * too. Drawn by vanilla's own armour layer, so dyes, trims, the enchantment glint and a mod's own
 * armour models and textures all work, on a stand-in player model posed as {@link Fit} says from the
 * villager's model as it stands this frame. The stand-in is one shared model, posed per draw; a
 * villager wearing nothing costs four empty-slot reads. */
public final class VillagerArmourLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private final HumanoidModel<Villager> standIn;
    private final HumanoidArmorLayer<Villager, HumanoidModel<Villager>, HumanoidModel<Villager>> armour;

    public VillagerArmourLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, EntityRendererProvider.Context context) {
        super(parent);
        // Any humanoid mesh serves: only its parts' poses are read, by the armour models copying them.
        standIn = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        var posed = new RenderLayerParent<Villager, HumanoidModel<Villager>>() {
            @Override public HumanoidModel<Villager> getModel() { return standIn; }
            @Override public ResourceLocation getTextureLocation(Villager villager) { return parent.getTextureLocation(villager); }
        };
        armour = new HumanoidArmorLayer<>(posed, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager());
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
                                 float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!wearsAny(villager)) return;
        var model = getParentModel();
        var head = model.getHead();
        var turned = Fit.head(head.xRot, head.yRot, head.zRot, new Fit.Vec(head.xScale, head.yScale, head.zScale));
        set(standIn.head, turned);
        set(standIn.hat, turned);
        set(standIn.body, Fit.body());
        set(standIn.rightArm, Fit.arm(true));
        set(standIn.leftArm, Fit.arm(false));
        set(standIn.rightLeg, Fit.leg(true, model.root().getChild("right_leg").xRot));
        set(standIn.leftLeg, Fit.leg(false, model.root().getChild("left_leg").xRot));
        // The renderer has already scaled a child down; the armour models must not do it again.
        standIn.young = false;
        standIn.crouching = false;
        standIn.riding = false;
        armour.render(pose, buffers, light, villager, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
    }

    private static boolean wearsAny(Villager villager) {
        for (var slot : WorkerMenu.WORN) if (!villager.getItemBySlot(slot).isEmpty()) return true;
        return false;
    }

    private static void set(ModelPart part, Fit.Pose p) {
        part.x = (float) p.pivot().x();
        part.y = (float) p.pivot().y();
        part.z = (float) p.pivot().z();
        part.xRot = (float) p.xRot();
        part.yRot = (float) p.yRot();
        part.zRot = (float) p.zRot();
        part.xScale = (float) p.scale().x();
        part.yScale = (float) p.scale().y();
        part.zScale = (float) p.scale().z();
    }
}
