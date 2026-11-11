package com.player2.playerengine.retrieval;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class MinHashIndex implements Retriever {
   private static final int NUM_HASHES = 128;
   private static final int NUM_BANDS = 32;
   private static final int ROWS_PER_BAND = 4;
   private static final int NGRAM_SIZE = 4;
   private static final int MERSENNE_P = Integer.MAX_VALUE;
   private static final int[] HASH_A = new int[128];
   private static final int[] HASH_B = new int[128];
   private final MinHashIndex.State state;
   private final List<HashMap<Long, List<Integer>>> bandTables;

   private MinHashIndex(MinHashIndex.State state) {
      this.state = state;
      this.bandTables = buildBandTables(state.signatures);
   }

   public static MinHashIndex build(Collection<ToolDocument> documents) {
      List<ToolDocument> docs = new ArrayList<>(documents);
      int n = docs.size();
      String[] docIds = new String[n];
      int[][] signatures = new int[n][128];

      for (int i = 0; i < n; i++) {
         docIds[i] = docs.get(i).id();
         Set<Integer> ngrams = extractNgrams(docs.get(i).indexedText());
         signatures[i] = computeSignature(ngrams);
      }

      return new MinHashIndex(new MinHashIndex.State(docIds, signatures));
   }

   static MinHashIndex fromState(MinHashIndex.State state) {
      return new MinHashIndex(state);
   }

   MinHashIndex.State getState() {
      return this.state;
   }

   @Override
   public List<RetrievalHit> query(String text, int topK) {
      Set<Integer> queryNgrams = extractNgrams(text);
      if (!queryNgrams.isEmpty() && this.state.docIds.length != 0) {
         int[] querySig = computeSignature(queryNgrams);
         Set<Integer> candidates = new HashSet<>();

         for (int band = 0; band < 32; band++) {
            long key = bandHash(querySig, band * 4);
            List<Integer> bucket = this.bandTables.get(band).get(key);
            if (bucket != null) {
               candidates.addAll(bucket);
            }
         }

         List<int[]> ranked = new ArrayList<>(candidates.size());

         for (int docIdx : candidates) {
            int matches = 0;

            for (int h = 0; h < 128; h++) {
               if (this.state.signatures[docIdx][h] == querySig[h]) {
                  matches++;
               }
            }

            ranked.add(new int[]{docIdx, matches});
         }

         ranked.sort((a, b) -> Integer.compare(b[1], a[1]));
         List<RetrievalHit> hits = new ArrayList<>();
         int limit = Math.min(topK, ranked.size());

         for (int r = 0; r < limit; r++) {
            int docIdx = ranked.get(r)[0];
            double approxJaccard = ranked.get(r)[1] / 128.0;
            hits.add(new RetrievalHit(this.state.docIds[docIdx], approxJaccard, Integer.MAX_VALUE, r + 1));
         }

         return hits;
      } else {
         return new ArrayList<>();
      }
   }

   @Override
   public int slotIndex() {
      return 1;
   }

   private static Set<Integer> extractNgrams(String text) {
      String lower = text.toLowerCase();
      Set<Integer> ngrams = new HashSet<>();
      int len = lower.length();

      for (int i = 0; i <= len - 4; i++) {
         ngrams.add(lower.substring(i, i + 4).hashCode());
      }

      return ngrams;
   }

   private static int[] computeSignature(Set<Integer> ngrams) {
      int[] sig = new int[128];

      for (int h = 0; h < 128; h++) {
         sig[h] = Integer.MAX_VALUE;
      }

      for (int ngram : ngrams) {
         long x = ngram & 4294967295L;

         for (int h = 0; h < 128; h++) {
            int hashVal = (int)((HASH_A[h] * x + HASH_B[h]) % 2147483647L);
            if (hashVal < sig[h]) {
               sig[h] = hashVal;
            }
         }
      }

      return sig;
   }

   private static List<HashMap<Long, List<Integer>>> buildBandTables(int[][] signatures) {
      List<HashMap<Long, List<Integer>>> bands = new ArrayList<>(32);

      for (int b = 0; b < 32; b++) {
         bands.add(new HashMap<>());
      }

      for (int docIdx = 0; docIdx < signatures.length; docIdx++) {
         for (int band = 0; band < 32; band++) {
            long key = bandHash(signatures[docIdx], band * 4);
            bands.get(band).computeIfAbsent(key, k -> new ArrayList<>()).add(docIdx);
         }
      }

      return bands;
   }

   private static long bandHash(int[] sig, int start) {
      long h = 1L;

      for (int i = 0; i < 4; i++) {
         h = h * 31L + sig[start + i];
      }

      return h;
   }

   static {
      Random rng = new Random(3735928559L);

      for (int i = 0; i < 128; i++) {
         HASH_A[i] = rng.nextInt(2147483646) + 1 & 2147483647;
         HASH_B[i] = rng.nextInt(Integer.MAX_VALUE) & 2147483647;
      }
   }

   static final class State implements Serializable {
      private static final long serialVersionUID = 1L;
      final String[] docIds;
      final int[][] signatures;

      State(String[] docIds, int[][] signatures) {
         this.docIds = docIds;
         this.signatures = signatures;
      }
   }
}
