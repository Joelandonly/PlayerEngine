package com.player2.playerengine.retrieval;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public final class LexicalIndex implements Retriever {
   private static final double K1 = 1.2;
   private static final double B = 0.75;
   private static final Pattern SPLIT = Pattern.compile("[^a-z0-9]+");
   private static final int MIN_TOKEN_LEN = 2;
   private final LexicalIndex.State state;
   private final Map<String, Integer> docIndexMap;

   private LexicalIndex(LexicalIndex.State state) {
      this.state = state;
      this.docIndexMap = new HashMap<>(state.docIds.length * 2);

      for (int i = 0; i < state.docIds.length; i++) {
         this.docIndexMap.put(state.docIds[i], i);
      }
   }

   public static LexicalIndex build(Collection<ToolDocument> documents) {
      List<ToolDocument> docs = new ArrayList<>(documents);
      int n = docs.size();
      String[] docIds = new String[n];
      int[] docLengths = new int[n];
      HashMap<String, List<int[]>> postings = new HashMap<>();

      for (int i = 0; i < n; i++) {
         ToolDocument doc = docs.get(i);
         docIds[i] = doc.id();
         String[] tokens = tokenize(doc.indexedText());
         docLengths[i] = tokens.length;
         Map<String, Integer> tf = new LinkedHashMap<>();

         for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
         }

         for (Entry<String, Integer> e : tf.entrySet()) {
            postings.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(new int[]{i, e.getValue()});
         }
      }

      double totalLen = 0.0;

      for (int l : docLengths) {
         totalLen += l;
      }

      double avgDocLength = n == 0 ? 0.0 : totalLen / n;
      return new LexicalIndex(new LexicalIndex.State(docIds, docLengths, avgDocLength, postings));
   }

   static LexicalIndex fromState(LexicalIndex.State state) {
      return new LexicalIndex(state);
   }

   LexicalIndex.State getState() {
      return this.state;
   }

   @Override
   public List<RetrievalHit> query(String text, int topK) {
      String[] queryTokens = tokenize(text);
      if (queryTokens.length != 0 && this.state.docIds.length != 0) {
         int n = this.state.docIds.length;
         double[] scores = new double[n];

         for (String token : queryTokens) {
            List<int[]> posting = this.state.postings.get(token);
            if (posting != null) {
               int df = posting.size();
               double idf = Math.log(1.0 + (n - df + 0.5) / (df + 0.5));

               for (int[] p : posting) {
                  int docIdx = p[0];
                  int tf = p[1];
                  double normFactor = this.state.docLengths[docIdx] / this.state.avgDocLength;
                  double tfNorm = tf * 2.2 / (tf + 1.2 * (0.25 + 0.75 * normFactor));
                  scores[docIdx] += idf * tfNorm;
               }
            }
         }

         List<LexicalIndex.IndexedScore> sortable = new ArrayList<>();

         for (int i = 0; i < n; i++) {
            if (scores[i] > 0.0) {
               sortable.add(new LexicalIndex.IndexedScore(i, scores[i]));
            }
         }

         sortable.sort((a, b) -> Double.compare(b.score, a.score));
         List<RetrievalHit> hits = new ArrayList<>();
         int limit = Math.min(topK, sortable.size());

         for (int r = 0; r < limit; r++) {
            LexicalIndex.IndexedScore is = sortable.get(r);
            hits.add(new RetrievalHit(this.state.docIds[is.idx], is.score, r + 1, Integer.MAX_VALUE));
         }

         return hits;
      } else {
         return Collections.emptyList();
      }
   }

   @Override
   public int slotIndex() {
      return 0;
   }

   static String[] tokenize(String text) {
      String lower = text.toLowerCase();
      String[] parts = SPLIT.split(lower);
      List<String> tokens = new ArrayList<>(parts.length);

      for (String p : parts) {
         if (p.length() >= 2) {
            tokens.add(p);
         }
      }

      return tokens.toArray(new String[0]);
   }

   private static final class IndexedScore {
      final int idx;
      final double score;

      IndexedScore(int idx, double score) {
         this.idx = idx;
         this.score = score;
      }
   }

   static final class State implements Serializable {
      private static final long serialVersionUID = 1L;
      final String[] docIds;
      final int[] docLengths;
      final double avgDocLength;
      final HashMap<String, List<int[]>> postings;

      State(String[] docIds, int[] docLengths, double avgDocLength, HashMap<String, List<int[]>> postings) {
         this.docIds = docIds;
         this.docLengths = docLengths;
         this.avgDocLength = avgDocLength;
         this.postings = postings;
      }
   }
}
