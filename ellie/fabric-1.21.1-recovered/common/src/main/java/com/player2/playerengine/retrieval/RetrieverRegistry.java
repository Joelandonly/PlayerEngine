package com.player2.playerengine.retrieval;

import java.util.Arrays;

public final class RetrieverRegistry {
   public static final int SLOT_LEXICAL = 0;
   public static final int SLOT_MINHASH = 1;
   public static final int SLOT_GRAPH = 2;
   public static final int SLOT_DENSE = 3;
   public static final int WIDTH = 4;
   public static final int ABSENT_RANK = Integer.MAX_VALUE;

   private RetrieverRegistry() {
   }

   public static int[] emptyRanks() {
      int[] r = new int[4];
      Arrays.fill(r, Integer.MAX_VALUE);
      return r;
   }
}
