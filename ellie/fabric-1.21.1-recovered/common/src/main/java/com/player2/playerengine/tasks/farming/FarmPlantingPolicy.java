package com.player2.playerengine.tasks.farming;

import java.util.Objects;
import java.util.Optional;

public record FarmPlantingPolicy(FarmPlantingPolicy.Kind kind, String plantingItemId) {
   public FarmPlantingPolicy(FarmPlantingPolicy.Kind kind, String plantingItemId) {
      kind = Objects.requireNonNull(kind, "kind");
      if (kind == FarmPlantingPolicy.Kind.SET) {
         plantingItemId = FarmPlantingRequest.canonicalId(plantingItemId);
      } else if (plantingItemId != null) {
         throw new IllegalArgumentException("only SET may carry a planting item id");
      }

      this.kind = kind;
      this.plantingItemId = plantingItemId;
   }

   public static FarmPlantingPolicy preserve() {
      return new FarmPlantingPolicy(FarmPlantingPolicy.Kind.PRESERVE, null);
   }

   public static FarmPlantingPolicy clear() {
      return new FarmPlantingPolicy(FarmPlantingPolicy.Kind.CLEAR, null);
   }

   public static FarmPlantingPolicy set(String plantingItemId) {
      return new FarmPlantingPolicy(FarmPlantingPolicy.Kind.SET, plantingItemId);
   }

   public Optional<String> restrictedItemId() {
      return Optional.ofNullable(this.plantingItemId);
   }

   public static FarmPlantingPolicy parse(String raw) {
      String value = raw != null && !raw.isBlank() ? raw.strip() : "preserve";
      if ("preserve".equals(value)) {
         return preserve();
      } else if ("mixed".equals(value)) {
         return clear();
      } else {
         String prefix = "single:";
         if (value.startsWith(prefix) && value.length() > prefix.length()) {
            return set(value.substring(prefix.length()));
         } else {
            throw new IllegalArgumentException("farm_policy must be preserve, mixed, or single:<item-id>");
         }
      }
   }

   public enum Kind {
      PRESERVE,
      CLEAR,
      SET;
   }
}
