package com.player2.playerengine.memory.retrieval;

import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import com.player2.playerengine.memory.MemoryNodeType;
import com.player2.playerengine.retrieval.RetrievalHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MemoryBlockSerializer {
   public static final int DEFAULT_BLOCK_CHAR_CAP = 1600;
   static final int LINE_CHAR_CAP = 352;
   private static final String HEADER = "[Memory]";
   public static final String HAS_MEMORY_FOOTER = "ALWAYS carry out whatever the player just asked or told you — instructions, tasks, and new facts are never filtered through this memory list. A statement like \"today we are testing the hunger system\" or \"go mining\" is an instruction or new information: act on it or acknowledge it directly, never treat it as a memory test and never deflect, ignore, or change the subject. The list above is relevant ONLY if the player EXPLICITLY ASKS whether you remember a specific shared past event (\"do you remember when we...\"). ONLY in that narrow case: if the event is not listed, say honestly in your own voice that you do not recall it — never invent or play along with an event that is not here.";
   public static final String NO_MEMORY_NOTE = "[Memory] ALWAYS act on whatever the player just said — instructions and new facts require no memory match. This note fires ONLY because the player appears to be asking about a specific shared past event that is not in your memory store. In that case only: say honestly, in your own in-character voice, that you do not recall that event — never invent or play along with a fabricated shared memory. Then engage with whatever else they said or asked; never ignore the player, change the subject, or greet them again instead of replying.";

   private MemoryBlockSerializer() {
   }

   public static Optional<String> serialize(MemoryRetrievalConfidence.BoundaryVerdict verdict, List<RetrievalHit> hits, MemoryGraph graph, int blockCharCap) {
      switch (verdict) {
         case STORE_ABSENT:
            return Optional.empty();
         case NO_MEMORY:
            return Optional.of(
               "[Memory] ALWAYS act on whatever the player just said — instructions and new facts require no memory match. This note fires ONLY because the player appears to be asking about a specific shared past event that is not in your memory store. In that case only: say honestly, in your own in-character voice, that you do not recall that event — never invent or play along with a fabricated shared memory. Then engage with whatever else they said or asked; never ignore the player, change the subject, or greet them again instead of replying."
            );
         case HAS_MEMORY:
         default:
            String block = buildHasMemory(hits, graph, blockCharCap);
            return block.isEmpty() ? Optional.empty() : Optional.of(block);
      }
   }

   private static String buildHasMemory(List<RetrievalHit> hits, MemoryGraph graph, int blockCharCap) {
      int cap = blockCharCap > 0 ? blockCharCap : 1600;
      List<String> lines = new ArrayList<>();
      if (hits != null && graph != null) {
         for (RetrievalHit hit : hits) {
            MemoryNode node = graph.node(hit.id());
            if (node != null && isRecallRenderable(node)) {
               String line = renderLine(node);
               if (!line.isEmpty()) {
                  lines.add(line);
               }
            }
         }
      }

      if (lines.isEmpty()) {
         return "";
      }

      int footerReserve = "ALWAYS carry out whatever the player just asked or told you — instructions, tasks, and new facts are never filtered through this memory list. A statement like \"today we are testing the hunger system\" or \"go mining\" is an instruction or new information: act on it or acknowledge it directly, never treat it as a memory test and never deflect, ignore, or change the subject. The list above is relevant ONLY if the player EXPLICITLY ASKS whether you remember a specific shared past event (\"do you remember when we...\"). ONLY in that narrow case: if the event is not listed, say honestly in your own voice that you do not recall it — never invent or play along with an event that is not here."
            .length()
         + 2;
      int bodyCap = Math.max("[Memory]".length(), cap - footerReserve);
      StringBuilder sb = new StringBuilder(Math.min(cap, 2048));
      sb.append("[Memory]");

      for (String line : lines) {
         int projected = sb.length() + 1 + line.length();
         if (projected > bodyCap) {
            break;
         }

         sb.append('\n').append(line);
      }

      sb.append("\n\n")
         .append(
            "ALWAYS carry out whatever the player just asked or told you — instructions, tasks, and new facts are never filtered through this memory list. A statement like \"today we are testing the hunger system\" or \"go mining\" is an instruction or new information: act on it or acknowledge it directly, never treat it as a memory test and never deflect, ignore, or change the subject. The list above is relevant ONLY if the player EXPLICITLY ASKS whether you remember a specific shared past event (\"do you remember when we...\"). ONLY in that narrow case: if the event is not listed, say honestly in your own voice that you do not recall it — never invent or play along with an event that is not here."
         );
      return MemoryCaps.cap(sb.toString(), cap);
   }

   private static boolean isRecallRenderable(MemoryNode node) {
      MemoryNodeType type = MemoryNodeType.fromWire(node.type());
      return type == MemoryNodeType.EVENT || type == MemoryNodeType.FACT || type == MemoryNodeType.PREFERENCE;
   }

   private static String renderLine(MemoryNode node) {
      String name = MemoryCaps.capName(safe(node.canonicalName()));
      String content = MemoryCaps.capContent(safe(node.content()));
      StringBuilder line = new StringBuilder();
      line.append("- ");
      if (!name.isEmpty()) {
         line.append(name);
         if (!content.isEmpty()) {
            line.append(": ");
         }
      }

      line.append(content);
      String rendered = line.toString().trim();
      return rendered.equals("-") ? "" : MemoryCaps.cap(rendered, 352);
   }

   private static String safe(String s) {
      return s == null ? "" : s;
   }
}
