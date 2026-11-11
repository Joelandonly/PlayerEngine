package com.player2.playerengine.memory.resolution;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.player2.playerengine.PlayerEngine;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.budget.Layer3Context;
import com.player2.playerengine.memory.budget.MemoryLlmClient;
import com.player2.playerengine.player2api.AiTaskClass;
import com.player2.playerengine.player2api.ConversationHistory;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

public final class AliasDisambiguationClient {
   public static final int MAX_CANDIDATES = 5;
   public static final double CONFIRM_THRESHOLD = 0.7;
   private static final int REASON_MAX = 120;
   public static final Set<String> ROLE_LEXICON = Set.of(
      "blacksmith",
      "smith",
      "guard",
      "merchant",
      "trader",
      "friend",
      "king",
      "queen",
      "elder",
      "chief",
      "priest",
      "healer",
      "innkeeper",
      "farmer",
      "miner",
      "hunter",
      "captain",
      "mayor",
      "wizard",
      "witch",
      "shopkeeper"
   );

   private AliasDisambiguationClient() {
   }

   public static boolean isReferentialAlias(String rawMention, String normalized) {
      if (normalized != null && !normalized.isEmpty()) {
         String raw = rawMention == null ? "" : rawMention.trim();
         if (raw.toLowerCase(Locale.ROOT).startsWith("the ")) {
            return true;
         }

         for (String tok : normalized.split("\\s+")) {
            if (ROLE_LEXICON.contains(tok)) {
               return true;
            }
         }

         return !raw.isEmpty() && raw.equals(raw.toLowerCase(Locale.ROOT));
      } else {
         return false;
      }
   }

   public static ResolutionResult confirm(
      MinecraftServer server, Layer3Context ctx, String rawMention, String normalized, String hintType, List<MemoryNode> candidates
   ) {
      if (ctx == null || !ctx.layer3Enabled() || server == null) {
         return ResolutionResult.skippedBudget();
      }

      if (candidates == null || candidates.isEmpty()) {
         return ResolutionResult.newNode();
      }

      if (!isReferentialAlias(rawMention, normalized)) {
         return ResolutionResult.newNode();
      }

      List<MemoryNode> offered = candidates.size() > 5 ? candidates.subList(0, 5) : candidates;

      try {
         ConversationHistory history = buildPrompt(normalized, hintType, offered);
         String raw = MemoryLlmClient.completeConversationToString(server, ctx.ownerBilling(), history, AiTaskClass.SUMMARIZATION);
         return parseDecision(raw, offered);
      } catch (Exception e) {
         PlayerEngine.LOGGER.debug("[memory] layer-3 disambiguation failed ({}); minting new node", e.getClass().getSimpleName());
         return ResolutionResult.newNode();
      }
   }

   private static ConversationHistory buildPrompt(String normalized, String hintType, List<MemoryNode> candidates) {
      StringBuilder sys = new StringBuilder();
      sys.append("You disambiguate a referring expression to a known entity. ");
      sys.append("Given a MENTION and a list of CANDIDATE entities, decide whether the mention ")
         .append("refers to exactly one candidate. Only confirm when you are confident the mention ")
         .append("denotes that specific candidate; otherwise return a null match. ");
      sys.append("Respond with a single JSON object and nothing else: ")
         .append("{\"match_id\": <candidate id string or null>, ")
         .append("\"confidence\": <number 0..1>, ")
         .append("\"reason\": <short string, <= ")
         .append(120)
         .append(" chars>}.");
      StringBuilder user = new StringBuilder();
      user.append("MENTION: ").append(normalized);
      if (hintType != null && !hintType.isEmpty()) {
         user.append(" (type hint: ").append(hintType).append(')');
      }

      user.append('\n').append("CANDIDATES:");

      for (MemoryNode c : candidates) {
         user.append("\n- id=").append(c.id()).append(" name=\"").append(c.canonicalName() == null ? "" : c.canonicalName()).append('"');
         if (c.type() != null && !c.type().isEmpty()) {
            user.append(" type=").append(c.type());
         }

         List<String> aliases = c.aliases();
         if (!aliases.isEmpty()) {
            user.append(" aliases=").append(aliases);
         }
      }

      ConversationHistory history = new ConversationHistory(sys.toString());
      history.addUserMessage(user.toString(), null);
      return history;
   }

   private static ResolutionResult parseDecision(String raw, List<MemoryNode> candidates) {
      if (raw != null && !raw.isBlank()) {
         try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            if (obj.has("match_id") && !obj.get("match_id").isJsonNull()) {
               String matchId = obj.get("match_id").getAsString();
               if (matchId != null && !matchId.isBlank()) {
                  double confidence = obj.has("confidence") && !obj.get("confidence").isJsonNull() ? obj.get("confidence").getAsDouble() : 0.0;
                  if (confidence < 0.7) {
                     return ResolutionResult.newNode();
                  }

                  for (MemoryNode c : candidates) {
                     if (matchId.equals(c.id())) {
                        return ResolutionResult.llmConfirmed(c.id(), confidence, c.canonicalName());
                     }
                  }

                  return ResolutionResult.newNode();
               } else {
                  return ResolutionResult.newNode();
               }
            } else {
               return ResolutionResult.newNode();
            }
         } catch (RuntimeException parseEx) {
            return ResolutionResult.newNode();
         }
      } else {
         return ResolutionResult.newNode();
      }
   }
}
