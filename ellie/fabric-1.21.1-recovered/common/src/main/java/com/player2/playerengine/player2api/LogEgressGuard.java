package com.player2.playerengine.player2api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LogEgressGuard {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final int MAX_MODEL_MESSAGE_CHARS = 32768;
   public static final int MAX_CHAT_COMPLETION_CONTENT_CHARS = 24576;
   public static final int MAX_CHAT_COMPLETION_MESSAGES = 32;
   public static final int MAX_SAFE_CHAT_COMPLETION_OUTPUT_TOKENS = 2048;
   public static final int DEFAULT_CHAT_COMPLETION_OUTPUT_TOKENS = 2048;
   private static final int MIN_PARTIAL_MESSAGE_CHARS = 1024;
   private static final int OMISSION_NOTICE_RESERVE_CHARS = 160;
   private static final int TRUNCATION_MARKER_RESERVE_CHARS = 256;

   private LogEgressGuard() {
   }

   public static String capForModel(String content, String role) {
      if (content != null && content.length() > 32768) {
         int cut = Math.max(0, 32512);
         if (cut > 0 && java.lang.Character.isHighSurrogate(content.charAt(cut - 1))) {
            cut--;
         }

         int overflow = content.length() - cut;
         LOGGER.warn(
            "[LogEgressGuard] Oversized model-facing message capped: role={} originalChars={} cap={} truncatedChars={}",
            role,
            content.length(),
            32768,
            overflow
         );
         return content.substring(0, cut) + "\n…[TRUNCATED " + overflow + " chars by LogEgressGuard — possible log/diagnostic egress, see DESIGN.md §3]";
      } else {
         return content;
      }
   }

   public static JsonObject cappedMessage(JsonObject msg) {
      if (msg == null) {
         return malformedMessageNotice();
      } else {
         String role = roleOf(msg);
         JsonElement contentEl = msg.get("content");
         if (isAllowedRole(role) && contentEl != null && contentEl.isJsonPrimitive() && contentEl.getAsJsonPrimitive().isString()) {
            JsonObject copy = new JsonObject();
            copy.addProperty("role", role);
            copy.addProperty("content", capForModel(contentEl.getAsString(), role));
            return copy;
         } else {
            LOGGER.warn(
               "[LogEgressGuard] Malformed chat message replaced before model dispatch: role={}",
               role == null ? "invalid" : (isAllowedRole(role) ? role : "unsupported")
            );
            return malformedMessageNotice();
         }
      }
   }

   private static boolean isAllowedRole(String role) {
      return "system".equals(role) || "user".equals(role) || "assistant".equals(role);
   }

   private static JsonObject malformedMessageNotice() {
      JsonObject notice = new JsonObject();
      notice.addProperty("role", "system");
      notice.addProperty("content", "Context notice: one malformed conversation message was omitted.");
      return notice;
   }

   public static JsonArray cappedMessages(List<JsonObject> messages, String surface) {
      JsonArray result = new JsonArray();
      if (messages != null && !messages.isEmpty()) {
         List<JsonObject> selectedSource = new ArrayList<>();
         int omittedByCount = 0;
         if (messages.size() > 32) {
            boolean preserveSystem = isRole(messages.get(0), "system");
            int originalTailStart = preserveSystem ? 1 : 0;
            int tailSlots = 31 - (preserveSystem ? 1 : 0);
            int selectedTailStart = Math.max(originalTailStart, messages.size() - tailSlots);
            if (preserveSystem) {
               selectedSource.add(messages.get(0));
            }

            selectedSource.addAll(messages.subList(selectedTailStart, messages.size()));
            omittedByCount = selectedTailStart - originalTailStart;
         } else {
            selectedSource.addAll(messages);
         }

         List<JsonObject> cappedSource = new ArrayList<>(selectedSource.size());
         long sourceChars = 0L;

         for (JsonObject message : selectedSource) {
            JsonObject capped = cappedMessage(message);
            cappedSource.add(capped);
            sourceChars += contentChars(capped);
         }

         if (sourceChars <= 24576L && omittedByCount == 0) {
            for (JsonObject message : cappedSource) {
               result.add(message);
            }

            return result;
         } else {
            int used = 0;
            int start = 0;
            JsonObject first = cappedSource.get(0);
            if (isRole(first, "system")) {
               JsonObject system = first;
               int systemChars = contentChars(system);
               int maxSystemChars = cappedSource.size() > 1 ? 23392 : 24576;
               if (systemChars > maxSystemChars) {
                  system = copyWithCappedContent(system, maxSystemChars, "system");
                  systemChars = contentChars(system);
               }

               result.add(system);
               used += systemChars;
               start = 1;
            }

            List<JsonObject> suffix = new ArrayList<>();
            int omitted = omittedByCount;
            int remaining = Math.max(0, 24576 - used - 160);

            for (int i = cappedSource.size() - 1; i >= start; i--) {
               JsonObject capped = cappedSource.get(i);
               int chars = contentChars(capped);
               if (chars > remaining) {
                  if (suffix.isEmpty() && remaining >= 1024) {
                     suffix.add(copyWithCappedContent(capped, remaining, roleOf(capped)));
                     omitted += i - start;
                     break;
                  }

                  omitted += i - start + 1;
                  break;
               }

               suffix.add(capped);
               remaining -= chars;
            }

            if (omitted > 0) {
               LOGGER.warn(
                  "[LogEgressGuard] Chat-completion request capped: surface={} sourceMessages={} omittedMessages={} contentCharsCap={}",
                  surface,
                  messages.size(),
                  omitted,
                  24576
               );
               JsonObject notice = new JsonObject();
               notice.addProperty("role", "system");
               notice.addProperty("content", "Context notice: " + omitted + " older message(s) were omitted to stay within the request limit.");
               result.add(notice);
            }

            Collections.reverse(suffix);

            for (JsonObject msg : suffix) {
               result.add(msg);
            }

            return result;
         }
      } else {
         return result;
      }
   }

   public static void applyChatCompletionRequestCaps(JsonObject requestBody, String surface) {
      if (requestBody != null) {
         int cap = configuredOutputTokenCap();
         JsonElement maxTokens = requestBody.get("max_tokens");
         if (maxTokens != null && !maxTokens.isJsonNull() && maxTokens.isJsonPrimitive() && maxTokens.getAsJsonPrimitive().isNumber()) {
            int requested = maxTokens.getAsInt();
            if (requested > 0 && requested <= cap) {
               return;
            }

            LOGGER.warn("[LogEgressGuard] Chat-completion max_tokens clamped: surface={} requested={} cap={}", surface, requested, cap);
         }

         requestBody.addProperty("max_tokens", cap);
      }
   }

   private static int configuredOutputTokenCap() {
      try {
         return Math.min(Player2ServerConfigHolder.get().getChatCompletionMaxOutputTokensClamped(), 2048);
      } catch (Exception e) {
         return 2048;
      }
   }

   private static boolean isRole(JsonObject msg, String role) {
      return role.equals(roleOf(msg));
   }

   private static String roleOf(JsonObject msg) {
      return msg != null
            && msg.has("role")
            && !msg.get("role").isJsonNull()
            && msg.get("role").isJsonPrimitive()
            && msg.get("role").getAsJsonPrimitive().isString()
         ? msg.get("role").getAsString()
         : null;
   }

   private static int contentChars(JsonObject msg) {
      if (msg == null) {
         return 0;
      }

      JsonElement contentEl = msg.get("content");
      return contentEl != null && contentEl.isJsonPrimitive() && contentEl.getAsJsonPrimitive().isString() ? contentEl.getAsString().length() : 0;
   }

   private static JsonObject copyWithCappedContent(JsonObject msg, int maxChars, String role) {
      if (msg != null && maxChars > 0) {
         JsonElement contentEl = msg.get("content");
         if (contentEl != null && contentEl.isJsonPrimitive() && contentEl.getAsJsonPrimitive().isString()) {
            String content = contentEl.getAsString();
            if (content.length() <= maxChars) {
               return msg;
            }

            int cut = Math.max(0, maxChars - 256);
            if (cut > 0 && java.lang.Character.isHighSurrogate(content.charAt(cut - 1))) {
               cut--;
            }

            int overflow = content.length() - cut;
            JsonObject copy = new JsonObject();

            for (Entry<String, JsonElement> entry : msg.entrySet()) {
               if ("content".equals(entry.getKey())) {
                  copy.addProperty("content", content.substring(0, cut) + "\n...[TRUNCATED " + overflow + " chars by LogEgressGuard request cap]");
               } else {
                  copy.add(entry.getKey(), entry.getValue());
               }
            }

            LOGGER.warn(
               "[LogEgressGuard] Oversized model-facing message capped by request budget: role={} originalChars={} cap={} truncatedChars={}",
               role,
               content.length(),
               maxChars,
               overflow
            );
            return copy;
         } else {
            return msg;
         }
      } else {
         return msg;
      }
   }
}
