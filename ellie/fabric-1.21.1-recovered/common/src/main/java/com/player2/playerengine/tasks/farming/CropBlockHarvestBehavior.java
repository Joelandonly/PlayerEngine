package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.registries.BuiltInRegistries;

public final class CropBlockHarvestBehavior implements HarvestBehavior {
   @Override
   public boolean recognizes(BlockState state) {
      Objects.requireNonNull(state, "state");
      if (state.getBlock() instanceof CropBlock crop) {
         try {
            return crop.getStateForAge(crop.getMaxAge()).getBlock() instanceof CropBlock;
         } catch (RuntimeException invalidCropContract) {
            return false;
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean isMature(BlockState state) {
      Objects.requireNonNull(state, "state");
      return this.recognizes(state) && state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
   }

   @Override
   public HarvestBehavior.HarvestAction action() {
      return HarvestBehavior.HarvestAction.BREAK;
   }

   @Override
   public Optional<ResourceLocation> canonicalCropId(BlockState state) {
      Objects.requireNonNull(state, "state");
      if (!this.recognizes(state)) {
         return Optional.empty();
      }

      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
      ResourceLocation airId = BuiltInRegistries.BLOCK.getKey(Blocks.AIR);
      return id != null && !id.equals(airId) ? Optional.of(id) : Optional.empty();
   }

   @Override
   public Optional<ResourceLocation> plantingItemId(LevelReader level, BlockPos pos, BlockState state) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(state, "state");
      return this.recognizes(state) && state.getBlock() instanceof CropBlock crop
         ? plantingItemIdFromClone(level, pos, state, crop::getCloneItemStack)
         : Optional.empty();
   }

   static Optional<ResourceLocation> plantingItemIdFromClone(
      LevelReader level, BlockPos pos, BlockState state, CropBlockHarvestBehavior.CloneStackSource cloneSource
   ) {
      return plantingItemIdFromClone(level, pos, state, cloneSource, BuiltInRegistries.ITEM::getKey);
   }

   static Optional<ResourceLocation> plantingItemIdFromClone(
      LevelReader level,
      BlockPos pos,
      BlockState state,
      CropBlockHarvestBehavior.CloneStackSource cloneSource,
      CropBlockHarvestBehavior.ItemIdSource itemIdSource
   ) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(state, "state");
      Objects.requireNonNull(cloneSource, "cloneSource");
      Objects.requireNonNull(itemIdSource, "itemIdSource");

      try {
         ItemStack clone = cloneSource.resolve(level, pos, state);
         if (clone != null && !clone.isEmpty() && !clone.is(Items.AIR)) {
            ResourceLocation id = itemIdSource.key(clone.getItem());
            ResourceLocation airId = BuiltInRegistries.ITEM.getKey(Items.AIR);
            return id != null && !id.equals(airId) ? Optional.of(id) : Optional.empty();
         } else {
            return Optional.empty();
         }
      } catch (RuntimeException ignored) {
         return Optional.empty();
      }
   }

   @FunctionalInterface
   interface CloneStackSource {
      ItemStack resolve(LevelReader var1, BlockPos var2, BlockState var3);
   }

   @FunctionalInterface
   interface ItemIdSource {
      ResourceLocation key(Item var1);
   }
}
