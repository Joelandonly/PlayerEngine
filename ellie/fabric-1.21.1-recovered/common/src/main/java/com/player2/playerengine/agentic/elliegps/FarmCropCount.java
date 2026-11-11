package com.player2.playerengine.agentic.elliegps;

import net.minecraft.resources.ResourceLocation;

public record FarmCropCount(String blockId, String plantingItemId, int count) {
   public FarmCropCount(String blockId, String plantingItemId, int count) {
      blockId = requireCanonicalId(blockId, "blockId");
      if (plantingItemId != null) {
         plantingItemId = requireCanonicalId(plantingItemId, "plantingItemId");
      }

      if (count >= 1 && count <= 80) {
         this.blockId = blockId;
         this.plantingItemId = plantingItemId;
         this.count = count;
      } else {
         throw new IllegalArgumentException("count must be between 1 and 80");
      }
   }

   static String requireCanonicalId(String value, String field) {
      if (value == null) {
         throw new IllegalArgumentException(field + " is required");
      } else {
         ResourceLocation parsed = ResourceLocation.tryParse(value);
         if (parsed != null && parsed.toString().equals(value)) {
            return value;
         } else {
            throw new IllegalArgumentException(field + " must be a canonical namespaced id");
         }
      }
   }
}
