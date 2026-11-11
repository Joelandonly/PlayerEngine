package com.player2.playerengine.retrieval;

import java.util.List;

public final class LexicalRetriever implements Retriever {
   private final LexicalIndex index;

   public LexicalRetriever(LexicalIndex index) {
      this.index = index;
   }

   @Override
   public List<RetrievalHit> query(String goal, int k) {
      return this.index.query(goal, k);
   }

   @Override
   public int slotIndex() {
      return 0;
   }
}
