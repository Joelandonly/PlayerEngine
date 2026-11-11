package com.player2.playerengine.memory.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.memory.budget.Layer3Context;
import com.player2.playerengine.memory.budget.MemoryGate;
import com.player2.playerengine.memory.budget.MemoryLlmClient;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.ConversationHistory;
import com.player2.playerengine.player2api.Player2PayerResolution;
import net.minecraft.server.MinecraftServer;

public final class ImportanceScorer {
   public static final int UNSCORED = 0;
   public static final int MIN_IMPORTANCE = 1;
   public static final int MAX_IMPORTANCE = 10;
   public static final int RUBRIC_VERSION = 1;
   private static final String SYSTEM_PROMPT = "You rate how emotionally or relationally significant a single remembered fact is to an ongoing companionship, on a 1-10 scale. Anchors: 1 = mundane/forgettable; 5 = shared personal information; 8 = a promise, betrayal, or a real gift; 10 = saved or endangered a life. Most facts are low. AVOID clustering scores at 7-10 — reserve those for genuinely high-stakes facts. Reply with ONLY a JSON object of the exact form {\"importance\": <integer 1-10>} and nothing else.";

   private ImportanceScorer() {
   }

   public static int scoreImportance(MinecraftServer server, Layer3Context layer3Context, String factContent) {
      if (layer3Context != null && layer3Context.layer3Enabled()) {
         Player2PayerResolution.ApiBillingContext ownerBilling = layer3Context.ownerBilling();
         if (ownerBilling == null || ownerBilling.billingKey() == null) {
            return 0;
         }

         if (factContent == null || factContent.isBlank()) {
            return 0;
         }

         if (!MemoryGate.reserveSlot(ownerBilling)) {
            return 0;
         }

         try {
            ConversationHistory history = new ConversationHistory(
               "You rate how emotionally or relationally significant a single remembered fact is to an ongoing companionship, on a 1-10 scale. Anchors: 1 = mundane/forgettable; 5 = shared personal information; 8 = a promise, betrayal, or a real gift; 10 = saved or endangered a life. Most facts are low. AVOID clustering scores at 7-10 — reserve those for genuinely high-stakes facts. Reply with ONLY a JSON object of the exact form {\"importance\": <integer 1-10>} and nothing else."
            );
            history.addUserMessage("Fact: " + factContent, null);
            String reply = MemoryLlmClient.completeConversationToString(server, ownerBilling, history, AiTaskClass.SUMMARIZATION);
            return parseAndClamp(reply);
         } catch (Exception e) {
            PlayerEngine.LOGGER.warn("Memory importance scoring failed (reason: {}); leaving node unscored.", e.getClass().getSimpleName());
            return 0;
         }
      } else {
         return 0;
      }
   }

   public static int parseAndClamp(String reply) {
      if (reply != null && !reply.isBlank()) {
         try {
            JsonObject obj = JsonParser.parseString(reply).getAsJsonObject();
            if (obj != null && obj.has("importance") && obj.get("importance").isJsonPrimitive()) {
               int raw = obj.get("importance").getAsInt();
               return clamp(raw);
            } else {
               return 0;
            }
         } catch (Exception parseEx) {
            return 0;
         }
      } else {
         return 0;
      }
   }

   private static int clamp(int v) {
      if (v < 1) {
         return 1;
      } else {
         return v > 10 ? 10 : v;
      }
   }
}
