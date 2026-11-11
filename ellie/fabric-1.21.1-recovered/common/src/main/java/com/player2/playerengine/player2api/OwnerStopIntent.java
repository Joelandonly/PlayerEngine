package com.player2.playerengine.player2api;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class OwnerStopIntent {
   private OwnerStopIntent() {
   }

   public static boolean matches(String rawMessage, Character character) {
      if (rawMessage != null && character != null) {
         String message = normalize(rawMessage);
         if (message.isEmpty()) {
            return false;
         }

         Set<String> names = new LinkedHashSet<>();
         addName(names, character.name());
         addName(names, character.shortName());

         for (String name : names) {
            if (message.equals("stop " + name) || message.equals(name + " stop")) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static void addName(Set<String> names, String rawName) {
      String normalized = normalize(rawName);
      if (!normalized.isEmpty()) {
         names.add(normalized);
      }
   }

   private static String normalize(String text) {
      return text == null
         ? ""
         : Normalizer.normalize(text, Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_-]+", " ").trim().replaceAll("\\s+", " ");
   }
}
