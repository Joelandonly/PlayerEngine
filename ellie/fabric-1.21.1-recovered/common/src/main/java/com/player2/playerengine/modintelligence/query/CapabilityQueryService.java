package com.player2.playerengine.modintelligence.query;

import com.player2.playerengine.modintelligence.ModIntelligenceService;
import com.player2.playerengine.modintelligence.ModIntelligenceStatus;
import com.player2.playerengine.modintelligence.capability.CapabilityDecl;
import com.player2.playerengine.modintelligence.capability.CapabilityEvidence;
import com.player2.playerengine.modintelligence.capability.CapabilityMap;
import com.player2.playerengine.modintelligence.capability.CapabilityStatus;
import com.player2.playerengine.modintelligence.capability.CapabilitySubjectKind;
import com.player2.playerengine.modintelligence.ingest.CapabilityStore;
import com.player2.playerengine.retrieval.RetrievalHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CapabilityQueryService {
   private final CapabilityStore store;

   public CapabilityQueryService(CapabilityStore store) {
      this.store = store;
   }

   public List<CapabilityHit> query(CapabilityQuery query, int topK) {
      Map<String, CapabilityMap> byDocumentId = new HashMap<>();
      synchronized (this.store) {
         for (CapabilityMap map : this.store.getActiveMaps().values()) {
            byDocumentId.put(map.documentId(), map);
         }
      }

      List<RetrievalHit> raw = CapabilityIndex.getActive().search(query.getText(), topK * 3);
      List<CapabilityHit> hits = new ArrayList<>();

      for (RetrievalHit rh : raw) {
         CapabilityMap map = byDocumentId.get(rh.toolId());
         if (map != null && passesFilters(map, query)) {
            hits.add(toHit(map, rh.score(), query));
            if (hits.size() >= topK) {
               break;
            }
         }
      }

      return hits;
   }

   public Optional<CapabilityMap> get(CapabilitySubjectKind kind, String subjectId) {
      synchronized (this.store) {
         return Optional.ofNullable(this.store.get(kind.name() + "|" + subjectId));
      }
   }

   public ModIntelligenceStatus status() {
      return ModIntelligenceService.statusFromStore(this.store);
   }

   private static boolean passesFilters(CapabilityMap map, CapabilityQuery query) {
      if (!query.getSubjectKinds().contains(map.getSubjectKind())) {
         return false;
      }

      if (!query.isIncludeTombstoned() && map.getStatus() == CapabilityStatus.TOMBSTONED) {
         return false;
      }

      if (!query.isIncludeUnknown() && map.getStatus() == CapabilityStatus.UNKNOWN) {
         return false;
      }

      if (!query.getAllowedStatuses().contains(map.getStatus())) {
         return false;
      }

      if (map.bestCapabilityConfidence() < query.getMinConfidence()) {
         return false;
      }

      if (query.isRequireDeterministicEvidence() && !map.hasDeterministicEvidence()) {
         return false;
      }

      List<String> capIds = map.getCapabilities().stream().map(CapabilityDecl::getId).toList();

      for (String req : query.getRequiredCapabilities()) {
         if (!capIds.contains(req)) {
            return false;
         }
      }

      for (String ex : query.getExcludedCapabilities()) {
         if (capIds.contains(ex)) {
            return false;
         }
      }

      return true;
   }

   private static CapabilityHit toHit(CapabilityMap map, double score, CapabilityQuery query) {
      List<String> matchedCaps = map.getCapabilities()
         .stream()
         .filter(c -> c.getConfidence() >= query.getMinConfidence())
         .map(CapabilityDecl::getId)
         .collect(Collectors.toList());
      List<CapabilityEvidence> evidence = map.getCapabilities()
         .stream()
         .flatMap(c -> c.getEvidence().stream())
         .sorted(Comparator.comparingDouble(CapabilityEvidence::getConfidence).reversed())
         .limit(5L)
         .collect(Collectors.toList());
      List<String> aliases = map.getEnrichment() == null ? List.of() : map.getEnrichment().getAliases();
      return new CapabilityHit(
         map.documentId(),
         map.getSubjectId(),
         map.getSubjectKind(),
         score,
         map.bestCapabilityConfidence(),
         matchedCaps,
         aliases,
         map.getStatus(),
         map.hasDeterministicEvidence(),
         evidence,
         map.getWarnings(),
         map.getSourceModId(),
         map.getEntryFingerprint()
      );
   }
}
