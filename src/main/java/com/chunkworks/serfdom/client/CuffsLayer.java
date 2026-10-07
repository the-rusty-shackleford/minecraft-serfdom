/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.client;

import com.chunkworks.serfdom.Workers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/** The cuffs (D-0003): a villager in chains wears a short length of chain across the front of its
 * crossed forearms, so anyone can see it is cuffed whether or not someone holds the chain. Vanilla's
 * chain model, laid on its side as the chain block's blockstate lays it, with a texture of ours in
 * an iron ingot's greys ({@code models/block/cuff_chain.json}; D-0011). With its arms out of the
 * fold, gripping a tool (D-0011), each wrist wears a manacle instead: a band in an iron ingot's
 * greys, a little wider than the sleeve, where the sleeve meets the hand (a chain that small reads as
 * a line). */
public final class CuffsLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    public static final net.minecraft.client.model.geom.ModelLayerLocation MANACLES =
            new net.minecraft.client.model.geom.ModelLayerLocation(com.chunkworks.serfdom.Serfdom.id("manacles"), "main");
    private static final net.minecraft.resources.ResourceLocation IRON = com.chunkworks.serfdom.Serfdom.id("textures/entity/manacles.png");
    /** The chain across folded forearms, loaded by {@link ClientSetup} beside the game's models. */
    public static final ModelResourceLocation CHAIN = ModelResourceLocation.standalone(com.chunkworks.serfdom.Serfdom.id("block/cuff_chain"));
    private final net.minecraft.client.model.geom.ModelPart manacles, right, left;
    /** The chain's length across the forearms, as a share of a block: eight of the model's pixels. */
    private static final float SCALE = 0.5F;
    /** Where the chain lies, in the arms' own frame (pixels): the middle of the forearms' front face. */
    private static final float Y = 4.0F, Z = -2.4F;

    public CuffsLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent, net.minecraft.client.renderer.entity.EntityRendererProvider.Context context) {
        super(parent);
        manacles = context.bakeLayer(MANACLES);
        right = manacles.getChild("right");
        left = manacles.getChild("left");
    }

    /** effects: a band round each wrist of a villager's arms out of the fold ({@link ArmsLayer}'s arms,
     * which pivot at the shoulders): 4.8 by 1.6 by 4.8 over the arm's 4 by 4, across the sleeve's end.
     * The texture is ours, 32 by 32 to match ({@code manacles} in {@code devtools/art/art.py}). */
    public static net.minecraft.client.model.geom.builders.LayerDefinition create() {
        var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("right", net.minecraft.client.model.geom.builders.CubeListBuilder.create().addBox(-3.4F, 5.2F, -2.4F, 4.8F, 1.6F, 4.8F),
                net.minecraft.client.model.geom.PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left", net.minecraft.client.model.geom.builders.CubeListBuilder.create().addBox(-1.4F, 5.2F, -2.4F, 4.8F, 1.6F, 4.8F),
                net.minecraft.client.model.geom.PartPose.offset(5, 2, 0));
        return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 32, 32);
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Villager villager, float limbSwing, float limbSwingAmount,
                                 float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!Workers.cuffed(villager) || villager.isInvisible()) return;
        var out = ArmsLayer.arms(villager, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch).orElse(null);
        if (out != null) {
            ArmsLayer.set(right, out.right());
            ArmsLayer.set(left, out.left());
            pose.pushPose();
            getParentModel().root().getChild("body").translateAndRotate(pose);
            manacles.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(IRON)), light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            return;
        }
        pose.pushPose();
        getParentModel().root().getChild("arms").translateAndRotate(pose);
        pose.translate(0.0F, Y / 16.0F, Z / 16.0F);
        pose.scale(SCALE, SCALE, SCALE);
        // Along x, as the blockstate turns the chain for axis=x (x 90, y 90: BlockModelRotation's
        // rotateYXZ(-y, -x, 0)), about the block's middle.
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.translate(-0.5F, -0.5F, -0.5F);
        var mc = Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()), null,
                mc.getModelManager().getModel(CHAIN), 1.0F, 1.0F, 1.0F, light, OverlayTexture.NO_OVERLAY,
                net.neoforged.neoforge.client.model.data.ModelData.EMPTY, null);
        pose.popPose();
    }
}
