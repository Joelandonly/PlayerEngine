package com.player2.playerengine.memory.ingest;

import com.player2.playerengine.memory.MemoryCaps;
import com.player2.playerengine.memory.MemoryNodeType;
import com.player2.playerengine.memory.MergePlan;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemoryExplicitFactExtractor {
   private static final String BASIC_COLOR_VALUE = "(?:light\\s+blue|dark\\s+blue|light\\s+green|dark\\s+green|black|white|gray|grey|red|orange|yellow|green|blue|purple|pink|brown|cyan|magenta|lime|teal|navy|violet|indigo|gold|silver)";
   private static final Pattern USER_MESSAGE = Pattern.compile("^\\s*User Message:\\s*\\[([^\\]]+)]\\s*:\\s*(.+)$", 34);
   private static final Pattern FAVORITE = Pattern.compile("\\bmy\\s+favou?rite\\s+([\\p{L}\\p{N}][\\p{L}\\p{N}\\s_\\-/]{0,40}?)\\s+(?:is|are|=)\\s+(.+)$", 34);
   private static final Pattern REMEMBER_MY = Pattern.compile(
      "\\bremember\\s+(?:that\\s+)?my\\s+([\\p{L}\\p{N}][\\p{L}\\p{N}\\s_\\-/]{0,40}?)\\s+(?:is|are|=)\\s+(.+)$", 34
   );
   private static final Pattern LIKE_COLOR = Pattern.compile("\\bi\\s+(?:really\\s+)?(?:like|love|prefer|enjoy)\\s+(?:the\\s+)?colou?r\\s+(.+)$", 34);
   private static final Pattern LIKE_NAMED_COLOR = Pattern.compile(
      "\\bi\\s+(?:really\\s+)?(?:like|love|prefer|enjoy)\\s+((?:light\\s+blue|dark\\s+blue|light\\s+green|dark\\s+green|black|white|gray|grey|red|orange|yellow|green|blue|purple|pink|brown|cyan|magenta|lime|teal|navy|violet|indigo|gold|silver))\\b\\s*[.!?]*$",
      34
   );
   private static final int PREFERENCE_IMPORTANCE = 6;
   private static final int FACT_IMPORTANCE = 6;

   private MemoryExplicitFactExtractor() {
   }

   public static List<MemoryExplicitFactExtractor.ExplicitMemory> extract(
      String turnText, String fallbackOwnerName, String companionName, String companionShortName
   ) {
      MemoryExplicitFactExtractor.ParsedTurn turn = parseTurn(turnText, fallbackOwnerName);
      if (turn.message().isBlank()) {
         return List.of();
      } else {
         String subject = cleanName(turn.speaker());
         String message = stripAddressing(turn.message(), companionName, companionShortName);
         List<String> companionNames = companionNames(companionName, companionShortName);
         Matcher favorite = FAVORITE.matcher(message);
         if (favorite.find()) {
            MemoryExplicitFactExtractor.ExplicitMemory memory = preference(
               subject, cleanTopic(favorite.group(1)), cleanValue(favorite.group(2), companionNames), true
            );
            return memory == null ? List.of() : List.of(memory);
         } else {
            Matcher likeColor = LIKE_COLOR.matcher(message);
            if (likeColor.find()) {
               MemoryExplicitFactExtractor.ExplicitMemory memory = colorPreference(subject, cleanValue(likeColor.group(1), companionNames));
               return memory == null ? List.of() : List.of(memory);
            } else {
               Matcher likeNamedColor = LIKE_NAMED_COLOR.matcher(message);
               if (likeNamedColor.find()) {
                  MemoryExplicitFactExtractor.ExplicitMemory memory = colorPreference(subject, cleanValue(likeNamedColor.group(1), companionNames));
                  return memory == null ? List.of() : List.of(memory);
               } else {
                  Matcher remember = REMEMBER_MY.matcher(message);
                  if (remember.find()) {
                     MemoryExplicitFactExtractor.ExplicitMemory memory = fact(
                        subject, cleanTopic(remember.group(1)), cleanValue(remember.group(2), companionNames)
                     );
                     return memory == null ? List.of() : List.of(memory);
                  } else {
                     return List.of();
                  }
               }
            }
         }
      }
   }

   public static MergePlan buildPlan(List<MemoryExplicitFactExtractor.ExplicitMemory> memories, long nowTick) {
      if (memories != null && !memories.isEmpty()) {
         long nowMs = System.currentTimeMillis();
         MergePlan.Builder builder = MergePlan.builder();

         for (MemoryExplicitFactExtractor.ExplicitMemory memory : memories) {
            if (memory != null && !memory.canonicalName().isBlank() && !memory.content().isBlank()) {
               String nodeId = nodeId(memory.canonicalName());
               builder.upsert(
                  new MergePlan.NodeUpsert(
                     nodeId,
                     MemoryCaps.capContent(memory.content()),
                     memory.type(),
                     MemoryCaps.capName(memory.canonicalName()),
                     List.of(),
                     memory.tags(),
                     memory.importance(),
                     nowMs,
                     nowTick
                  )
               );
               if (memory.subjectName() != null && !memory.subjectName().isBlank() && memory.relation() != null && !memory.relation().isBlank()) {
                  builder.edge(new MergePlan.EdgeUpsert(nodeId(memory.subjectName()), nodeId, MemoryCaps.capRelation(memory.relation()), 1.0, nowTick));
               }
            }
         }

         MergePlan plan = builder.build();
         return plan.isEmpty() ? null : plan;
      } else {
         return null;
      }
   }

   private static MemoryExplicitFactExtractor.ExplicitMemory preference(String subject, String topic, String value, boolean favorite) {
      if (!subject.isBlank() && !topic.isBlank() && !value.isBlank()) {
         String canonical = subject + (favorite ? " favorite " : " preference ") + topic;
         String content = subject + "'s " + (favorite ? "favorite " : "preferred ") + topic + " is " + value + ".";
         String relation = favorite ? "has favorite " + topic : "prefers " + topic;
         List<String> tags = tags("preference", favorite ? "favorite" : "preferred", topic, value);
         return new MemoryExplicitFactExtractor.ExplicitMemory(subject, canonical, content, MemoryNodeType.PREFERENCE.wire(), tags, relation, 6);
      } else {
         return null;
      }
   }

   private static MemoryExplicitFactExtractor.ExplicitMemory colorPreference(String subject, String value) {
      if (!subject.isBlank() && !value.isBlank()) {
         String canonical = subject + " favorite color";
         String content = subject + " likes the color " + value + ".";
         return new MemoryExplicitFactExtractor.ExplicitMemory(
            subject, canonical, content, MemoryNodeType.PREFERENCE.wire(), tags("preference", "favorite", "color", value), "likes color", 6
         );
      } else {
         return null;
      }
   }

   private static MemoryExplicitFactExtractor.ExplicitMemory fact(String subject, String topic, String value) {
      if (!subject.isBlank() && !topic.isBlank() && !value.isBlank()) {
         String canonical = subject + " " + topic;
         String content = subject + "'s " + topic + " is " + value + ".";
         return new MemoryExplicitFactExtractor.ExplicitMemory(
            subject, canonical, content, MemoryNodeType.FACT.wire(), tags("fact", topic, value), "has " + topic, 6
         );
      } else {
         return null;
      }
   }

   private static MemoryExplicitFactExtractor.ParsedTurn parseTurn(String turnText, String fallbackOwnerName) {
      if (turnText == null) {
         return new MemoryExplicitFactExtractor.ParsedTurn(cleanName(fallbackOwnerName), "");
      }

      Matcher m = USER_MESSAGE.matcher(turnText);
      return m.matches()
         ? new MemoryExplicitFactExtractor.ParsedTurn(cleanName(m.group(1)), m.group(2).trim())
         : new MemoryExplicitFactExtractor.ParsedTurn(cleanName(fallbackOwnerName), turnText.trim());
   }

   private static String stripAddressing(String message, String companionName, String companionShortName) {
      String out = message == null ? "" : message.trim();

      for (String name : companionNames(companionName, companionShortName)) {
         out = out.replaceFirst("(?i)^\\s*(?:hey\\s+)?\\Q" + name + "\\E\\b[\\s,:;\\-]*", "");
      }

      return out.trim();
   }

   private static List<String> companionNames(String companionName, String companionShortName) {
      Set<String> names = new LinkedHashSet<>();
      addName(names, companionName);
      addName(names, companionShortName);
      return new ArrayList<>(names);
   }

   private static void addName(Set<String> names, String raw) {
      String cleaned = raw == null ? "" : raw.trim();
      if (cleaned.length() >= 2) {
         names.add(cleaned);
      }
   }

   private static String cleanName(String raw) {
      String cleaned = raw != null && !raw.isBlank() ? raw.trim() : "owner";
      cleaned = cleaned.replaceAll("[\\r\\n\\t]+", " ");
      return MemoryCaps.capName(cleaned);
   }

   private static String cleanTopic(String raw) {
      String cleaned = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
      cleaned = cleaned.replace('_', ' ').replace('-', ' ');
      cleaned = cleaned.replaceAll("\\s+", " ");
      cleaned = cleaned.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
      return MemoryCaps.capName(normalizeTopic(cleaned));
   }

   private static String normalizeTopic(String cleaned) {
      return switch (cleaned) {
         case "colour" -> "color";
         case "video games", "videogame", "videogames" -> "video game";
         default -> cleaned;
      };
   }

   private static String cleanValue(String raw, List<String> companionNames) {
      String cleaned = raw == null ? "" : raw.trim();
      cleaned = cleaned.replaceAll("[\\r\\n\\t]+", " ");
      cleaned = cleaned.replaceAll("\\s+", " ");
      cleaned = cleaned.replaceAll("(?i)\\b(?:please|pls)\\b\\s*$", "").trim();

      for (String name : companionNames) {
         cleaned = cleaned.replaceAll("(?i)(?:[,;:\\-]\\s*)?\\b\\Q" + name + "\\E\\b\\s*$", "").trim();
      }

      cleaned = cleaned.replaceAll("^[\"'`]+|[\"'`.!?]+$", "").trim();
      return !cleaned.equalsIgnoreCase("that") && !cleaned.equalsIgnoreCase("this") ? MemoryCaps.capContent(cleaned) : "";
   }

   private static List<String> tags(String... values) {
      LinkedHashSet<String> out = new LinkedHashSet<>();
      if (values != null) {
         for (String value : values) {
            if (value != null) {
               for (String token : value.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                  if (token.length() >= 2) {
                     out.add(MemoryCaps.capName(token));
                  }
               }
            }
         }
      }

      return new ArrayList<>(out);
   }

   private static String nodeId(String canonicalName) {
      return "n_" + sha256Hex(canonicalName.toLowerCase(Locale.ROOT)).substring(0, 16);
   }

   private static String sha256Hex(String s) {
      try {
         MessageDigest md = MessageDigest.getInstance("SHA-256");
         byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
         StringBuilder hex = new StringBuilder(digest.length * 2);

         for (byte b : digest) {
            hex.append(Character.forDigit(b >> 4 & 15, 16));
            hex.append(Character.forDigit(b & 15, 16));
         }

         return hex.toString();
      } catch (NoSuchAlgorithmException impossible) {
         return Integer.toHexString(s.hashCode());
      }
   }

   public record ExplicitMemory(String subjectName, String canonicalName, String content, String type, List<String> tags, String relation, int importance) {
   }

   private record ParsedTurn(String speaker, String message) {
   }
}
