package com.player2.playerengine.retrieval;

public final class RetrievalTokenizer {
   private RetrievalTokenizer() {
   }

   public static String[] tokenize(String text) {
      return LexicalIndex.tokenize(text);
   }
}
