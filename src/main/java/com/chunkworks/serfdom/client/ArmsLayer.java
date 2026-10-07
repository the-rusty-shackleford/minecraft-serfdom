/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Serfdom;
import com.chunkworks.serfdom.domain.Fit;
import com.chunkworks.serfdom.domain.Grip;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemDisplayContext;

/** A villager's arms out of the fold, gripping a tool or a weapon (D-0011), posed by {@link Grip}.
 * The folded arms are hidden for it ({@link Dress}) and vanilla's item on them is not drawn
 * ({@code CrossedArmsItemLayerMixin}).
 *
 * <p>No art of ours: each arm is the folded arm's own upper sleeve (the villager's 4 by 8 by 4 box
 * at 44, 22) over a hand of the same size and place cut short, so it takes every skin's sleeve as it
 * is: drawn with the villager's skin, then its biome's and its profession's clothes, as the
 * profession layer draws the body. The hand is drawn with the bare skin alone (which is skin there;
 * every clothes layer covers it), a little narrower than the cuff. The arms pivot where a player's do,
 * so the item is placed in the hand as a player's third-person hand places it, and they hang from
 * the body as it stands this frame, so they follow a resource pack's body animations. */
public final class ArmsLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Serfdom.id("villager_arms"), "main");
    private static final Map<ResourceLocation, ResourceLocation> TYPES = new HashMap<>(), PROFESSIONS = new HashMap<>();
    private final ModelPart root, rightArm, leftArm, rightHand, leftHand;
    private final ItemInHandRenderer items;

    public ArmsLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, EntityRendererProvider.Context context) {
        super(parent);
        root = context.bakeLayer(LAYER);
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightHand = rightArm.getChild("hand");
        leftHand = leftArm.getChild("hand");
        items = context.getItemInHandRenderer();
    }

    /** effects: the two arms, each a sleeve over a hand, pivoting at the shoulders. */
    public static LayerDefinition create() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var hand = new CubeDeformation(-0.3F, 0, -0.3F);
        var right = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5, 2, 0));
        right.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(44, 22).addBox(-3, -2, -2, 4, 8, 4), PartPose.ZERO);
        right.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(44, 22).addBox(-3, 6, -2, 4, 4, 4, hand), PartPose.ZERO);
        var left = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5, 2, 0));
        left.addOrReplaceChild("sleeve", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1, -2, -2, 4, 8, 4), PartPose.ZERO);
        left.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(-1, 6, -2, 4, 4, 4, hand), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** effects: the arms' poses for {@code villager} this frame; empty when its arms stay folded. */
    public static Optional<Grip.Arms> arms(Villager villager, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!Grips.out(villager)) return Optional.empty();
        var moment = new Grip.Moment(limbSwing, Mth.clamp(limbSwingAmount, 0F, 1F), Mth.clamp(villager.getAttackAnim(partialTick), 0F, 1F),
                netHeadYaw * Mth.DEG_TO_RAD, headPitch * Mth.DEG_TO_RAD, villager.isAggressive(), ageInTicks);
        return Optional.of(Grip.arms(Grips.of(villager.getMainHandItem()), moment));
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
                                 float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (villager.isInvisible()) return;
        var arms = arms(villager, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch).orElse(null);
        if (arms == null) return;
        set(rightArm, arms.right());
        set(leftArm, arms.left());
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0.0F);
        pose.pushPose();
        getParentModel().root().getChild("body").translateAndRotate(pose);
        root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(villager))), light, overlay);
        rightHand.visible = leftHand.visible = false;
        var data = villager.getVillagerData();
        root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(clothes(TYPES, "type", BuiltInRegistries.VILLAGER_TYPE.getKey(data.getType())))), light, overlay);
        if (data.getProfession() != VillagerProfession.NONE) {
            var profession = clothes(PROFESSIONS, "profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(data.getProfession()));
            root.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(profession)), light, overlay);
        }
        rightHand.visible = leftHand.visible = true;
        // As a player's third-person hand holds it (ItemInHandLayer), from an arm of a player's shape.
        rightArm.translateAndRotate(pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        pose.translate(1 / 16.0F, 0.125F, -0.625F);
        items.renderItem(villager, villager.getMainHandItem(), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
        pose.popPose();
    }

    /** effects: the clothes texture for {@code key} in {@code folder}, as the profession layer names it. */
    private static ResourceLocation clothes(Map<ResourceLocation, ResourceLocation> cache, String folder, ResourceLocation key) {
        return cache.computeIfAbsent(key, k -> k.withPath(p -> "textures/entity/villager/" + folder + "/" + p + ".png"));
    }

    static void set(ModelPart part, Fit.Pose p) {
        part.x = (float) p.pivot().x();
        part.y = (float) p.pivot().y();
        part.z = (float) p.pivot().z();
        part.xRot = (float) p.xRot();
        part.yRot = (float) p.yRot();
        part.zRot = (float) p.zRot();
    }
}
