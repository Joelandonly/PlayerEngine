package com.player2.playerengine.tasks.farming;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;

public record FarmBreakToolRequirement(
   FarmBreakToolRequirement.Kind kind, FarmBreakToolRequirement.Family family, FarmBreakToolRequirement.StandardTier minimumTier, BlockState expectedState
) {
   public FarmBreakToolRequirement(
      FarmBreakToolRequirement.Kind kind, FarmBreakToolRequirement.Family family, FarmBreakToolRequirement.StandardTier minimumTier, BlockState expectedState
   ) {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(expectedState, "expectedState");
      if (kind == FarmBreakToolRequirement.Kind.STANDARD) {
         Objects.requireNonNull(family, "family");
         Objects.requireNonNull(minimumTier, "minimumTier");
      } else if (family != null || minimumTier != null) {
         throw new IllegalArgumentException("only a standard repair-tool requirement has a family and tier");
      }

      this.kind = kind;
      this.family = family;
      this.minimumTier = minimumTier;
      this.expectedState = expectedState;
   }

   public static FarmBreakToolRequirement resolve(BlockState state) {
      BlockState expected = Objects.requireNonNull(state, "state");
      if (!expected.requiresCorrectToolForDrops()) {
         return none(expected);
      }

      for (FarmBreakToolRequirement.Family candidateFamily : FarmBreakToolRequirement.Family.values()) {
         if (expected.is(tagFor(candidateFamily))) {
            FarmBreakToolRequirement tagged = new FarmBreakToolRequirement(
               FarmBreakToolRequirement.Kind.STANDARD, candidateFamily, minimumTaggedTier(expected), expected
            );

            for (Item item : standardItemsAtOrAbove(candidateFamily, tagged.minimumTier(), 1)) {
               if (tagged.accepts(item.getDefaultInstance())) {
                  return tagged;
               }
            }

            return custom(expected);
         }
      }

      for (FarmBreakToolRequirement.Family candidateFamily : new ArrayList<>(List.of(FarmBreakToolRequirement.Family.values()))) {
         for (FarmBreakToolRequirement.StandardTier candidateTier : FarmBreakToolRequirement.StandardTier.values()) {
            ItemStack candidate = new ItemStack(itemFor(candidateFamily, candidateTier));
            if (effectiveAndCorrect(candidate, expected)) {
               return new FarmBreakToolRequirement(FarmBreakToolRequirement.Kind.STANDARD, candidateFamily, candidateTier, expected);
            }
         }
      }

      return custom(expected);
   }

   private static FarmBreakToolRequirement.StandardTier minimumTaggedTier(BlockState state) {
      if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
         return FarmBreakToolRequirement.StandardTier.DIAMOND;
      } else if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
         return FarmBreakToolRequirement.StandardTier.IRON;
      } else {
         return state.is(BlockTags.NEEDS_STONE_TOOL) ? FarmBreakToolRequirement.StandardTier.STONE : FarmBreakToolRequirement.StandardTier.WOOD;
      }
   }

   public static FarmBreakToolRequirement none(BlockState state) {
      return new FarmBreakToolRequirement(FarmBreakToolRequirement.Kind.NONE, null, null, Objects.requireNonNull(state, "state"));
   }

   public static FarmBreakToolRequirement custom(BlockState state) {
      return new FarmBreakToolRequirement(FarmBreakToolRequirement.Kind.CUSTOM, null, null, Objects.requireNonNull(state, "state"));
   }

   public boolean requiresTool() {
      return this.kind != FarmBreakToolRequirement.Kind.NONE;
   }

   public boolean isStandard() {
      return this.kind == FarmBreakToolRequirement.Kind.STANDARD;
   }

   public boolean isCustom() {
      return this.kind == FarmBreakToolRequirement.Kind.CUSTOM;
   }

   public boolean accepts(ItemStack stack) {
      return !this.requiresTool() ? true : stack != null && !stack.isEmpty() && remainingUses(stack) > 0 && effectiveAndCorrect(stack, this.expectedState);
   }

   static boolean effectiveAndCorrect(ItemStack stack, BlockState state) {
      return stack != null && !stack.isEmpty() && stack.getDestroySpeed(state) > 1.0F && (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state));
   }

   public static int remainingUses(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         int maximum = stack.getMaxDamage();
         return maximum <= 0 ? Integer.MAX_VALUE : Math.max(0, maximum - stack.getDamageValue());
      } else {
         return 0;
      }
   }

   static List<Item> standardItemsAtOrAbove(FarmBreakToolRequirement.Family family, FarmBreakToolRequirement.StandardTier minimumTier, int requiredUses) {
      Objects.requireNonNull(family, "family");
      Objects.requireNonNull(minimumTier, "minimumTier");
      if (requiredUses < 1) {
         throw new IllegalArgumentException("requiredUses must be positive");
      }

      ArrayList<Item> result = new ArrayList<>();

      for (FarmBreakToolRequirement.StandardTier tier : FarmBreakToolRequirement.StandardTier.values()) {
         if (tier.ordinal() >= minimumTier.ordinal()) {
            Item item = itemFor(family, tier);
            if (remainingUses(item.getDefaultInstance()) >= requiredUses) {
               result.add(item);
            }
         }
      }

      Item netherite = netheriteItemFor(family);
      if (remainingUses(netherite.getDefaultInstance()) >= requiredUses) {
         result.add(netherite);
      }

      return List.copyOf(result);
   }

   static Item fallbackItem(FarmBreakToolRequirement.Family family, FarmBreakToolRequirement.StandardTier minimumTier, int requiredUses) {
      for (FarmBreakToolRequirement.StandardTier tier : FarmBreakToolRequirement.StandardTier.values()) {
         if (tier.ordinal() >= minimumTier.ordinal()) {
            Item item = itemFor(family, tier);
            if (remainingUses(item.getDefaultInstance()) >= requiredUses) {
               return item;
            }
         }
      }

      return null;
   }

   static Item itemFor(FarmBreakToolRequirement.Family family, FarmBreakToolRequirement.StandardTier tier) {
      return switch ((FarmBreakToolRequirement.Family)Objects.requireNonNull(family, "family")) {
         case PICKAXE -> {
            switch ((FarmBreakToolRequirement.StandardTier)Objects.requireNonNull(tier, "tier")) {
               case WOOD:
                  yield Items.WOODEN_PICKAXE;
               case STONE:
                  yield Items.STONE_PICKAXE;
               case IRON:
                  yield Items.IRON_PICKAXE;
               case DIAMOND:
                  yield Items.DIAMOND_PICKAXE;
               default:
                  throw new MatchException(null, null);
            }
         }
         case AXE -> {
            switch ((FarmBreakToolRequirement.StandardTier)Objects.requireNonNull(tier, "tier")) {
               case WOOD:
                  yield Items.WOODEN_AXE;
               case STONE:
                  yield Items.STONE_AXE;
               case IRON:
                  yield Items.IRON_AXE;
               case DIAMOND:
                  yield Items.DIAMOND_AXE;
               default:
                  throw new MatchException(null, null);
            }
         }
         case SHOVEL -> {
            switch ((FarmBreakToolRequirement.StandardTier)Objects.requireNonNull(tier, "tier")) {
               case WOOD:
                  yield Items.WOODEN_SHOVEL;
               case STONE:
                  yield Items.STONE_SHOVEL;
               case IRON:
                  yield Items.IRON_SHOVEL;
               case DIAMOND:
                  yield Items.DIAMOND_SHOVEL;
               default:
                  throw new MatchException(null, null);
            }
         }
         case HOE -> {
            switch ((FarmBreakToolRequirement.StandardTier)Objects.requireNonNull(tier, "tier")) {
               case WOOD:
                  yield Items.WOODEN_HOE;
               case STONE:
                  yield Items.STONE_HOE;
               case IRON:
                  yield Items.IRON_HOE;
               case DIAMOND:
                  yield Items.DIAMOND_HOE;
               default:
                  throw new MatchException(null, null);
            }
         }
      };
   }

   private static Item netheriteItemFor(FarmBreakToolRequirement.Family family) {
      return switch (family) {
         case PICKAXE -> Items.NETHERITE_PICKAXE;
         case AXE -> Items.NETHERITE_AXE;
         case SHOVEL -> Items.NETHERITE_SHOVEL;
         case HOE -> Items.NETHERITE_HOE;
      };
   }

   private static TagKey<Block> tagFor(FarmBreakToolRequirement.Family family) {
      return switch (family) {
         case PICKAXE -> BlockTags.MINEABLE_WITH_PICKAXE;
         case AXE -> BlockTags.MINEABLE_WITH_AXE;
         case SHOVEL -> BlockTags.MINEABLE_WITH_SHOVEL;
         case HOE -> BlockTags.MINEABLE_WITH_HOE;
      };
   }

   public enum Family {
      PICKAXE,
      AXE,
      SHOVEL,
      HOE;
   }

   public enum Kind {
      NONE,
      STANDARD,
      CUSTOM;
   }

   public enum StandardTier {
      WOOD,
      STONE,
      IRON,
      DIAMOND;
   }
}
