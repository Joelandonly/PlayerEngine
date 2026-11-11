package com.player2.playerengine.memory.ingest;

import com.player2.playerengine.memory.MemoryGraph;
import com.player2.playerengine.memory.MemoryNode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class MemoryExtractionGate {
   private static final Set<String> JUNK_PHRASES = new HashSet<>();
   private static final Set<String> SENTENCE_INITIAL_COMMON = new HashSet<>();

   private MemoryExtractionGate() {
   }

   public static boolean isEligible(String turnText, int lengthThreshold, MemoryGraph graph) {
      if (turnText == null) {
         return false;
      } else {
         String trimmed = turnText.trim();
         if (trimmed.isEmpty()) {
            return false;
         } else {
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (JUNK_PHRASES.contains(stripTrailingPunct(lower))) {
               return false;
            } else if (trimmed.length() >= Math.max(1, lengthThreshold)) {
               return true;
            } else {
               return mentionsKnownEntity(lower, graph) ? true : containsProperNoun(trimmed);
            }
         }
      }
   }

   private static boolean mentionsKnownEntity(String lowerText, MemoryGraph graph) {
      if (graph != null && graph.nodeCount() != 0) {
         for (MemoryNode node : graph.nodes()) {
            if (containsName(lowerText, node.canonicalName())) {
               return true;
            }

            for (String alias : node.aliases()) {
               if (containsName(lowerText, alias)) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean containsName(String lowerText, String name) {
      if (name == null) {
         return false;
      }

      String n = name.trim().toLowerCase(Locale.ROOT);
      return n.length() < 3 ? false : lowerText.contains(n);
   }

   private static boolean containsProperNoun(String text) {
      String[] words = text.split("\\s+");

      for (int i = 0; i < words.length; i++) {
         String w = stripPunct(words[i]);
         if (w.length() >= 2) {
            char c0 = w.charAt(0);
            if (Character.isUpperCase(c0) && (i != 0 || !SENTENCE_INITIAL_COMMON.contains(w))) {
               boolean restLower = true;

               for (int k = 1; k < w.length(); k++) {
                  if (Character.isUpperCase(w.charAt(k))) {
                     restLower = false;
                     break;
                  }
               }

               if (restLower) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static String stripPunct(String w) {
      return w.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
   }

   private static String stripTrailingPunct(String s) {
      return s.replaceAll("[\\p{Punct}\\s]+$", "");
   }

   static {
      JUNK_PHRASES.add("ok");
      JUNK_PHRASES.add("okay");
      JUNK_PHRASES.add("k");
      JUNK_PHRASES.add("yes");
      JUNK_PHRASES.add("no");
      JUNK_PHRASES.add("yep");
      JUNK_PHRASES.add("nope");
      JUNK_PHRASES.add("sure");
      JUNK_PHRASES.add("thanks");
      JUNK_PHRASES.add("thank you");
      JUNK_PHRASES.add("stop");
      JUNK_PHRASES.add("wait");
      JUNK_PHRASES.add("go");
      JUNK_PHRASES.add("go left");
      JUNK_PHRASES.add("go right");
      JUNK_PHRASES.add("come here");
      JUNK_PHRASES.add("follow me");
      JUNK_PHRASES.add("follow");
      JUNK_PHRASES.add("stay");
      JUNK_PHRASES.add("hi");
      JUNK_PHRASES.add("hey");
      JUNK_PHRASES.add("hello");

      for (String w : new String[]{
         "The",
         "A",
         "An",
         "I",
         "You",
         "We",
         "He",
         "She",
         "It",
         "They",
         "This",
         "That",
         "These",
         "Those",
         "My",
         "Your",
         "Our",
         "Their",
         "Let",
         "Go",
         "Come",
         "Can",
         "Will",
         "Do",
         "Did",
         "Is",
         "Are",
         "What",
         "Where",
         "When",
         "Why",
         "How",
         "Who",
         "Yes",
         "No",
         "Ok",
         "Okay",
         "Please",
         "Hey",
         "Hi",
         "Hello",
         "Thanks",
         "Thank",
         "Stop",
         "Wait",
         "Now"
      }) {
         SENTENCE_INITIAL_COMMON.add(w);
      }
   }
}
