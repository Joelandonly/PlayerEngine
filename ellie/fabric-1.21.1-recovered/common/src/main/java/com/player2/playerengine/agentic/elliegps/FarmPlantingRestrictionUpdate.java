package com.player2.playerengine.agentic.elliegps;

import java.util.Objects;

public record FarmPlantingRestrictionUpdate(FarmPlantingRestrictionUpdate.Kind kind, String plantingItemId) {
   private static final FarmPlantingRestrictionUpdate PRESERVE = new FarmPlantingRestrictionUpdate(FarmPlantingRestrictionUpdate.Kind.PRESERVE, null);
   private static final FarmPlantingRestrictionUpdate CLEAR = new FarmPlantingRestrictionUpdate(FarmPlantingRestrictionUpdate.Kind.CLEAR, null);

   public FarmPlantingRestrictionUpdate(FarmPlantingRestrictionUpdate.Kind kind, String plantingItemId) {
      kind = Objects.requireNonNull(kind, "kind");
      if (kind == FarmPlantingRestrictionUpdate.Kind.SET) {
         plantingItemId = FarmCropCount.requireCanonicalId(plantingItemId, "plantingRestrictionItemId");
      } else if (plantingItemId != null) {
         throw new IllegalArgumentException("only a SET restriction update may carry a planting item id");
      }

      this.kind = kind;
      this.plantingItemId = plantingItemId;
   }

   public static FarmPlantingRestrictionUpdate preserve() {
      return PRESERVE;
   }

   public static FarmPlantingRestrictionUpdate clear() {
      return CLEAR;
   }

   public static FarmPlantingRestrictionUpdate set(String canonicalPlantingItemId) {
      return new FarmPlantingRestrictionUpdate(FarmPlantingRestrictionUpdate.Kind.SET, canonicalPlantingItemId);
   }

   String apply(String existingRestriction) {
      return switch (this.kind) {
         case PRESERVE -> existingRestriction;
         case CLEAR -> null;
         case SET -> this.plantingItemId;
      };
   }

   public enum Kind {
      PRESERVE,
      CLEAR,
      SET;
   }
}
