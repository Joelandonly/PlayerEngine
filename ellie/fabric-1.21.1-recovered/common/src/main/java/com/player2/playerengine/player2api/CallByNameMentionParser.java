package com.player2.playerengine.player2api;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CallByNameMentionParser {
   private static final Pattern POSSESSIVE = Pattern.compile("(?iu)(^|\\b)(?<owner>[\\p{L}\\p{N}_-]{1,32})\\s*(?:'s|’s)\\s*(?<bot>[^\\s\"].*?)\\b");
   private static final Pattern AT_QUOTED = Pattern.compile("(?iu)@\\s*\"(?<name>[^\"]+)\"");
   private static final Pattern AT_BARE = Pattern.compile("(?iu)@\\s*(?<name>[^\\s,;:!?]+)");

   private CallByNameMentionParser() {
   }

   public static CallByNameMentionParser.MentionParseResult parse(String raw, Set<String> candidateBotKeys) {
      return parse(raw, candidateBotKeys, Set.of());
   }

   public static CallByNameMentionParser.MentionParseResult parse(String raw, Set<String> candidateBotKeys, Set<String> candidateOwnerKeys) {
      if (raw == null) {
         return new CallByNameMentionParser.MentionParseResult(List.of(), Optional.empty());
      }

      String normalizedRaw = Normalizer.normalize(raw, Form.NFKC);
      List<CallByNameMentionParser.MentionIntent> out = new ArrayList<>();
      Matcher m = AT_QUOTED.matcher(normalizedRaw);

      while (m.find()) {
         String name = m.group("name");
         if (name != null && !name.isBlank()) {
            out.add(new CallByNameMentionParser.MentionIntent.Unqualified(name));
         }
      }

      m = AT_BARE.matcher(normalizedRaw);

      while (m.find()) {
         String name = m.group("name");
         if (name != null && !name.isBlank()) {
            out.add(new CallByNameMentionParser.MentionIntent.Unqualified(name));
         }
      }

      m = POSSESSIVE.matcher(normalizedRaw);

      while (m.find()) {
         String owner = m.group("owner");
         String bot = m.group("bot");
         if (owner != null && bot != null) {
            bot = trimTrailingPunctuation(bot);
            if (!owner.isBlank() && !bot.isBlank()) {
               out.add(new CallByNameMentionParser.MentionIntent.Qualified(owner, bot));
            }
         }
      }

      if (candidateBotKeys != null && !candidateBotKeys.isEmpty()) {
         String lower = normalizedRaw.toLowerCase(Locale.ROOT);

         for (String botKey : candidateBotKeys) {
            if (botKey != null && !botKey.isBlank() && containsNameWithBoundaries(lower, botKey)) {
               out.add(new CallByNameMentionParser.MentionIntent.Unqualified(botKey));
            }
         }
      }

      Optional<Integer> strip = computeLeadingStripLength(normalizedRaw, out);
      return new CallByNameMentionParser.MentionParseResult(out, strip);
   }

   private static Optional<Integer> computeLeadingStripLength(String raw, List<CallByNameMentionParser.MentionIntent> intents) {
      if (raw != null && !raw.isBlank() && intents != null && !intents.isEmpty()) {
         Matcher m = AT_QUOTED.matcher(raw);
         if (m.find() && m.start() == 0) {
            return Optional.of(m.end());
         }

         m = AT_BARE.matcher(raw);
         if (m.find() && m.start() == 0) {
            return Optional.of(m.end());
         }

         int firstSep = indexOfFirst(raw, ':', ',', ';');
         if (firstSep > 0) {
            String possibleName = raw.substring(0, firstSep).trim();
            if (!possibleName.isBlank()) {
               String possibleKey = normalizeKey(possibleName);

               for (CallByNameMentionParser.MentionIntent i : intents) {
                  if (i instanceof CallByNameMentionParser.MentionIntent.Unqualified u && Objects.equals(normalizeKey(u.bot()), possibleKey)) {
                     return Optional.of(firstSep + 1);
                  }
               }
            }
         }

         return Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   private static int indexOfFirst(String s, char... chars) {
      int best = -1;

      for (char c : chars) {
         int idx = s.indexOf(c);
         if (idx >= 0 && (best < 0 || idx < best)) {
            best = idx;
         }
      }

      return best;
   }

   private static String trimTrailingPunctuation(String s) {
      if (s == null) {
         return "";
      }

      String t = s.trim();

      int end;
      for (end = t.length(); end > 0; end--) {
         char c = t.charAt(end - 1);
         if (c != ',' && c != ':' && c != ';' && c != '!' && c != '?' && c != '.' && !java.lang.Character.isWhitespace(c)) {
            break;
         }
      }

      return t.substring(0, end).trim();
   }

   static boolean containsNameWithBoundaries(String lowerMsg, String lowerNeedle) {
      if (lowerMsg != null && lowerNeedle != null && !lowerNeedle.isBlank()) {
         int from = 0;

         while (true) {
            int idx = lowerMsg.indexOf(lowerNeedle, from);
            if (idx < 0) {
               return false;
            }

            int end = idx + lowerNeedle.length();
            if (isBoundary(lowerMsg, idx - 1) && isBoundary(lowerMsg, end)) {
               return true;
            }

            from = idx + 1;
         }
      } else {
         return false;
      }
   }

   private static boolean isBoundary(String s, int idx) {
      if (idx >= 0 && idx < s.length()) {
         char c = s.charAt(idx);
         return !java.lang.Character.isLetterOrDigit(c);
      } else {
         return true;
      }
   }

   static String normalizeKey(String s) {
      if (s == null) {
         return null;
      }

      String trimmed = s.trim();
      return trimmed.isEmpty() ? null : Normalizer.normalize(trimmed, Form.NFKC).toLowerCase(Locale.ROOT);
   }

   public sealed interface MentionIntent permits CallByNameMentionParser.MentionIntent.Qualified, CallByNameMentionParser.MentionIntent.Unqualified {
      record Qualified(String owner, String bot) implements CallByNameMentionParser.MentionIntent {
      }

      record Unqualified(String bot) implements CallByNameMentionParser.MentionIntent {
      }
   }

   public record MentionParseResult(List<CallByNameMentionParser.MentionIntent> intents, Optional<Integer> stripLeadingAddressing) {
   }
}
