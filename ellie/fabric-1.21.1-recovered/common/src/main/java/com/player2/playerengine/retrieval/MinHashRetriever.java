package com.player2.playerengine.retrieval;

import java.util.List;

public final class MinHashRetriever implements Retriever {
   private final MinHashIndex index;

   public MinHashRetriever(MinHashIndex index) {
      this.index = index;
   }

   @Override
   public List<RetrievalHit> query(String goal, int k) {
      return this.index.query(goal, k);
   }

   @Override
   public int slotIndex() {
      return 1;
   }
}
