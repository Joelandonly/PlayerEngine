package com.player2.playerengine.player2api;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.manager.ConversationManager;

public final class AiConversationFeedback {
   private AiConversationFeedback() {
   }

   public static void enqueueInfo(PlayerEngineController mod, String message) {
      ConversationManager.getOrCreateEventQueueData(mod).onEvent(new Event.InfoMessage(message));
   }

   public static void deferInfo(PlayerEngineController mod, String message) {
      ConversationManager.getOrCreateEventQueueData(mod).deferInfo(new Event.InfoMessage(message));
   }
}
