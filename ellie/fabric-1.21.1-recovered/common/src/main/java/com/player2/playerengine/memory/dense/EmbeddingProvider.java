package com.player2.playerengine.memory.dense;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.Player2ApiDispatcher;
import com.player2.playerengine.player2api.Player2PayerResolution;
import com.player2.playerengine.player2api.config.Player2ServerConfigHolder;
import com.player2.playerengine.player2api.utils.HttpApiException;
import com.player2.playerengine.util.Debug;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EmbeddingProvider {
   private static final String SERVER_THREAD_NAME = "Server thread";
   static final String TOKEN_REQUEST_INVALID = "embedding_request_invalid";
   static final String TOKEN_AUTH_REQUIRED = "embedding_auth_required";
   static final String TOKEN_INSUFFICIENT_JOULES = "embedding_insufficient_joules";
   static final String TOKEN_RATE_LIMITED = "embedding_rate_limited";
   static final String TOKEN_SERVER_ERROR = "embedding_server_error";

   private EmbeddingProvider() {
   }

   public static boolean isAvailable() {
      return true;
   }

   public static Optional<String> unavailable() {
      return Optional.empty();
   }

   public static EmbeddingResult embed(List<String> inputs, PlayerEngineController controller, Player2PayerResolution.ApiBillingContext billing) {
      assertOffTick();
      if (inputs == null || inputs.isEmpty()) {
         return EmbeddingResult.ok(new float[0][], EmbeddingModel.vectorModelToken(), 0);
      }

      if (controller != null && billing != null && billing.billingKey() != null) {
         JsonObject body = new JsonObject();
         body.addProperty("model", "text-embedding-3-small");
         body.addProperty("encoding_format", "base64");
         JsonArray in = new JsonArray();
         inputs.forEach(in::add);
         body.add("input", in);
         String clientId = Player2ServerConfigHolder.get().getHeartbeatClientId();

         try {
            Map<String, JsonElement> resp = Player2ApiDispatcher.route(controller, clientId, "POST", "/v1/embeddings", body, billing);
            JsonElement dataEl = resp.get("data");
            if (dataEl != null && dataEl.isJsonArray()) {
               JsonArray data = dataEl.getAsJsonArray();
               float[][] vecs = new float[data.size()][];

               for (JsonElement e : data) {
                  JsonObject o = e.getAsJsonObject();
                  int idx = o.get("index").getAsInt();
                  if (idx < 0 || idx >= vecs.length) {
                     Debug.logWarning("Embedding response index out of range: %s (size %s).", idx, vecs.length);
                     return EmbeddingResult.degraded("embedding_server_error");
                  }

                  vecs[idx] = decodeBase64LEf32(o.get("embedding").getAsString());
               }

               int totalTokens = 0;
               JsonElement usageEl = resp.get("usage");
               if (usageEl != null && usageEl.isJsonObject()) {
                  JsonElement totEl = usageEl.getAsJsonObject().get("total_tokens");
                  if (totEl != null && totEl.isJsonPrimitive()) {
                     totalTokens = totEl.getAsInt();
                  }
               }

               return EmbeddingResult.ok(vecs, EmbeddingModel.vectorModelToken(), totalTokens);
            } else {
               Debug.logWarning("Embedding response missing 'data' array.");
               return EmbeddingResult.degraded("embedding_server_error");
            }
         } catch (HttpApiException e) {
            String token = switch (e.getStatusCode()) {
               case 400 -> "embedding_request_invalid";
               case 401 -> "embedding_auth_required";
               case 402 -> "embedding_insufficient_joules";
               case 429 -> "embedding_rate_limited";
               default -> "embedding_server_error";
            };
            Debug.logWarning("Embedding call failed (status %s): %s", e.getStatusCode(), e.getMessage());
            return EmbeddingResult.degraded(token);
         } catch (Exception e) {
            Debug.logWarning("Embedding call failed (%s).", e.getClass().getSimpleName());
            return EmbeddingResult.degraded("embedding_server_error");
         }
      } else {
         Debug.logWarning("Embedding call skipped: no route/billing context.");
         return EmbeddingResult.degraded("embedding_auth_required");
      }
   }

   static float[] decodeBase64LEf32(String b64) {
      byte[] bytes = Base64.getDecoder().decode(b64);
      ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
      float[] out = new float[bytes.length / 4];

      for (int i = 0; i < out.length; i++) {
         out[i] = buf.getFloat();
      }

      return out;
   }

   static void assertOffTick() {
      if ("Server thread".equals(Thread.currentThread().getName())) {
         throw new IllegalStateException("EmbeddingProvider.embed must not run on the server tick thread (off-tick only).");
      }
   }
}
