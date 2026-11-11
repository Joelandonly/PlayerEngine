package com.player2.playerengine.agentic.elliegps;

import com.player2.playerengine.retrieval.LexicalIndex;
import com.player2.playerengine.retrieval.MinHashIndex;
import com.player2.playerengine.retrieval.RetrievalHit;
import com.player2.playerengine.retrieval.RrfFusion;
import com.player2.playerengine.retrieval.ToolDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class WaypointSearchService {
   public static final int MAX_AUTHORITATIVE_RECORDS = 4096;
   private static final double RRF_K = 60.0;
   private static final long MAX_SQUARE_ROOT = 3037000499L;

   private WaypointSearchService() {
   }

   public static WaypointSearchResult find(
      String query, String dimension, Set<String> allowedTypes, boolean includeStale, WaypointSearchOrder order, BlockPos origin, int limit
   ) {
      return find(EllieGPSStore.get(), query, dimension, allowedTypes, includeStale, order, origin, limit);
   }

   static WaypointSearchResult find(
      EllieGPSStore store,
      String query,
      String dimension,
      Set<String> allowedTypes,
      boolean includeStale,
      WaypointSearchOrder order,
      BlockPos origin,
      int limit
   ) {
      Objects.requireNonNull(order, "order");
      if (order == WaypointSearchOrder.NEAREST && origin == null) {
         throw new IllegalArgumentException("origin is required for NEAREST waypoint search");
      }

      if (order == WaypointSearchOrder.RELEVANCE && origin != null) {
         throw new IllegalArgumentException("origin must be null for RELEVANCE waypoint search");
      }

      if (store == null) {
         return new WaypointSearchResult(List.of(), WaypointSearchStatus.FAILED_STORE_UNAVAILABLE);
      }

      List<WaypointRecord> authoritative = store.publishedRecords();
      if (authoritative.size() > 4096) {
         return new WaypointSearchResult(List.of(), WaypointSearchStatus.FAILED_SCAN_LIMIT);
      }

      String normalizedQuery = query == null ? "" : query.trim();
      String normalizedDimension = dimension == null ? "" : dimension.trim();
      Set<String> normalizedTypes = normalizeTypes(allowedTypes);
      int boundedLimit = Math.max(0, Math.min(limit, 4096));

      try {
         return order == WaypointSearchOrder.NEAREST
            ? nearest(authoritative, normalizedQuery, normalizedDimension, normalizedTypes, includeStale, origin, boundedLimit)
            : relevance(store, authoritative, normalizedQuery, normalizedDimension, normalizedTypes, includeStale, boundedLimit);
      } catch (RuntimeException e) {
         return new WaypointSearchResult(List.of(), WaypointSearchStatus.FAILED_SEARCH_ERROR);
      }
   }

   private static WaypointSearchResult relevance(
      EllieGPSStore store, List<WaypointRecord> authoritative, String query, String dimension, Set<String> allowedTypes, boolean includeStale, int limit
   ) {
      boolean indexHealthy = store.isIndexHealthy();
      if (!indexHealthy) {
         indexHealthy = store.synchronizeIndex() == WaypointIndexUpdateStatus.INDEX_COMMITTED && store.isIndexHealthy();
      }

      if (query.isEmpty()) {
         List<WaypointRecord> records = structuredRecords(authoritative, dimension, allowedTypes, includeStale);
         records.sort(Comparator.comparing(WaypointSearchService::stableId));
         return new WaypointSearchResult(take(records, limit), indexHealthy ? WaypointSearchStatus.INDEXED : WaypointSearchStatus.FULL_STORE_FALLBACK);
      }

      if (indexHealthy) {
         try {
            List<RetrievalHit> hits = store.queryIndex(query, authoritative.size());
            List<WaypointRecord> records = recordsForHits(authoritative, hits, dimension, allowedTypes, includeStale, limit);
            return new WaypointSearchResult(records, WaypointSearchStatus.INDEXED);
         } catch (Exception var10) {
         }
      }

      List<RetrievalHit> fallbackHits = rankInMemory(authoritative, query);
      List<WaypointRecord> fallbackRecords = recordsForHits(authoritative, fallbackHits, dimension, allowedTypes, includeStale, limit);
      return new WaypointSearchResult(fallbackRecords, WaypointSearchStatus.FULL_STORE_FALLBACK);
   }

   private static WaypointSearchResult nearest(
      List<WaypointRecord> authoritative, String query, String dimension, Set<String> allowedTypes, boolean includeStale, BlockPos origin, int limit
   ) {
      List<WaypointRecord> candidates = structuredRecords(authoritative, dimension, allowedTypes, includeStale);
      candidates.removeIf(record -> record.canonicalBlockPos() == null);
      if (!query.isEmpty()) {
         Set<String> eligibleIds = new HashSet<>();

         for (RetrievalHit hit : rankInMemory(candidates, query)) {
            if (hit.toolId() != null) {
               eligibleIds.add(hit.toolId());
            }
         }

         candidates.removeIf(record -> !eligibleIds.contains(record.id));
      }

      candidates.sort(
         Comparator.<WaypointRecord>comparingLong(record -> squaredDistanceSaturated(record, origin)).thenComparing(WaypointSearchService::stableId)
      );
      return new WaypointSearchResult(take(candidates, limit), WaypointSearchStatus.AUTHORITATIVE_SPATIAL);
   }

   private static List<WaypointRecord> recordsForHits(
      List<WaypointRecord> authoritative, List<RetrievalHit> hits, String dimension, Set<String> allowedTypes, boolean includeStale, int limit
   ) {
      if (limit != 0 && hits != null && !hits.isEmpty()) {
         Map<String, WaypointRecord> recordsById = new LinkedHashMap<>();

         for (WaypointRecord record : authoritative) {
            if (record != null && record.id != null) {
               recordsById.put(record.id, record);
            }
         }

         List<RetrievalHit> stableHits = new ArrayList<>(hits);
         stableHits.sort(Comparator.comparingDouble(RetrievalHit::score).reversed().thenComparing(hitx -> hitx.toolId() == null ? "" : hitx.toolId()));
         List<WaypointRecord> records = new ArrayList<>();
         Set<String> seen = new HashSet<>();

         for (RetrievalHit hit : stableHits) {
            String id = hit.toolId();
            if (id != null && seen.add(id)) {
               WaypointRecord record = recordsById.get(id);
               if (matchesStructuredFilters(record, dimension, allowedTypes, includeStale)) {
                  records.add(record);
                  if (records.size() >= limit) {
                     break;
                  }
               }
            }
         }

         return records;
      } else {
         return List.of();
      }
   }

   static List<RetrievalHit> rankInMemory(List<WaypointRecord> records, String query) {
      if (query != null && !query.isBlank() && !records.isEmpty()) {
         List<ToolDocument> documents = new ArrayList<>(records.size());

         for (WaypointRecord record : records) {
            if (record != null && record.id != null) {
               try {
                  documents.add(new WaypointDocument(record).toToolDocument());
               } catch (RuntimeException var7) {
               }
            }
         }

         if (documents.isEmpty()) {
            return List.of();
         }

         int fullCorpusLimit = documents.size();
         List<RetrievalHit> lexical = LexicalIndex.build(documents).query(query, fullCorpusLimit);
         List<RetrievalHit> minHash = MinHashIndex.build(documents).query(query, fullCorpusLimit);
         List<RetrievalHit> fused = new ArrayList<>(RrfFusion.fuse(lexical, minHash, fullCorpusLimit, 60.0));
         fused.sort(Comparator.comparingDouble(RetrievalHit::score).reversed().thenComparing(hit -> hit.toolId() == null ? "" : hit.toolId()));
         return fused;
      } else {
         return List.of();
      }
   }

   private static List<WaypointRecord> structuredRecords(List<WaypointRecord> authoritative, String dimension, Set<String> allowedTypes, boolean includeStale) {
      List<WaypointRecord> records = new ArrayList<>();

      for (WaypointRecord record : authoritative) {
         if (matchesStructuredFilters(record, dimension, allowedTypes, includeStale)) {
            records.add(record);
         }
      }

      return records;
   }

   private static boolean matchesStructuredFilters(WaypointRecord record, String dimension, Set<String> allowedTypes, boolean includeStale) {
      if (record == null || record.id == null) {
         return false;
      } else if (!allowedTypes.isEmpty() && !allowedTypes.contains(record.type)) {
         return false;
      } else {
         return !dimension.isEmpty() && !dimension.equals(record.dimension) ? false : includeStale || !record.stale;
      }
   }

   private static Set<String> normalizeTypes(Set<String> allowedTypes) {
      if (allowedTypes != null && !allowedTypes.isEmpty()) {
         Set<String> normalized = new HashSet<>();

         for (String type : allowedTypes) {
            if (type != null && !type.isBlank()) {
               normalized.add(type);
            }
         }

         return Set.copyOf(normalized);
      } else {
         return Set.of();
      }
   }

   private static List<WaypointRecord> take(List<WaypointRecord> records, int limit) {
      return limit > 0 && !records.isEmpty() ? new ArrayList<>(records.subList(0, Math.min(limit, records.size()))) : List.of();
   }

   private static long squaredDistanceSaturated(WaypointRecord record, BlockPos origin) {
      BlockPos pos = record.canonicalBlockPos();
      if (pos == null) {
         return Long.MAX_VALUE;
      }

      long dx = (long)pos.getX() - origin.getX();
      long dy = (long)pos.getY() - origin.getY();
      long dz = (long)pos.getZ() - origin.getZ();
      return saturatingAdd(saturatingAdd(saturatingSquare(dx), saturatingSquare(dy)), saturatingSquare(dz));
   }

   private static long saturatingSquare(long value) {
      return value <= 3037000499L && value >= -3037000499L ? value * value : Long.MAX_VALUE;
   }

   private static long saturatingAdd(long left, long right) {
      return left != Long.MAX_VALUE && right != Long.MAX_VALUE && Long.MAX_VALUE - left >= right ? left + right : Long.MAX_VALUE;
   }

   private static String stableId(WaypointRecord record) {
      return record != null && record.id != null ? record.id : "";
   }
}
