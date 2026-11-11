package com.player2.playerengine.player2api;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class OwnerStopTargetResolution {
   private OwnerStopTargetResolution() {
   }

   public static OwnerStopTargetResolution.Resolution resolve(Collection<String> characterIds) {
      Set<String> distinct = new LinkedHashSet<>();
      if (characterIds != null) {
         for (String id : characterIds) {
            if (id != null && !id.isBlank()) {
               distinct.add(id);
            }
         }
      }

      if (distinct.isEmpty()) {
         return new OwnerStopTargetResolution.Resolution(OwnerStopTargetResolution.Kind.NONE, null);
      } else {
         return distinct.size() > 1
            ? new OwnerStopTargetResolution.Resolution(OwnerStopTargetResolution.Kind.AMBIGUOUS, null)
            : new OwnerStopTargetResolution.Resolution(OwnerStopTargetResolution.Kind.UNIQUE, distinct.iterator().next());
      }
   }

   public enum Kind {
      NONE,
      UNIQUE,
      AMBIGUOUS;
   }

   public record Resolution(OwnerStopTargetResolution.Kind kind, String characterId) {
   }
}
