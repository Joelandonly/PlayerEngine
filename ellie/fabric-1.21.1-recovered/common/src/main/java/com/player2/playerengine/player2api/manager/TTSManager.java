package com.player2.playerengine.player2api.manager;

import com.player2.playerengine.player2api.Character;
import com.player2.playerengine.player2api.MarkerParser;
import com.player2.playerengine.player2api.Player2APIService;
import com.player2.playerengine.util.ExecutorShutdown;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TTSManager {
   private static final Logger LOGGER = LogManager.getLogger();
   private static volatile ExecutorService ttsThread = Executors.newSingleThreadExecutor();

   public static ExecutorService getExecutor() {
      return ttsThread;
   }

   public static synchronized void shutdownAndReset() {
      ExecutorService prev = ttsThread;
      if (prev != null && !prev.isShutdown()) {
         ExecutorShutdown.shutdownNowAwait("TTSManager", prev);
      }

      ttsThread = Executors.newSingleThreadExecutor();
   }

   public static void TTS(String message, Character character, Player2APIService player2apiService, UUID botUuid) {
      TTS(message, List.of(message == null ? "" : message), List.of(), character, player2apiService, botUuid);
   }

   public static void TTS(
      String message,
      List<String> chunks,
      List<MarkerParser.SegmentBoundary> validBoundaries,
      Character character,
      Player2APIService player2apiService,
      UUID botUuid
   ) {
      if (message != null) {
         LOGGER.info(
            "TTSManager.TTS submitting broadcast for msg.len={} chunks={} boundaries={}",
            message.length(),
            chunks == null ? 0 : chunks.size(),
            validBoundaries == null ? 0 : validBoundaries.size()
         );
         ttsThread.submit(() -> player2apiService.textToSpeech(message, chunks, validBoundaries, character, botUuid, _unusedMap -> {}));
      }
   }
}
