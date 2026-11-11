package com.player2.playerengine.retrieval;

import java.util.List;

public interface Retriever {
   List<RetrievalHit> query(String var1, int var2);

   int slotIndex();
}
