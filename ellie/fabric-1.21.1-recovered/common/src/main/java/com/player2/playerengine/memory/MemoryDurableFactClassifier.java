package com.player2.playerengine.memory;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MemoryDurableFactClassifier {
   private static final Pattern PREFERENCE_SIGNAL = Pattern.compile("\\b(favou?rite|prefers?|preference|likes?|loves?|enjoys?|dislikes?|hates?)\\b", 2);
   private static final Pattern STABLE_FACT_SIGNAL = Pattern.compile(
      "\\b(is|are|has|have|lives|works|plays|owns|uses|speaks|knows|wants|needs|birthday|name|age|home)\\b", 2
   );
   private static final Pattern PROFILE_WRAPPER = Pattern.compile("^(?:a|an|the)\\s+[^.]{0,100}\\b(?:who|that)\\b", 2);
   private static final Pattern EPISODIC_MARKER = Pattern.compile(
      "\\b(conversation episode|today|yesterday|last session|this session|earlier|just now|during|after|before|when|while|to test|testing|tested|inventory drop|on death|automatically revived|respawned|was slain|were slain|was killed|were killed|got killed|got slain|died|was revived|were revived)\\b",
      2
   );

   private MemoryDurableFactClassifier() {
   }

   public static boolean isDurableFactType(MemoryNodeType type) {
      return type == MemoryNodeType.FACT || type == MemoryNodeType.PREFERENCE;
   }

   public static boolean isDisplayableFactNode(MemoryNode node) {
      if (node == null) {
         return false;
      }

      MemoryNodeType type = MemoryNodeType.fromWire(node.type());
      return isDurableFactNode(type, node.canonicalName(), node.content(), node.tags());
   }

   public static MemoryNodeType normalizeExtractedType(MemoryNodeType type, String name, String content, List<String> tags) {
      if (type == null) {
         return null;
      } else if (!isDurableFactType(type)) {
         return type;
      } else if (looksEpisodic(name, content, tags)) {
         return null;
      } else if (hasPreferenceSignal(name, content, tags)) {
         return MemoryNodeType.PREFERENCE;
      } else {
         return looksLikeStableFact(name, content, tags) ? MemoryNodeType.FACT : null;
      }
   }

   public static boolean isDurableFactNode(MemoryNodeType type, String name, String content, List<String> tags) {
      if (!isDurableFactType(type) || looksEpisodic(name, content, tags)) {
         return false;
      } else {
         return type == MemoryNodeType.PREFERENCE
            ? hasPreferenceSignal(name, content, tags)
            : !hasPreferenceSignal(name, content, tags) && looksLikeStableFact(name, content, tags);
      }
   }

   private static boolean hasPreferenceSignal(String name, String content, List<String> tags) {
      return PREFERENCE_SIGNAL.matcher(join(name, content, tags)).find();
   }

   private static boolean looksLikeStableFact(String name, String content, List<String> tags) {
      String text = join(name, content, tags);
      return !text.isBlank() && !PROFILE_WRAPPER.matcher(text).find() && STABLE_FACT_SIGNAL.matcher(text).find();
   }

   private static boolean looksEpisodic(String name, String content, List<String> tags) {
      String text = join(name, content, tags);
      return text.isBlank() || PROFILE_WRAPPER.matcher(text).find() || EPISODIC_MARKER.matcher(text).find();
   }

   private static String join(String name, String content, List<String> tags) {
      StringBuilder sb = new StringBuilder();
      append(sb, name);
      append(sb, content);
      if (tags != null) {
         for (String tag : tags) {
            append(sb, tag);
         }
      }

      return sb.toString().toLowerCase(Locale.ROOT);
   }

   private static void append(StringBuilder sb, String value) {
      if (value != null && !value.isBlank()) {
         if (sb.length() > 0) {
            sb.append(' ');
         }

         sb.append(value.trim());
      }
   }
}
