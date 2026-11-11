package com.player2.playerengine.memory.dense;

import com.player2.playerengine.PlayerEngineController;
import com.player2.playerengine.player2api.Player2PayerResolution;
import java.util.ArrayList;
import java.util.List;

public final class EmbeddingBatcher {
   public static final int MAX_BATCH = 100;
   public static final int MAX_EMBED_CHARS = 24000;

   private EmbeddingBatcher() {
   }

   public static EmbeddingResult embedAll(List<String> inputs, PlayerEngineController controller, Player2PayerResolution.ApiBillingContext billing) {
      if (inputs != null && !inputs.isEmpty()) {
         List<String> guarded = new ArrayList<>(inputs.size());

         for (String s : inputs) {
            guarded.add(guard(s));
         }

         float[][] out = new float[guarded.size()][];
         int totalTokens = 0;

         for (int start = 0; start < guarded.size(); start += 100) {
            int end = Math.min(start + 100, guarded.size());
            List<String> chunk = guarded.subList(start, end);
            EmbeddingResult r = EmbeddingProvider.embed(chunk, controller, billing);
            if (!r.ok()) {
               return r;
            }

            float[][] chunkVecs = r.vectors();

            for (int j = 0; j < chunkVecs.length && start + j < out.length; j++) {
               out[start + j] = chunkVecs[j];
            }

            totalTokens += r.totalTokens();
         }

         return EmbeddingResult.ok(out, EmbeddingModel.vectorModelToken(), totalTokens);
      } else {
         return EmbeddingResult.ok(new float[0][], EmbeddingModel.vectorModelToken(), 0);
      }
   }

   static String guard(String s) {
      if (s == null) {
         return "";
      } else {
         return s.length() <= 24000 ? s : s.substring(0, 24000);
      }
   }
}
