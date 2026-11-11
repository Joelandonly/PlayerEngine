package com.player2.playerengine.player2api;

import com.player2.playerengine.player2api.status.StatusUtils;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.jetbrains.annotations.Nullable;

public final class CallByNameMentionRouter {
   private CallByNameMentionRouter() {
   }

   public static CallByNameMentionRouter.ResolvedTargets resolveTargets(Event.UserMessage msg, String speakerUsername, List<AgentConversationData> candidates) {
      if (msg != null && candidates != null && !candidates.isEmpty()) {
         String raw = msg.message();
         if (raw != null && !raw.isBlank()) {
            Map<String, List<AgentConversationData>> byBotKey = new HashMap<>();
            Map<String, List<AgentConversationData>> byOwnerKey = new HashMap<>();

            for (AgentConversationData d : candidates) {
               if (d != null) {
                  Character ch = d.getCharacter();
                  if (ch != null) {
                     addKey(byBotKey, normalizeKey(ch.name()), d);
                     addKey(byBotKey, normalizeKey(ch.shortName()), d);
                     addKey(byOwnerKey, normalizeKey(d.getMod().getOwnerUsername()), d);
                  }
               }
            }

            CallByNameMentionParser.MentionParseResult parsed = CallByNameMentionParser.parse(raw, byBotKey.keySet(), byOwnerKey.keySet());
            if (parsed.intents().isEmpty()) {
               Optional<CallByNameMentionRouter.FuzzyAddressMatch> fuzzy = resolveUniqueFuzzyLeadingAddress(raw, byBotKey);
               if (fuzzy.isPresent()) {
                  CallByNameMentionRouter.FuzzyAddressMatch match = fuzzy.get();
                  String cleaned = stripLeadingAddressing(raw, match.prefixLen()).orElse(match.canonicalKey());
                  Event.UserMessage cleanedMsg = msg.withMessage(cleaned);
                  return new CallByNameMentionRouter.ResolvedTargets(Set.of(match.target()), cleanedMsg);
               } else {
                  return new CallByNameMentionRouter.ResolvedTargets(Collections.emptySet(), null);
               }
            } else {
               Set<AgentConversationData> out = new HashSet<>();

               for (CallByNameMentionParser.MentionIntent intent : parsed.intents()) {
                  if (intent instanceof CallByNameMentionParser.MentionIntent.Qualified q) {
                     String ownerKey = normalizeKey(q.owner());
                     String botKey = normalizeKey(q.bot());
                     if (ownerKey != null && botKey != null) {
                        for (AgentConversationData d : byOwnerKey.getOrDefault(ownerKey, List.of())) {
                           if (d != null && candidateMatchesBotKey(d, botKey)) {
                              out.add(d);
                           }
                        }
                     }
                  } else if (intent instanceof CallByNameMentionParser.MentionIntent.Unqualified u) {
                     String botKey = normalizeKey(u.bot());
                     if (botKey != null) {
                        List<AgentConversationData> matches = byBotKey.getOrDefault(botKey, List.of());
                        if (!matches.isEmpty()) {
                           List<AgentConversationData> owned = new ArrayList<>();

                           for (AgentConversationData d : matches) {
                              if (d != null && ownerEquals(d, speakerUsername)) {
                                 owned.add(d);
                              }
                           }

                           if (!owned.isEmpty()) {
                              out.add(pickClosestStable(owned, speakerUsername));
                           } else {
                              out.add(pickClosestStable(matches, speakerUsername));
                           }
                        }
                     }
                  }
               }

               if (out.isEmpty()) {
                  return new CallByNameMentionRouter.ResolvedTargets(Collections.emptySet(), null);
               } else {
                  String cleaned = parsed.stripLeadingAddressing().flatMap(prefixLen -> stripLeadingAddressing(raw, prefixLen)).orElse(raw);
                  if (cleaned != null && !cleaned.isBlank()) {
                     Event.UserMessage cleanedMsg = cleaned.equals(raw) ? msg : msg.withMessage(cleaned);
                     return new CallByNameMentionRouter.ResolvedTargets(out, cleanedMsg);
                  } else {
                     return new CallByNameMentionRouter.ResolvedTargets(Collections.emptySet(), null);
                  }
               }
            }
         } else {
            return new CallByNameMentionRouter.ResolvedTargets(Collections.emptySet(), null);
         }
      } else {
         return new CallByNameMentionRouter.ResolvedTargets(Collections.emptySet(), null);
      }
   }

