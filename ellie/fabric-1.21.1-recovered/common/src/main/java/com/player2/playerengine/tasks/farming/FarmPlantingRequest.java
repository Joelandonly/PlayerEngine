package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record FarmPlantingRequest(String plantingItemId, int count) {
   public FarmPlantingRequest(String plantingItemId, int count) {
      plantingItemId = canonicalId(plantingItemId);
      if (count >= 1 && count <= 80) {
         this.plantingItemId = plantingItemId;
         this.count = count;
      } else {
         throw new IllegalArgumentException("planting count must be between 1 and 80");
      }
   }

   static String canonicalId(String raw) {
      String value = Objects.requireNonNull(raw, "plantingItemId").strip();
      String candidate = value.indexOf(58) > 0 ? value : "minecraft:" + value;
      ResourceLocation parsed = ResourceLocation.tryParse(candidate);
      if (!value.isEmpty() && parsed != null && !parsed.getPath().isEmpty() && parsed.toString().equals(candidate)) {
         return candidate;
      } else {
         throw new IllegalArgumentException("planting item id must be canonical; bare ids use the minecraft namespace");
      }
   }
}
