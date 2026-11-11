package com.player2.playerengine.agentic.elliegps;

import java.util.List;

public final class WaypointIngestionPrompt {
   static final int MAX_DESCRIPTION_CHARS = 200;

   private WaypointIngestionPrompt() {
   }

   public static String systemPrompt() {
      return "You write concise one-sentence descriptions for Minecraft storage chests. Reply with a single plain-text sentence, no markdown, no quotes, at most 200 characters. Describe what is stored and approximately where, using the information provided.";
   }

   public static String userPrompt(String snapshotSummary, List<String> keywords) {
      String kwLine = keywords != null && !keywords.isEmpty() ? String.join(", ", keywords.subList(0, Math.min(keywords.size(), 20))) : "(none)";
      return "Container summary: "
         + snapshotSummary
         + "\nKeywords: "
         + kwLine
         + "\nWrite a single concise sentence describing what is stored here and where it is. Keep it under 200 characters.";
   }
}
