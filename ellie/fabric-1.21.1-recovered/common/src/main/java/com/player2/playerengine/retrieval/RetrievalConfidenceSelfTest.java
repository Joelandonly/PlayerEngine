package com.player2.playerengine.retrieval;

import com.player2.playerengine.retrieval.overlay.ToolOverlayMerger;
import java.util.List;

public final class RetrievalConfidenceSelfTest {
   private RetrievalConfidenceSelfTest() {
   }

   public static boolean runAll() {
      ToolMetadataRegistry registry = ToolMetadataRegistry.create(ToolOverlayMerger.merge(SeedToolMetadata.all(), List.of()));
      ToolRetriever retriever = ToolRetriever.buildInMemory(registry);
      RetrievalConfidenceThresholds thresholds = RetrievalConfidenceThresholds.defaults();
      RetrievalResult empty = retriever.retrieveWithConfidence("xyzzy_nonexistent_q", 5, null, thresholds);
      if (empty.confidence().empty() && empty.confidence().weak()) {
         RetrievalResult strong = retriever.retrieveWithConfidence("get wood logs", 5, null, thresholds);
         if (!strong.confidence().empty() && !strong.hits().isEmpty()) {
            RetrievalConfidenceThresholds strict = new RetrievalConfidenceThresholds(1.0, 1.0, 1.0, true);
            RetrievalResult lowScore = retriever.retrieveWithConfidence("get wood", 5, null, strict);
            return lowScore.confidence().weak();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
