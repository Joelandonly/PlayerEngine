package com.player2.playerengine.agentic.elliegps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;

public record FarmWaypointObservation(
   String dimension, BlockPos center, int radius, int farmlandCount, int openSlots, List<FarmCropCount> crops, long observedGameTime
) {
   public FarmWaypointObservation {
      dimension = FarmCropCount.requireCanonicalId(dimension, "dimension");
      center = Objects.requireNonNull(center, "center").immutable();
      if (radius < 0 || radius > 4) {
         throw new IllegalArgumentException("radius must be between 0 and 4");
      }

      if (farmlandCount < 0 || farmlandCount > 80) {
         throw new IllegalArgumentException("farmlandCount must be between 0 and 80");
      }

      if (openSlots < 0 || openSlots > 80) {
         throw new IllegalArgumentException("openSlots must be between 0 and 80");
      }

      if (openSlots > farmlandCount) {
         throw new IllegalArgumentException("openSlots cannot exceed observed farmland");
      }

      if (observedGameTime < 0L) {
         throw new IllegalArgumentException("observedGameTime must be non-negative");
      }

      ArrayList<FarmCropCount> sorted = new ArrayList<>(Objects.requireNonNull(crops, "crops"));
      sorted.sort(Comparator.comparing(FarmCropCount::blockId).thenComparing(c -> c.plantingItemId() == null ? "" : c.plantingItemId()));
      int total = 0;

      for (FarmCropCount crop : sorted) {
         total = Math.addExact(total, Objects.requireNonNull(crop, "crop").count());
      }

      if (total > 80) {
         throw new IllegalArgumentException("aggregate crop count cannot exceed 80");
      }

      if (openSlots + total > 80) {
         throw new IllegalArgumentException("open slots plus recognized crops cannot exceed 80");
      }

      crops = List.copyOf(sorted);
   }
}
