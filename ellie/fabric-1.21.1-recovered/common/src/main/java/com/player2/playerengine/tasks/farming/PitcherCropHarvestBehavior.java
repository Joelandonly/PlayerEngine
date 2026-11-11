package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.PitcherCropBlock;

public final class PitcherCropHarvestBehavior implements HarvestBehavior {
   private static final String BLOCK_ID = "minecraft:pitcher_crop";

   @Override
   public boolean recognizes(BlockState state) {
      Objects.requireNonNull(state, "state");
      return state.is(Blocks.PITCHER_CROP) && state.hasProperty(PitcherCropBlock.AGE) && state.hasProperty(DoublePlantBlock.HALF);
   }

   @Override
   public boolean isMature(BlockState state) {
      Objects.requireNonNull(state, "state");
      return this.recognizes(state) && (Integer)state.getValue(PitcherCropBlock.AGE) >= 4;
   }

   @Override
   public HarvestBehavior.HarvestAction action() {
      return HarvestBehavior.HarvestAction.BREAK;
   }

   @Override
   public Optional<ResourceLocation> canonicalCropId(BlockState state) {
      Objects.requireNonNull(state, "state");
      return this.recognizes(state) ? Optional.of(BuiltInRegistries.BLOCK.getKey(Blocks.PITCHER_CROP)) : Optional.empty();
   }

   @Override
   public Optional<ResourceLocation> plantingItemId(LevelReader level, BlockPos pos, BlockState state) {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(pos, "pos");
      Objects.requireNonNull(state, "state");
      return this.recognizes(state) ? Optional.of(BuiltInRegistries.ITEM.getKey(Items.PITCHER_POD)) : Optional.empty();
   }

   @Override
   public boolean allowsLowCollisionStance(BlockState state) {
      return this.recognizes(state) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
   }

   static boolean isLowerFingerprint(String fingerprint) {
      return hasHalfFingerprint(fingerprint, "lower");
   }

   static boolean isUpperFingerprint(String fingerprint) {
      return hasHalfFingerprint(fingerprint, "upper");
   }

   static OptionalInt ageFromFingerprint(String fingerprint) {
      if (fingerprint != null && fingerprint.startsWith("minecraft:pitcher_crop|")) {
         int open = fingerprint.indexOf(91);
         int close = fingerprint.lastIndexOf(93);
         if (open >= 0 && close > open) {
            String[] properties = fingerprint.substring(open + 1, close).split(",");

            for (String property : properties) {
               String stripped = property.strip();
               if (stripped.startsWith("age=")) {
                  try {
                     return OptionalInt.of(Integer.parseInt(stripped.substring("age=".length())));
                  } catch (NumberFormatException invalidAge) {
                     return OptionalInt.empty();
                  }
               }
            }

            return OptionalInt.empty();
         } else {
            return OptionalInt.empty();
         }
      } else {
         return OptionalInt.empty();
      }
   }

   private static boolean hasHalfFingerprint(String fingerprint, String expectedHalf) {
      if (fingerprint != null && fingerprint.startsWith("minecraft:pitcher_crop|")) {
         int open = fingerprint.indexOf(91);
         int close = fingerprint.lastIndexOf(93);
         if (open >= 0 && close > open) {
            String[] properties = fingerprint.substring(open + 1, close).split(",");

            for (String property : properties) {
               if (("half=" + expectedHalf).equals(property.strip())) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
