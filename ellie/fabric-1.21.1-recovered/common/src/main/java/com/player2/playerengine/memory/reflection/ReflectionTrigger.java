package com.player2.playerengine.memory.reflection;

import com.player2.playerengine.memory.MemoryStore;

public final class ReflectionTrigger {
   public static final long DEFAULT_IMPORTANCE_THRESHOLD = 150L;
   public static final long DEFAULT_SESSION_END_MIN_IMPORTANCE = 20L;

   private ReflectionTrigger() {
   }

   public static boolean shouldReflect(MemoryStore store, long threshold) {
      if (store == null) {
         return false;
      }

      long effective = threshold > 0L ? threshold : 150L;
      return store.cumulativeImportanceSinceLastReflection() > effective;
   }

   public static boolean shouldReflect(MemoryStore store) {
      return shouldReflect(store, 150L);
   }

   public static boolean shouldReflectAtSessionEnd(MemoryStore store, long minImportance) {
      if (store == null) {
         return false;
      }

      long floor = minImportance > 0L ? minImportance : 20L;
      return store.cumulativeImportanceSinceLastReflection() >= floor;
   }
}