   private static boolean ownerEquals(AgentConversationData d, String speakerUsername) {
      String owner = d.getMod().getOwnerUsername();
      return owner != null && speakerUsername != null ? owner.equalsIgnoreCase(speakerUsername) : false;
   }

   private static boolean candidateMatchesBotKey(AgentConversationData d, String botKey) {
      if (d != null && botKey != null) {
         Character ch = d.getCharacter();
         return ch == null ? false : Objects.equals(normalizeKey(ch.name()), botKey) || Objects.equals(normalizeKey(ch.shortName()), botKey);
      } else {
         return false;
      }
   }

   private static AgentConversationData pickClosestStable(List<AgentConversationData> candidates, String speakerUsername) {
      if (candidates != null && !candidates.isEmpty()) {
         List<AgentConversationData> copy = new ArrayList<>(candidates);
         copy.sort(Comparator.<AgentConversationData>comparingDouble(d -> safeDistanceToSpeaker(d, speakerUsername)).thenComparing(d -> safeUuid(d)));
         return copy.get(0);
      } else {
         return null;
      }
   }

   private static double safeDistanceToSpeaker(AgentConversationData d, String speakerUsername) {
      try {
         return d != null && speakerUsername != null ? StatusUtils.getDistanceToUsername(d.getMod(), speakerUsername) : Double.POSITIVE_INFINITY;
      } catch (Exception e) {
         return Double.POSITIVE_INFINITY;
      }
   }

   private static UUID safeUuid(AgentConversationData d) {
      try {
         return d.getUUID();
      } catch (Exception e) {
         return new UUID(0L, 0L);
      }
   }

   private static Optional<String> stripLeadingAddressing(String raw, int prefixLen) {
      if (raw == null) {
         return Optional.empty();
      }

      if (prefixLen > 0 && prefixLen <= raw.length()) {
         String rest = raw.substring(prefixLen).trim();

         while (!rest.isEmpty() && isHailSeparator(rest.charAt(0))) {
            rest = rest.substring(1).trim();
         }

         return rest.isBlank() ? Optional.empty() : Optional.of(rest);
      } else {
         return Optional.empty();
      }
   }

   private static boolean isHailSeparator(char c) {
      return c == ':' || c == ',' || c == ';' || java.lang.Character.isWhitespace(c);
   }

   private static Optional<CallByNameMentionRouter.FuzzyAddressMatch> resolveUniqueFuzzyLeadingAddress(
      String raw, Map<String, List<AgentConversationData>> byBotKey
   ) {
      Optional<CallByNameMentionRouter.LeadingAddress> leading = leadingAddress(raw);
      if (!leading.isEmpty() && byBotKey != null && !byBotKey.isEmpty()) {
         Map<UUID, CallByNameMentionRouter.FuzzyAddressMatch> matchesByBot = new HashMap<>();

         for (Entry<String, List<AgentConversationData>> entry : byBotKey.entrySet()) {
            String key = entry.getKey();
            if (looksLikeLeadingSttAddress(leading.get().text(), key)) {
               for (AgentConversationData data : entry.getValue()) {
                  if (data != null) {
                     UUID uuid = safeUuid(data);
                     CallByNameMentionRouter.FuzzyAddressMatch existing = matchesByBot.get(uuid);
                     String canonical = existing == null ? key : shortestKey(existing.canonicalKey(), key);
                     matchesByBot.put(uuid, new CallByNameMentionRouter.FuzzyAddressMatch(data, canonical, leading.get().prefixLen()));
                  }
               }
            }
         }

         return matchesByBot.size() != 1 ? Optional.empty() : Optional.of(matchesByBot.values().iterator().next());
      } else {
         return Optional.empty();
      }
   }

   private static String shortestKey(String a, String b) {
      if (a == null || a.isBlank()) {
         return b;
      } else if (b != null && !b.isBlank()) {
         return compactAlnum(b).length() < compactAlnum(a).length() ? b : a;
      } else {
         return a;
      }
   }

