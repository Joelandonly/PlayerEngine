package com.player2.playerengine.tasks.farming;

import java.util.Objects;

public final class FarmPlantingReceiptClassifier {
   private FarmPlantingReceiptClassifier() {
   }

   public static FarmPlantingReceiptClassifier.Disposition classify(
      int issuedAttempts,
      boolean baselineCaptured,
      boolean targetMatchesExpectedBefore,
      boolean supportMatchesExpectedFarmland,
      String expectedCanonicalCropId,
      String observedCanonicalCropId,
      int requestedItemsBefore,
      int requestedItemsAfter
   ) {
      return classify(
         issuedAttempts,
         baselineCaptured,
         targetMatchesExpectedBefore,
         supportMatchesExpectedFarmland,
         expectedCanonicalCropId,
         observedCanonicalCropId,
         0,
         requestedItemsBefore,
         requestedItemsAfter
      );
   }

   public static FarmPlantingReceiptClassifier.Disposition classify(
      int issuedAttempts,
      boolean baselineCaptured,
      boolean targetMatchesExpectedBefore,
      boolean supportMatchesExpectedFarmland,
      String expectedCanonicalCropId,
      String observedCanonicalCropId,
      int confirmedSurvivalConsumptions,
      int requestedItemsBefore,
      int requestedItemsAfter
   ) {
      if (issuedAttempts <= 0) {
         return FarmPlantingReceiptClassifier.Disposition.PENDING;
      } else if (!baselineCaptured
         || requestedItemsBefore < 1
         || requestedItemsAfter < 0
         || confirmedSurvivalConsumptions < 0
         || expectedCanonicalCropId == null
         || expectedCanonicalCropId.isBlank()) {
         return FarmPlantingReceiptClassifier.Disposition.INVALID;
      } else if (!supportMatchesExpectedFarmland) {
         return FarmPlantingReceiptClassifier.Disposition.INVALID;
      } else {
         boolean expectedFamilyObserved = Objects.equals(expectedCanonicalCropId, observedCanonicalCropId);
         long rawConsumed = (long)requestedItemsBefore - requestedItemsAfter;
         long plantingConsumed = rawConsumed - confirmedSurvivalConsumptions;
         if (plantingConsumed < 0L) {
            return FarmPlantingReceiptClassifier.Disposition.INVALID;
         } else if (expectedFamilyObserved && !targetMatchesExpectedBefore && plantingConsumed == 1L) {
            return FarmPlantingReceiptClassifier.Disposition.SUCCESS;
         } else {
            return targetMatchesExpectedBefore && observedCanonicalCropId == null && plantingConsumed == 0L
               ? FarmPlantingReceiptClassifier.Disposition.PENDING
               : FarmPlantingReceiptClassifier.Disposition.INVALID;
         }
      }
   }

   public enum Disposition {
      PENDING,
      SUCCESS,
      INVALID;
   }
}
