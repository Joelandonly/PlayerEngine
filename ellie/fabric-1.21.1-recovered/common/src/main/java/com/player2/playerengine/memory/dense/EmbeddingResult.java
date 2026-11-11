package com.player2.playerengine.memory.dense;

public final class EmbeddingResult {
   private final float[][] vectors;
   private final String vectorModel;
   private final int totalTokens;
   private final String degradedToken;

   private EmbeddingResult(float[][] vectors, String vectorModel, int totalTokens, String degradedToken) {
      this.vectors = vectors;
      this.vectorModel = vectorModel;
      this.totalTokens = totalTokens;
      this.degradedToken = degradedToken;
   }

   public static EmbeddingResult ok(float[][] vectors, String vectorModel, int totalTokens) {
      return new EmbeddingResult(vectors, vectorModel, Math.max(0, totalTokens), null);
   }

   public static EmbeddingResult degraded(String boundedToken) {
      return new EmbeddingResult(null, null, 0, boundedToken);
   }

   public boolean ok() {
      return this.vectors != null && this.degradedToken == null;
   }

   public float[][] vectors() {
      return this.vectors;
   }

   public String vectorModel() {
      return this.vectorModel;
   }

   public int totalTokens() {
      return this.totalTokens;
   }

   public double joules() {
      return EmbeddingModel.joulesFor(this.totalTokens);
   }

   public String degradedToken() {
      return this.degradedToken;
   }
}
