package com.player2.playerengine.memory.resolution;

import java.util.Locale;
import java.util.Set;

public final class MentionNormalizer {
   public static final int MAX_HONORIFIC_STRIPS = 3;
   public static final Set<String> LEADING_STRIP_TOKENS = Set.of("the", "a", "an", "mr", "mrs", "ms", "sir", "lord", "lady", "old", "young", "my");

   private MentionNormalizer() {
   }

   public static String normalize(String mention) {
      if (mention == null) {
         return "";
      }

      String lower = mention.trim().toLowerCase(Locale.ROOT);
      if (lower.isEmpty()) {
         return "";
      }

      StringBuilder sb = new StringBuilder(lower.length());

      for (int i = 0; i < lower.length(); i++) {
         char c = lower.charAt(i);
         sb.append(Character.isLetterOrDigit(c) ? c : ' ');
      }

      String[] tokens = sb.toString().trim().split("\\s+");
      if (tokens.length != 0 && (tokens.length != 1 || !tokens[0].isEmpty())) {
         int start = 0;

         for (int strips = 0; start < tokens.length && strips < 3 && LEADING_STRIP_TOKENS.contains(tokens[start]); strips++) {
            start++;
         }

         if (start >= tokens.length) {
            start = tokens.length - 1;
         }

         StringBuilder out = new StringBuilder();

         for (int i = start; i < tokens.length; i++) {
            if (!tokens[i].isEmpty()) {
               if (out.length() > 0) {
                  out.append(' ');
               }

               out.append(tokens[i]);
            }
         }

         return out.toString();
      } else {
         return "";
      }
   }
}
