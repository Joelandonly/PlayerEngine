package com.player2.playerengine.retrieval.overlay;

import com.player2.playerengine.retrieval.ToolDocument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ToolOverlayMerger {
   private static final Logger LOGGER = LogManager.getLogger(ToolOverlayMerger.class);
   public static final int MAX_KEYWORDS_PER_TOOL = 200;
   public static final int MAX_EXAMPLES_PER_TOOL = 5;
   private static final Pattern ALLOWED_KEYWORD = Pattern.compile("[a-z0-9 \\-_']+");

   private ToolOverlayMerger() {
   }

   public static Collection<ToolDocument> merge(Collection<ToolDocument> baseline, List<ToolOverlay> overlays) {
      if (overlays != null && !overlays.isEmpty()) {
         Map<String, ToolOverlayMerger.WorkingDoc> working = new LinkedHashMap<>();

         for (ToolDocument doc : baseline) {
            working.put(doc.id(), new ToolOverlayMerger.WorkingDoc(doc));
         }

         for (ToolOverlay overlay : overlays) {
            if (overlay != null) {
               for (Entry<String, OverlayEntry> entry : overlay.safeTools().entrySet()) {
                  String toolId = entry.getKey();
                  OverlayEntry patch = entry.getValue();
                  if (patch != null) {
                     ToolOverlayMerger.WorkingDoc wd = working.get(toolId);
                     if (wd == null) {
                        LOGGER.warn("RAG overlay: unknown toolId '{}' — skipping.", toolId);
                     } else {
                        applyKeywords(wd, patch.safeKeywords(), toolId);
                        applyExamples(wd, patch.safeExamples(), toolId);
                     }
                  }
               }
            }
         }

         List<ToolDocument> result = new ArrayList<>(working.size());

         for (ToolOverlayMerger.WorkingDoc wd : working.values()) {
            result.add(wd.build());
         }

         return result;
      } else {
         return baseline;
      }
   }

   private static void applyKeywords(ToolOverlayMerger.WorkingDoc wd, List<String> additions, String toolId) {
      for (String raw : additions) {
         if (raw != null && !raw.isBlank()) {
            String lower = raw.trim().toLowerCase();
            if (!ALLOWED_KEYWORD.matcher(lower).matches()) {
               LOGGER.warn("RAG overlay: keyword '{}' for tool '{}' contains disallowed characters — skipping.", lower, toolId);
            } else {
               if (wd.keywords.size() >= 200) {
                  LOGGER.warn("RAG overlay: tool '{}' already has {} keywords (cap); further additions dropped.", toolId, 200);
                  break;
               }

               wd.keywords.add(lower);
            }
         }
      }
   }

   private static void applyExamples(ToolOverlayMerger.WorkingDoc wd, List<String> additions, String toolId) {
      int added = 0;

      for (String example : additions) {
         if (example != null && !example.isBlank()) {
            if (added >= 5) {
               LOGGER.warn("RAG overlay: tool '{}' has hit per-overlay example cap ({}); remaining additions dropped.", toolId, 5);
               break;
            }

            wd.examples.add(example.trim());
            added++;
         }
      }
   }

   private static final class WorkingDoc {
      final ToolDocument original;
      final Set<String> keywords;
      final List<String> examples;
      boolean dirty = false;

      WorkingDoc(ToolDocument doc) {
         this.original = doc;
         this.keywords = new LinkedHashSet<>();

         for (String k : doc.keywords()) {
            this.keywords.add(k.toLowerCase());
         }

         this.examples = new ArrayList<>(doc.examples());
      }

      ToolDocument build() {
         List<String> mergedKeywords = new ArrayList<>(this.keywords);
         return mergedKeywords.equals(lowerAll(this.original.keywords())) && this.examples.equals(this.original.examples())
            ? this.original
            : new ToolDocument(
               this.original.id(),
               this.original.name(),
               this.original.description(),
               this.original.whenToUse(),
               new ArrayList<>(this.examples),
               mergedKeywords,
               this.original.categoryTags()
            );
      }

      private static List<String> lowerAll(List<String> list) {
         List<String> out = new ArrayList<>(list.size());

         for (String s : list) {
            out.add(s.toLowerCase());
         }

         return out;
      }
   }
}
