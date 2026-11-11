package com.player2.playerengine.player2api;

import com.player2.playerengine.tasks.movement.BodyLanguageTask;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkerParser {
   private static final Pattern MARKER = Pattern.compile("\\[bl:([a-z_]+)\\]");

   private MarkerParser() {
   }

   public static MarkerParser.ParsedMessage parse(String rawMessage) {
      if (rawMessage == null) {
         rawMessage = "";
      }

      List<String> chunks = new ArrayList<>();
      List<MarkerParser.SegmentBoundary> boundaries = new ArrayList<>();
      Matcher m = MARKER.matcher(rawMessage);

      int lastEnd;
      for (lastEnd = 0; m.find(); lastEnd = m.end()) {
         String chunkText = rawMessage.substring(lastEnd, m.start());
         chunks.add(chunkText);
         int chunkIndexAfter = chunks.size() - 1;
         String rawToken = m.group(1);
         BodyLanguageTask.Type action = mapToken(rawToken);
         boundaries.add(new MarkerParser.SegmentBoundary(chunkIndexAfter, action, action != null, rawToken));
      }

      chunks.add(rawMessage.substring(lastEnd));
      List<String> normalizedChunks = new ArrayList<>(chunks.size());

      for (String c : chunks) {
         normalizedChunks.add(normalize(c));
      }

      StringBuilder stripped = new StringBuilder();

      for (String c : normalizedChunks) {
         if (!c.isEmpty()) {
            if (stripped.length() > 0) {
               stripped.append(' ');
            }

            stripped.append(c);
         }
      }

      return new MarkerParser.ParsedMessage(stripped.toString(), normalizedChunks, boundaries);
   }

   private static BodyLanguageTask.Type mapToken(String rawToken) {
      if (rawToken == null) {
         return null;
      }

      try {
         return BodyLanguageTask.Type.valueOf(rawToken.toUpperCase());
      } catch (IllegalArgumentException e) {
         return null;
      }
   }

   private static String normalize(String s) {
      return s == null ? "" : s.replaceAll("\\s+", " ").trim();
   }

   public record ParsedMessage(String strippedText, List<String> chunks, List<MarkerParser.SegmentBoundary> boundaries) {
   }

   public record SegmentBoundary(int chunkIndexAfter, BodyLanguageTask.Type action, boolean valid, String rawToken) {
   }
}
