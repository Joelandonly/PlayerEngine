package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public final class FarmPlantingItemResolver {
   private FarmPlantingItemResolver() {
   }

   public static FarmPlantingItemResolver.Result resolve(Item requestedItem) {
      if (requestedItem != null && requestedItem != Items.AIR) {
         ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(requestedItem);
         ResourceLocation airItemId = BuiltInRegistries.ITEM.getKey(Items.AIR);
         if (itemId == null || itemId.equals(airItemId)) {
            return rejected(FarmPlantingItemResolver.Status.INVALID_ITEM);
         }

         if (requestedItem instanceof BlockItem blockItem) {
            Block placementBlock = blockItem.getBlock();
            if (placementBlock == null || placementBlock == Blocks.AIR) {
               return rejected(FarmPlantingItemResolver.Status.UNSUPPORTED_FAMILY);
            }

            if (!(placementBlock instanceof StemBlock) && !(placementBlock instanceof AttachedStemBlock)) {
               BlockState placementState = placementBlock.defaultBlockState();
               HarvestBehavior behavior;
               if (placementBlock == Blocks.TORCHFLOWER_CROP) {
                  if (requestedItem != Items.TORCHFLOWER_SEEDS) {
                     return rejected(FarmPlantingItemResolver.Status.UNSUPPORTED_CROP_CONTRACT);
                  }

                  behavior = HarvestBehaviorRegistry.DEFAULT.resolve(placementState).orElse(null);
               } else if (placementBlock == Blocks.PITCHER_CROP) {
                  if (requestedItem != Items.PITCHER_POD) {
                     return rejected(FarmPlantingItemResolver.Status.UNSUPPORTED_CROP_CONTRACT);
                  }

                  behavior = HarvestBehaviorRegistry.DEFAULT.resolve(placementState).orElse(null);
               } else {
                  if (!(placementBlock instanceof CropBlock)) {
                     return rejected(FarmPlantingItemResolver.Status.UNSUPPORTED_FAMILY);
                  }

                  behavior = HarvestBehaviorRegistry.DEFAULT.resolve(placementState).orElse(null);
               }

               if (behavior == null) {
                  return rejected(FarmPlantingItemResolver.Status.UNSUPPORTED_CROP_CONTRACT);
               }

               ResourceLocation canonicalId = behavior.canonicalCropId(placementState).orElse(null);
               return canonicalId == null
                  ? rejected(FarmPlantingItemResolver.Status.UNSUPPORTED_CROP_CONTRACT)
                  : new FarmPlantingItemResolver.Result(
                     FarmPlantingItemResolver.Status.SUPPORTED,
                     new FarmPlantingItemResolver.Descriptor(requestedItem, itemId, placementBlock, canonicalId, behavior)
                  );
            } else {
               return rejected(FarmPlantingItemResolver.Status.EXCLUDED_STEM);
            }
         } else {
            return rejected(FarmPlantingItemResolver.Status.NOT_BLOCK_ITEM);
         }
      } else {
         return rejected(FarmPlantingItemResolver.Status.INVALID_ITEM);
      }
   }

   private static FarmPlantingItemResolver.Result rejected(FarmPlantingItemResolver.Status status) {
      return new FarmPlantingItemResolver.Result(status, null);
   }

   public record Descriptor(Item item, ResourceLocation plantingItemId, Block placementBlock, ResourceLocation canonicalCropId, HarvestBehavior behavior) {
      public Descriptor {
         Objects.requireNonNull(item, "item");
         Objects.requireNonNull(plantingItemId, "plantingItemId");
         Objects.requireNonNull(placementBlock, "placementBlock");
         Objects.requireNonNull(canonicalCropId, "canonicalCropId");
         Objects.requireNonNull(behavior, "behavior");
      }
   }

   public record Result(FarmPlantingItemResolver.Status status, FarmPlantingItemResolver.Descriptor descriptor) {
      public Result {
         Objects.requireNonNull(status, "status");
         if (status == FarmPlantingItemResolver.Status.SUPPORTED != (descriptor != null)) {
            throw new IllegalArgumentException("supported status and descriptor disagree");
         }
      }

      public boolean supported() {
         return this.status == FarmPlantingItemResolver.Status.SUPPORTED;
      }
   }

   public enum Status {
      SUPPORTED,
      INVALID_ITEM,
      NOT_BLOCK_ITEM,
      EXCLUDED_STEM,
      UNSUPPORTED_FAMILY,
      UNSUPPORTED_CROP_CONTRACT;
   }
}