   private static Optional<CallByNameMentionRouter.LeadingAddress> leadingAddress(String raw) {
      if (raw == null) {
         return Optional.empty();
      }

      int leadingOffset = 0;

      while (leadingOffset < raw.length() && java.lang.Character.isWhitespace(raw.charAt(leadingOffset))) {
         leadingOffset++;
      }

      String trimmed = Normalizer.normalize(raw.substring(leadingOffset).trim(), Form.NFKC);
      if (trimmed.isEmpty()) {
         return Optional.empty();
      }

      int punct = indexOfFirst(trimmed, '.', ':', ',', ';', '!', '?');
      String head = punct >= 0 ? trimmed.substring(0, punct).trim() : trimmed;
      int prefixLen = leadingOffset + (punct >= 0 ? punct + 1 : firstTokenLength(trimmed));
      if (head.isEmpty()) {
         return Optional.empty();
      }

      String[] parts = head.split("\\s+");
      if (parts.length == 0) {
         return Optional.empty();
      }

      if (compactAlnum(parts[0]).length() != 1) {
         return Optional.of(new CallByNameMentionRouter.LeadingAddress(parts[0], prefixLen));
      }

      StringBuilder spelled = new StringBuilder();
      int count = 0;
      int spelledPrefixLen = 0;
      int cursor = 0;

      for (String part : parts) {
         String compact = compactAlnum(part);
         if (compact.length() != 1) {
            break;
         }

         int partStart = head.indexOf(part, cursor);
         if (partStart < 0) {
            break;
         }

         spelledPrefixLen = partStart + part.length();
         cursor = spelledPrefixLen;
         if (spelled.length() > 0) {
            spelled.append(' ');
         }

         spelled.append(part);
         if (++count >= 8) {
            break;
         }
      }

      int consumedPrefixLen = punct >= 0 ? prefixLen : leadingOffset + spelledPrefixLen;
      return count >= 4 ? Optional.of(new CallByNameMentionRouter.LeadingAddress(spelled.toString(), consumedPrefixLen)) : Optional.empty();
   }

   private static int firstTokenLength(String text) {
      int i = 0;

      while (i < text.length() && !java.lang.Character.isWhitespace(text.charAt(i))) {
         i++;
      }

      return i;
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

   private static boolean looksLikeLeadingSttAddress(String leading, String botKey) {
      String spoken = compactAlnum(leading);
      String target = compactAlnum(botKey);
      if (spoken.length() < 4 || target.length() < 4) {
         return false;
      } else if (spoken.equals(target) || target.startsWith(spoken)) {
         return true;
      } else {
         return spoken.length() >= 5 && target.endsWith(spoken) && target.length() - spoken.length() == 1
            ? true
            : isSpelledLetterRun(leading) && isSubsequence(spoken, target);
      }
   }

   private static boolean isSpelledLetterRun(String text) {
      if (text == null) {
         return false;
      }

      String[] parts = text.trim().split("\\s+");
      if (parts.length < 4) {
         return false;
      }

      for (String part : parts) {
         if (compactAlnum(part).length() != 1) {
            return false;
         }
      }

      return true;
   }

   private static String compactAlnum(String text) {
      if (text == null) {
         return "";
      }

      String normalized = Normalizer.normalize(text, Form.NFD).toLowerCase(Locale.ROOT);
      StringBuilder out = new StringBuilder(normalized.length());

      for (int i = 0; i < normalized.length(); i++) {
         char c = normalized.charAt(i);
         if (java.lang.Character.getType(c) != 6 && java.lang.Character.isLetterOrDigit(c)) {
            out.append(c);
         }
      }

      return out.toString();
   }

   private static boolean isSubsequence(String needle, String haystack) {
      int pos = 0;

      for (int i = 0; i < haystack.length() && pos < needle.length(); i++) {
         if (needle.charAt(pos) == haystack.charAt(i)) {
            pos++;
         }
      }

      return pos == needle.length();
   }

   private static void addKey(Map<String, List<AgentConversationData>> map, @Nullable String key, AgentConversationData d) {
      if (key != null && !key.isBlank() && d != null) {
         map.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
      }
   }

   @Nullable
   private static String normalizeKey(@Nullable String s) {
      return CallByNameMentionParser.normalizeKey(s);
   }

   private record FuzzyAddressMatch(AgentConversationData target, String canonicalKey, int prefixLen) {
   }

   private record LeadingAddress(String text, int prefixLen) {
   }

   public record ResolvedTargets(Set<AgentConversationData> targets, @Nullable Event.UserMessage cleanedMessage) {
   }
}
