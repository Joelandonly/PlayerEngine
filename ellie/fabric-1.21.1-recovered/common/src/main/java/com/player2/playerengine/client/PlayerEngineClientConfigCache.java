package com.player2.playerengine.client;

import com.player2.playerengine.player2api.config.Player2PayerMode;

public final class PlayerEngineClientConfigCache {
   private static volatile boolean dedicatedClientProxy = false;
   private static volatile Player2PayerMode payerMode = Player2PayerMode.PROMPTER_PAYS;
   private static volatile boolean ownerOfflineServerContinuation = false;
   private static volatile String heartbeatClientId = "player2-ai-npc-minecraft";
   private static volatile int bodylangMarkerPauseMs = 250;
   private static volatile boolean bodylangGaplessPrefetch = true;

   private PlayerEngineClientConfigCache() {
   }

   public static void applyFromSync(
      boolean dedicated, String payerModeName, boolean ownerOffline, String hbClientId, int markerPauseMs, boolean gaplessPrefetch
   ) {
      dedicatedClientProxy = dedicated;
      ownerOfflineServerContinuation = ownerOffline;
      heartbeatClientId = hbClientId != null && !hbClientId.isBlank() ? hbClientId : "player2-ai-npc-minecraft";
      bodylangMarkerPauseMs = markerPauseMs < 0 ? 0 : Math.min(markerPauseMs, 2000);
      bodylangGaplessPrefetch = gaplessPrefetch;

      try {
         payerMode = Player2PayerMode.valueOf(payerModeName);
      } catch (Exception e) {
         payerMode = Player2PayerMode.PROMPTER_PAYS;
      }
   }

   public static boolean shouldSendPlayerHeartbeat() {
      return dedicatedClientProxy || payerMode == Player2PayerMode.PROMPTER_PAYS;
   }

   public static String getHeartbeatClientId() {
      return heartbeatClientId;
   }

   public static int getBodylangMarkerPauseMs() {
      return bodylangMarkerPauseMs;
   }

   public static boolean isBodylangGaplessPrefetch() {
      return bodylangGaplessPrefetch;
   }
}
