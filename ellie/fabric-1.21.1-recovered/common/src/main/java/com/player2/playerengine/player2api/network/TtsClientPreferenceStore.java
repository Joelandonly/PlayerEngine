package com.player2.playerengine.player2api.network;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TtsClientPreferenceStore {
   private static final Set<UUID> TTS_DISABLED = ConcurrentHashMap.newKeySet();

   private TtsClientPreferenceStore() {
   }

   public static void setTtsEnabled(UUID playerId, boolean enabled) {
      if (enabled) {
         TTS_DISABLED.remove(playerId);
      } else {
         TTS_DISABLED.add(playerId);
      }
   }

   public static boolean isTtsEnabled(UUID playerId) {
      return !TTS_DISABLED.contains(playerId);
   }

   public static void clear(UUID playerId) {
      TTS_DISABLED.remove(playerId);
   }
}
