package com.player2.playerengine.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum MaterialChain {
   WOOD(MiningRequirement.WOOD, "wooden_pickaxe"),
   STONE(MiningRequirement.STONE, "stone_pickaxe"),
   IRON(MiningRequirement.IRON, "iron_pickaxe"),
   DIAMOND(MiningRequirement.DIAMOND, "diamond_pickaxe");

   public static final List<MaterialChain> ORDERED = Collections.unmodifiableList(Arrays.asList(values()));
   private final MiningRequirement requirement;
   private final String pickaxeCatalogueName;

   MaterialChain(MiningRequirement requirement, String pickaxeCatalogueName) {
      this.requirement = requirement;
      this.pickaxeCatalogueName = pickaxeCatalogueName;
   }

   public MiningRequirement getRequirement() {
      return this.requirement;
   }

   public String getPickaxeCatalogueName() {
      return this.pickaxeCatalogueName;
   }

   public static MaterialChain forRequirement(MiningRequirement req) {
      if (req != null && req != MiningRequirement.HAND) {
         for (MaterialChain tier : values()) {
            if (tier.requirement == req) {
               return tier;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public MaterialChain prerequisite() {
      int idx = this.ordinal();
      return idx > 0 ? values()[idx - 1] : null;
   }
}
