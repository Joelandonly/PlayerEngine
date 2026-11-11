package com.player2.playerengine.memory.dense;

public final class EmbeddingModel {
   public static final String FROZEN = "text-embedding-3-small";
   public static final int FROZEN_DIMS = 1536;
   public static final double JOULES_PER_TOKEN = 2.0E-5;

   private EmbeddingModel() {
   }

   public static String vectorModelToken() {
      return "player2:text-embedding-3-small:1536";
   }

   public static double joulesFor(int totalTokens) {
      return Math.max(0, totalTokens) * 2.0E-5;
   }
}
