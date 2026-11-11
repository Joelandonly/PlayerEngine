package com.player2.playerengine.memory.ingest;

import com.player2.playerengine.memory.MemoryScope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MemoryTurnBatcher {
   private final Map<MemoryScope, List<String>> buffers = new HashMap<>();

   public List<String> offer(MemoryScope scope, String turnText, int batchMin, int batchMax) {
      if (scope != null && turnText != null && !turnText.isBlank()) {
         int min = Math.max(1, batchMin);
         int max = Math.max(min, batchMax);
         List<String> buf = this.buffers.computeIfAbsent(scope, k -> new ArrayList<>());
         buf.add(turnText);

         while (buf.size() > max) {
            buf.remove(0);
         }

         if (buf.size() >= min) {
            List<String> batch = new ArrayList<>(buf);
            buf.clear();
            return batch;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public int bufferedCount(MemoryScope scope) {
      List<String> buf = this.buffers.get(scope);
      return buf == null ? 0 : buf.size();
   }

   public void discard(MemoryScope scope) {
      this.buffers.remove(scope);
   }

   public void clear() {
      this.buffers.clear();
   }
}
