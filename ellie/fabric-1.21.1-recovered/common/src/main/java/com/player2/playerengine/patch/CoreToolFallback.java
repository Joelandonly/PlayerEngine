package com.player2.playerengine.patch;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CoreToolFallback {
   private CoreToolFallback() {
   }

   public static Set<String> alwaysInclude() {
      return Collections.unmodifiableSet(new LinkedHashSet<>(List.of("idle", "stop", "bodylang", "get", "build_structure")));
   }
}
