package com.player2.playerengine.player2api;

import org.jetbrains.annotations.Nullable;

public final class CallByNameChatFilter {
   private CallByNameChatFilter() {
   }

   @Nullable
   public static Event.UserMessage filterForAutomaton(Event.UserMessage msg, Character character, boolean callByNameEnabled) {
      if (!callByNameEnabled) {
         return msg;
      }

      if (character == null) {
         return msg;
      }

      String trimmed = msg.message().trim();
      String stripped = stripMatchingPrefix(trimmed, character);
      return stripped == null ? null : msg.withMessage(stripped);
   }

   @Nullable
   private static String stripMatchingPrefix(String trimmed, Character character) {
      String fromName = tryStripPrefix(trimmed, character.name());
      if (fromName != null) {
         return fromName;
      }

      String shortName = character.shortName();
      return shortName != null && !shortName.isBlank() && !shortName.equalsIgnoreCase(character.name()) ? tryStripPrefix(trimmed, shortName) : null;
   }

   @Nullable
   private static String tryStripPrefix(String trimmed, String prefix) {
      if (prefix != null && !prefix.isBlank()) {
         if (trimmed.length() >= prefix.length() && trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String rest = trimmed.substring(prefix.length()).trim();

            while (!rest.isEmpty() && isHailSeparator(rest.charAt(0))) {
               rest = rest.substring(1).trim();
            }

            return rest.isEmpty() ? null : rest;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static boolean isHailSeparator(char c) {
      return c == ':' || c == ',' || c == ';' || java.lang.Character.isWhitespace(c);
   }
}
