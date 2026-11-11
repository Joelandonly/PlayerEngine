package com.player2.playerengine.player2api;

import java.util.Optional;

public record RoutingDecision(boolean isOnDevice, Optional<String> profileBaseUrlOverride) {
   public static RoutingDecision onDevice() {
      return new RoutingDecision(true, Optional.empty());
   }

   public static RoutingDecision defaultProfile() {
      return new RoutingDecision(false, Optional.empty());
   }

   public static RoutingDecision namedProfile(String baseUrl) {
      return new RoutingDecision(false, Optional.of(baseUrl));
   }
}
