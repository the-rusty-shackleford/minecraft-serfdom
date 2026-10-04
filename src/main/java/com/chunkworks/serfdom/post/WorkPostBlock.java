/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.post;

import com.chunkworks.serfdom.Screens;
import com.chunkworks.serfdom.Workers;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Work Post (D-0001): a fence post with a board. It belongs to whoever placed it; its owner
 * opens its screen with a right-click; breaking it sends its workers back to a villager's life at
 * the base. */
public final class WorkPostBlock extends BaseEntityBlock {
    public static final MapCodec<WorkPostBlock> CODEC = simpleCodec(WorkPostBlock::new);
    private static final VoxelShape POST = Block.box(6, 0, 6, 10, 16, 10);
    /** The post with its board, by the way the board faces: the model's north, turned. */
    private static final java.util.Map<net.minecraft.core.Direction, VoxelShape> SHAPES = java.util.Map.of(
            net.minecraft.core.Direction.NORTH, net.minecraft.world.phys.shapes.Shapes.or(POST, Block.box(1, 6, 5, 15, 15, 6)),
            net.minecraft.core.Direction.SOUTH, net.minecraft.world.phys.shapes.Shapes.or(POST, Block.box(1, 6, 10, 15, 15, 11)),
            net.minecraft.core.Direction.EAST, net.minecraft.world.phys.shapes.Shapes.or(POST, Block.box(10, 6, 1, 11, 15, 15)),
            net.minecraft.core.Direction.WEST, net.minecraft.world.phys.shapes.Shapes.or(POST, Block.box(5, 6, 1, 6, 15, 15)));

    public WorkPostBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(HorizontalDirectionalBlock.FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, ctx.getHorizontalDirection().getOpposite());
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(HorizontalDirectionalBlock.FACING));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new WorkPostBlockEntity(pos, state); }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof WorkPostBlockEntity post)
            post.claim(player.getUUID(), player.getGameProfile().getName());
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof WorkPostBlockEntity post)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            if (post.ownedBy(sp.getUUID())) Screens.openPost(sp, post);
            else sp.displayClientMessage(Component.translatable("message.serfdom.someones_post", post.ownerName()).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof WorkPostBlockEntity post) {
            for (var id : post.workers())
                if (server.getEntity(id) instanceof Villager worker && Workers.of(worker).post().map(p -> p.pos().equals(pos)).orElse(false))
                    Workers.clearJob(server, worker);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
