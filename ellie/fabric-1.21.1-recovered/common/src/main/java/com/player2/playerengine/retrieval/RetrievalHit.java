package com.player2.playerengine.retrieval;

import java.util.Arrays;

public final class RetrievalHit {
   private final String toolId;
   private final double score;
   private final int[] retrieverRanks;

   public RetrievalHit(String toolId, double score, int[] retrieverRanks) {
      this.toolId = toolId;
      this.score = score;
      this.retrieverRanks = normalize(retrieverRanks);
   }

   public RetrievalHit(String toolId, double score, int bm25Rank, int minHashRank) {
      this.toolId = toolId;
      this.score = score;
      int[] r = RetrieverRegistry.emptyRanks();
      r[0] = bm25Rank;
      r[1] = minHashRank;
      this.retrieverRanks = r;
   }

   private static int[] normalize(int[] src) {
      int[] r = RetrieverRegistry.emptyRanks();
      if (src != null) {
         int n = Math.min(src.length, r.length);
         System.arraycopy(src, 0, r, 0, n);
      }

      return r;
   }

   public String toolId() {
      return this.toolId;
   }

   public String id() {
      return this.toolId;
   }

   public double score() {
      return this.score;
   }

   public int[] retrieverRanks() {
      return this.retrieverRanks;
   }

   public int rank(int slot) {
      return slot >= 0 && slot < this.retrieverRanks.length ? this.retrieverRanks[slot] : Integer.MAX_VALUE;
   }

   public int retrieverCount() {
      return this.retrieverRanks.length;
   }

   public int bm25Rank() {
      return this.rank(0);
   }

   public int minHashRank() {
      return this.rank(1);
   }

   @Override
   public String toString() {
      String bm25 = this.bm25Rank() == Integer.MAX_VALUE ? "—" : String.valueOf(this.bm25Rank());
      String mh = this.minHashRank() == Integer.MAX_VALUE ? "—" : String.valueOf(this.minHashRank());
      return String.format(
         "RetrievalHit{id='%s', score=%.4f, bm25=%s, minHash=%s, ranks=%s}", this.toolId, this.score, bm25, mh, Arrays.toString(this.retrieverRanks)
      );
   }
}
