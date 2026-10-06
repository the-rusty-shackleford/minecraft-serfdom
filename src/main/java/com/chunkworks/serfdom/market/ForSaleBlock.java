/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.serfdom.market;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The For Sale block (D-0006): a market counter whose front faces the one who placed it. It belongs
 * to whoever placed it: its owner opens its screen with a right-click; anyone else is told what it
 * sells and whose it is. Nobody but its owner can break it (Warehouse Manager's D-0006 rule), and
 * explosions leave it ({@link Stalls}). Broken, it drops its stock and its proceeds. */
public final class ForSaleBlock extends BaseEntityBlock {
    public static final MapCodec<ForSaleBlock> CODEC = simpleCodec(ForSaleBlock::new);
    /** The counter, and the sign board standing up at its back, by the way its front faces. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Shapes.or(Block.box(1, 0, 2, 15, 11, 14), Block.box(2, 11, 12, 14, 16, 13)),
            Direction.SOUTH, Shapes.or(Block.box(1, 0, 2, 15, 11, 14), Block.box(2, 11, 3, 14, 16, 4)),
            Direction.WEST, Shapes.or(Block.box(2, 0, 1, 14, 11, 15), Block.box(12, 11, 2, 13, 16, 14)),
            Direction.EAST, Shapes.or(Block.box(2, 0, 1, 14, 11, 15), Block.box(3, 11, 2, 4, 16, 14)));

    public ForSaleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
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
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ForSaleBlockEntity(pos, state); }
    /** No mob walks through it: its shape is no full block, so vanilla's default would take it for open
     * ground. */
    @Override protected boolean isPathfindable(BlockState state, net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
    /** Nor over it: a block it can't walk through, a mob plans to jump onto unless it is a fence, and the
     * stall's sign makes its top a full block high, within a villager's jump; but nobody can stand on it
     * past the sign. A villager going from one stall to the next planned straight over the stall between
     * and stuck there. */
    @Override public net.minecraft.world.level.pathfinder.PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Mob mob) {
        return net.minecraft.world.level.pathfinder.PathType.FENCE;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof ForSaleBlockEntity stall)
            stall.claim(player.getUUID(), player.getGameProfile().getName());
    }

    /** effects: nobody but the owner makes a dent in it, on either side, so a stranger sees no
     * crack. */
    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ForSaleBlockEntity stall && !stall.ownedBy(player.getUUID())) return 0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ForSaleBlockEntity stall)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            if (stall.ownedBy(sp.getUUID())) Stalls.open(sp, stall);
            else sp.displayClientMessage(Component.translatable("message.serfdom.someones_stall", stall.ownerName(), Stalls.label(stall)).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ForSaleBlockEntity stall)
            for (var s : stall.contents()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onRemove(state, level, pos, newState, moved);
    }
}
