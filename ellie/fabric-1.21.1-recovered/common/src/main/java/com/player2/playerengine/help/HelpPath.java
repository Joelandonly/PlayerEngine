package com.player2.playerengine.help;

import java.util.List;

public final class HelpPath {
   private HelpPath() {
   }

   public static String of(String... segments) {
      if (segments == null) {
         return "";
      }

      StringBuilder sb = new StringBuilder();

      for (String seg : segments) {
         if (seg != null) {
            String trimmed = seg.trim();
            if (!trimmed.isEmpty()) {
               if (sb.length() > 0) {
                  sb.append(' ');
               }

               sb.append(trimmed);
            }
         }
      }

      return sb.toString();
   }

   public static String of(List<String> segments) {
      return segments == null ? "" : of(segments.toArray(new String[0]));
   }

   public static String normalize(String raw) {
      return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
   }

   public static String dashed(String path) {
      return normalize(path).replace(' ', '-');
   }
}
